package com.runningtale.story.signal

enum class Gear { P, R, N, D, UNKNOWN }

enum class TurnDirection { LEFT, RIGHT }

/** 내비 TBT의 다음 회전 안내. */
data class TurnInfo(val direction: TurnDirection, val distanceM: Int)

/**
 * 한 시점의 주행 상태.
 *
 * 차종·SDK 버전마다 받을 수 있는 신호가 달라서, 모르는 값은 null로 두고
 * [EventDetector]가 있는 신호만으로 사건을 만든다.
 */
data class DrivingSnapshot(
    val timestampMs: Long,
    val speedKmh: Float? = null,
    val gear: Gear = Gear.UNKNOWN,
    /** 양수 = 오른쪽. */
    val steeringAngleDeg: Float? = null,
    val wiperOn: Boolean? = null,
    val inTunnel: Boolean? = null,
    val nextTurn: TurnInfo? = null,
    val remainingDistanceM: Int? = null,
    val totalDistanceM: Int? = null,
    val arrived: Boolean = false,
)

/** 주행 신호 공급원. 실차(Pleos SDK)와 데모 시뮬레이터가 같은 인터페이스를 쓴다. */
interface DrivingSignalSource {
    fun start(onSnapshot: (DrivingSnapshot) -> Unit)
    fun stop()
}
