package com.runningtale.story.sim

import com.runningtale.story.signal.DrivingSnapshot
import com.runningtale.story.signal.Gear
import com.runningtale.story.signal.TurnDirection
import com.runningtale.story.signal.TurnInfo

/**
 * 기획안 "시연 계획"의 3분 데모 코스.
 *
 * 0:00 출발 → 0:30 가속 → 1:00 우회전 → 1:12 터널(1:23 통과) → 1:30 신호 대기(2:10 재출발) → 2:15 와이퍼 → 2:50 도착·주차
 */
object DemoCourse {
    const val DURATION_MS = 180_000L
    private const val TURN_AT_S = 62.0
    private const val TURN_NOTICE_FROM_S = 45.0
    private const val PARK_AT_S = 170.5
    private val TUNNEL_S = 72.0..<83.0

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
                wiperOn = s >= 135,
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

    private fun speedAt(s: Double): Double = when {
        s < 2 -> 0.0
        s < 30 -> lerp(0.0, 30.0, s, 2.0, 30.0)
        s < 50 -> lerp(30.0, 60.0, s, 30.0, 50.0)
        s < 58 -> lerp(60.0, 25.0, s, 50.0, 58.0)
        s < 66 -> 25.0
        s < 75 -> lerp(25.0, 45.0, s, 66.0, 75.0)
        s < 85 -> 45.0
        s < 90 -> lerp(45.0, 0.0, s, 85.0, 90.0)
        s < 130 -> 0.0
        s < 140 -> lerp(0.0, 40.0, s, 130.0, 140.0)
        s < 165 -> 40.0
        s < 170 -> lerp(40.0, 0.0, s, 165.0, 170.0)
        else -> 0.0
    }

    private fun steeringAt(s: Double): Float = when {
        s >= 60 && s < 62 -> 120f
        s >= 62 && s < 64 -> 60f
        else -> 0f
    }

    private fun lerp(from: Double, to: Double, s: Double, s0: Double, s1: Double) =
        from + (to - from) * (s - s0) / (s1 - s0)
}
