package com.runningtale.app

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.runningtale.story.signal.DrivingSignalSource
import com.runningtale.story.signal.DrivingSnapshot
import com.runningtale.story.signal.Gear
import com.runningtale.story.signal.TurnDirection
import com.runningtale.story.signal.TurnInfo

/**
 * 실차·에뮬레이터 주행 신호.
 *
 * - Pleos Vehicle SDK: 기어, 조향각, 앞 와이퍼, 누적 주행거리
 * - Pleos NaviHelper: 다음 회전(TBT), 목적지까지 남은 거리, 도착
 * - 차속: Pleos SDK에 차속 API가 없어 AAOS 표준 PERF_VEHICLE_SPEED를 먼저 쓰고,
 *   못 받으면 누적 주행거리 변화량으로 계산한다.
 *
 * 콜백은 신호가 바뀔 때만 오므로, 정차 판정(차속 0 유지)을 위해 [TICK_MS]마다 최신 상태를 다시 보낸다.
 */
class PleosSignalSource(private val context: Context) : DrivingSignalSource {
    enum class SpeedSource { CAR_PROPERTY, ODOMETER, NONE }

    private val main = Handler(Looper.getMainLooper())
    private var onSnapshot: ((DrivingSnapshot) -> Unit)? = null
    private var latest = DrivingSnapshot(timestampMs = 0)

    var speedSource = SpeedSource.NONE
        private set

    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var car: Car? = null

    private val odometerSamples = ArrayDeque<Pair<Long, Float>>() // (시각 ms, 누적 km)
    private var maxRemainingM = 0

    /** TBT 목록에 있는 다음 터널까지 거리(m). 없으면 null. */
    private var tunnelAheadM: Double? = null
    /** 터널에 들어간 시각(elapsedRealtime). 터널 밖이면 null. */
    private var tunnelSince: Long? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = update { copy(gear = gear.toGear()) }
        override fun onFailed(e: Exception) = logFailure("gear", e)
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = update { copy(steeringAngleDeg = angle.toRightPositive()) }
        override fun onFailed(e: Exception) = logFailure("steering", e)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = update { copy(wiperOn = state == WiperState.ON) }
        override fun onFailed(e: Exception) = logFailure("wiper", e)
    }
    private val distanceListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(km: Float?) {
            if (km == null) return
            main.post { odometerSamples.addLast(SystemClock.elapsedRealtime() to km) }
        }
        override fun onFailed(e: Exception) = logFailure("odometer", e)
    }
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val metersPerSecond = value.value as? Float ?: return
            speedSource = SpeedSource.CAR_PROPERTY
            update { copy(speedKmh = metersPerSecond * 3.6f) }
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "PERF_VEHICLE_SPEED error zone=$zone")
        }
    }
    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(list: List<TBTInfo>) {
            val next = list.firstOrNull { it.type.toDirection() != null }
            val tunnelM = list.firstOrNull { it.type == TurnType.TURN_TUNNEL }?.distance
            main.post { tunnelAheadM = tunnelM }
            update { copy(nextTurn = next?.let { TurnInfo(it.type.toDirection()!!, it.distance.toInt()) }) }
        }
        override fun onDrivingInfo(info: DrivingInfo) {
            val remaining = info.destination?.distance ?: return
            maxRemainingM = maxOf(maxRemainingM, remaining)
            update { copy(remainingDistanceM = remaining, totalDistanceM = maxRemainingM) }
        }
        override fun onDestinationArrived(info: DestinationArrivedInfo) = update { copy(arrived = true) }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (speedSource != SpeedSource.CAR_PROPERTY) {
                odometerSpeedKmh()?.let {
                    speedSource = SpeedSource.ODOMETER
                    latest = latest.copy(speedKmh = it)
                }
            }
            navi?.runCatching { getTBTInfo() }
            latest = latest.copy(inTunnel = updateTunnel())
            emit()
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun start(onSnapshot: (DrivingSnapshot) -> Unit) {
        this.onSnapshot = onSnapshot
        startVehicle()
        startNavi()
        startCarSpeed()
        main.post(tick)
    }

    override fun stop() {
        main.removeCallbacks(tick)
        onSnapshot = null
        vehicle?.run {
            runCatching { getGear().unregisterGearSelection(gearListener) }
            runCatching { getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
            runCatching { getWiper().unregisterWiperState(FRONT, wiperListener) }
            runCatching { getOdometer().unregisterDistanceDriven(distanceListener) }
            runCatching { release() }
        }
        navi?.run {
            runCatching { removeListener(naviListener) }
            runCatching { release() }
        }
        car?.run {
            runCatching {
                (getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).unregisterCallback(speedCallback)
            }
            runCatching { disconnect() }
        }
        vehicle = null
        navi = null
        car = null
    }

    /** 오버레이에 띄울 신호 출처 설명. */
    fun describe(): String = when (speedSource) {
        SpeedSource.CAR_PROPERTY -> "차속: AAOS"
        SpeedSource.ODOMETER -> "차속: 주행거리"
        SpeedSource.NONE -> "차속: 없음"
    }

    private fun startVehicle() = runCatching {
        val v = Vehicle(context).also { it.initialize() }
        vehicle = v
        v.getGear().run {
            getCurrentGearSelectionState({ g -> update { copy(gear = g.toGear()) } }, { logFailure("gear", it) })
            registerGearSelection(gearListener)
        }
        v.getSteeringWheel().run {
            getCurrentSteeringWheelAngle({ a -> update { copy(steeringAngleDeg = a.toRightPositive()) } }, { logFailure("steering", it) })
            registerSteeringWheelAngle(steeringListener)
        }
        v.getWiper().run {
            getCurrentWiperState(FRONT, { s -> update { copy(wiperOn = s == WiperState.ON) } }, { logFailure("wiper", it) })
            registerWiperState(FRONT, wiperListener)
        }
        v.getOdometer().registerDistanceDriven(distanceListener)
    }.onFailure { Log.e(TAG, "Vehicle SDK 초기화 실패", it) }

    private fun startNavi() = runCatching {
        navi = NaviHelper(context).also {
            it.initialize()
            it.addListener(naviListener)
        }
    }.onFailure { Log.e(TAG, "NaviHelper 초기화 실패", it) }

    private fun startCarSpeed() = runCatching {
        val c = Car.createCar(context) ?: return@runCatching
        car = c
        val manager = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        manager.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
    }.onFailure { Log.w(TAG, "PERF_VEHICLE_SPEED 사용 불가, 주행거리로 차속 계산", it) }

    /** 최근 [ODOMETER_WINDOW_MS] 동안의 주행거리 변화로 구한 차속. 샘플이 없으면 null. */
    private fun odometerSpeedKmh(): Float? {
        val now = SystemClock.elapsedRealtime()
        while (odometerSamples.size > 1 && now - odometerSamples.first().first > ODOMETER_WINDOW_MS) {
            odometerSamples.removeFirst()
        }
        val first = odometerSamples.firstOrNull() ?: return null
        val last = odometerSamples.last()
        // 마지막 갱신 뒤로 주행거리가 안 바뀌고 있으면 멈춘 것
        if (now - last.first > ODOMETER_WINDOW_MS) return 0f
        val hours = (now - first.first) / 3_600_000f
        if (hours <= 0f) return null
        return ((last.second - first.second) / hours).coerceAtLeast(0f)
    }

    /**
     * 터널 안인지. TBT에는 터널 입구까지 거리만 있고 길이는 없어서,
     * 입구 [TUNNEL_ENTER_M] 안쪽이면 들어간 것으로 보고, 터널 안내가 목록에서 사라진 뒤
     * 최소 [TUNNEL_MIN_MS]가 지나면 나온 것으로 본다 (동굴 장면이 너무 짧게 끝나지 않게).
     */
    private fun updateTunnel(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val ahead = tunnelAheadM
        val since = tunnelSince
        if (since == null) {
            if (ahead != null && ahead <= TUNNEL_ENTER_M) tunnelSince = now
        } else if ((ahead == null || ahead > TUNNEL_ENTER_M) && now - since >= TUNNEL_MIN_MS) {
            tunnelSince = null
        }
        return tunnelSince != null
    }

    private fun update(change: DrivingSnapshot.() -> DrivingSnapshot) {
        main.post {
            latest = latest.change()
            emit()
        }
    }

    private fun emit() {
        latest = latest.copy(timestampMs = SystemClock.elapsedRealtime())
        onSnapshot?.invoke(latest)
    }

    private fun logFailure(what: String, e: Exception) {
        Log.w(TAG, "$what 신호 실패", e)
    }

    private companion object {
        const val TAG = "PleosSignalSource"
        const val TICK_MS = 500L
        const val ODOMETER_WINDOW_MS = 3_000L
        const val TUNNEL_ENTER_M = 30.0
        const val TUNNEL_MIN_MS = 20_000L
        val FRONT = WindowArea(WindowValue.FRONT_WINDSHIELD)

        /**
         * Pleos SDK 조향각은 왼쪽이 양수다 (Virtual Console에서 오른쪽 300° → SDK -300°, 2026-10-29 확인).
         * DrivingSnapshot은 오른쪽이 양수라 부호를 뒤집는다.
         */
        fun Float?.toRightPositive(): Float? = this?.let { -it }

        fun VehicleGear?.toGear() = when (this) {
            null -> Gear.UNKNOWN
            VehicleGear.GEAR_PARK -> Gear.P
            VehicleGear.GEAR_REVERSE -> Gear.R
            VehicleGear.GEAR_NEUTRAL -> Gear.N
            VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.D // GEAR_DRIVE, GEAR_1~9
        }

        fun TurnType.toDirection(): TurnDirection? = when (this) {
            TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> TurnDirection.RIGHT
            TurnType.TURN_LEFT, TurnType.TURN_SHARP_L -> TurnDirection.LEFT
            else -> null // 차선 유지(KEEP)는 회전이 아니라 이야기에서 꺾지 않는다
        }
    }
}
