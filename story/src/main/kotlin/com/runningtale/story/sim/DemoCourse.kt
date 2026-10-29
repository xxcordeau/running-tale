package com.runningtale.story.sim

import com.runningtale.story.signal.DrivingSnapshot
import com.runningtale.story.signal.Gear
import com.runningtale.story.signal.TurnDirection
import com.runningtale.story.signal.TurnInfo

/**
 * 시연 영상·PT용 데모 코스 (약 4분 30초). 숲속 친구 셋이 모두 나오도록 세 번 멈춘다.
 *
 * 0:00 출발 → 0:30 가속 → 1:02 우회전 → 1:12 터널(1:23 통과) → 1:30 정차: 용
 * → 2:12 와이퍼(비) → 2:23 정차: 여우 → 3:05 와이퍼 끔(무지개) → 3:25 정차: 올빼미 → 4:26 도착·주차
 *
 * 정차는 30초씩이라 선택지를 말이나 버튼으로 고를 시간이 있다.
 */
object DemoCourse {
    const val DURATION_MS = 272_000L
    private const val TURN_AT_S = 62.0
    private const val TURN_NOTICE_FROM_S = 45.0
    private const val PARK_AT_S = 266.0
    private val TUNNEL_S = 72.0..<83.0
    private val RAIN_S = 132.0..<185.0

    /** (시각 s, 차속 km/h). 사이는 직선으로 잇는다. */
    private val SPEED = listOf(
        0.0 to 0.0, 2.0 to 0.0, 30.0 to 30.0, 50.0 to 60.0, 58.0 to 25.0, 66.0 to 25.0,
        75.0 to 45.0, 85.0 to 45.0, 90.0 to 0.0,
        122.0 to 0.0, 130.0 to 35.0, 138.0 to 35.0, 143.0 to 0.0, // 정차 1: 용
        175.0 to 0.0, 183.0 to 40.0, 200.0 to 40.0, 205.0 to 0.0, // 정차 2: 여우
        237.0 to 0.0, 245.0 to 40.0, 260.0 to 40.0, 265.0 to 0.0, // 정차 3: 올빼미
        DURATION_MS / 1000.0 to 0.0,
    )

    fun snapshots(stepMs: Long = 200): List<DrivingSnapshot> {
        val times = (0..DURATION_MS step stepMs).toList()

        // 차속을 적분해 지나온 거리를 구한 뒤, 남은 거리와 회전까지 거리를 계산한다.
        val traveled = DoubleArray(times.size)
        for (i in 1 until times.size) {
            val dtS = (times[i] - times[i - 1]) / 1000.0
            traveled[i] = traveled[i - 1] + speedAt(times[i] / 1000.0) / 3.6 * dtS
        }
        val total = traveled.last()
        val turnPoint = traveled[times.indexOfFirst { it / 1000.0 >= TURN_AT_S }]

        return times.mapIndexed { i, t ->
            val s = t / 1000.0
            DrivingSnapshot(
                timestampMs = t,
                speedKmh = speedAt(s).toFloat(),
                gear = if (s < 1 || s >= PARK_AT_S) Gear.P else Gear.D,
                steeringAngleDeg = steeringAt(s),
                wiperOn = s in RAIN_S,
                inTunnel = s in TUNNEL_S,
                nextTurn = if (s in TURN_NOTICE_FROM_S..<TURN_AT_S) {
                    TurnInfo(TurnDirection.RIGHT, (turnPoint - traveled[i]).toInt().coerceAtLeast(0))
                } else null,
                remainingDistanceM = (total - traveled[i]).toInt(),
                totalDistanceM = total.toInt(),
                arrived = s >= PARK_AT_S,
            )
        }
    }

    private fun speedAt(s: Double): Double {
        val next = SPEED.indexOfFirst { it.first > s }
        if (next <= 0) return SPEED.last().second
        val (s0, v0) = SPEED[next - 1]
        val (s1, v1) = SPEED[next]
        return v0 + (v1 - v0) * (s - s0) / (s1 - s0)
    }

    private fun steeringAt(s: Double): Float = when {
        s >= 60 && s < 62 -> 120f
        s >= 62 && s < 64 -> 60f
        else -> 0f
    }
}
