package com.runningtale.story.signal

import kotlin.math.abs

/**
 * 연속된 [DrivingSnapshot]을 받아 상태가 바뀐 순간에만 [DrivingEvent]를 낸다.
 *
 * 급정거·급가속은 일부러 사건으로 만들지 않는다 (기획안 안전 설계: 자극 수위 제한).
 *
 * 실제 도로의 잡음을 견디도록:
 * - 정차는 [stopHoldMs] 동안 멈춰 있어야, 재출발은 [RESUME_KMH] 이상이어야 잡는다 (막히는 길의 엉금엉금에 친구가 소진되지 않게).
 * - 속도 단계는 경계에서 [BAND_MARGIN_KMH] 여유를 두고, 새 단계가 [bandDwellMs] 이어져야 알린다.
 * - 후진(R) 중에는 출발·정차·속도를 판단하지 않는다.
 * - 내비 경로가 없으면 P단을 [parkHoldMs] 동안 유지해야 도착으로 본다 (휴게소에 잠깐 세운 것과 구분).
 */
class EventDetector(
    private val stopHoldMs: Long = 5_000,
    private val bandDwellMs: Long = 6_000,
    private val parkHoldMs: Long = 60_000,
    private val turnAnnounceM: Int = 300,
    private val turnAngleDeg: Float = 90f,
    private val turnRearmDeg: Float = 30f,
) {
    private var departed = false
    private var stopped = false
    private var zeroSpeedSinceMs: Long? = null
    private var band: SpeedBand? = null
    private var rawBand: SpeedBand? = null
    private var rawBandSinceMs = 0L
    private var parkedSinceMs: Long? = null
    private var prevTurn: TurnInfo? = null
    private var steeringArmed = true
    private var raining: Boolean? = null
    private var tunnel: Boolean? = null
    private var lastProgressStep = 0
    private var arrived = false

    fun onSnapshot(s: DrivingSnapshot): List<DrivingEvent> {
        if (arrived) return emptyList()
        val out = mutableListOf<DrivingEvent>()

        val remaining = s.remainingDistanceM
        if (s.arrived || isParkedAtEnd(s, remaining)) {
            arrived = true
            out += DrivingEvent.Arrived
            return out
        }

        detectMotion(s, out)
        detectTurn(s, out)

        s.wiperOn?.let { on ->
            if (raining != null && on != raining) out += if (on) DrivingEvent.RainStarted else DrivingEvent.RainStopped
            raining = on
        }
        s.inTunnel?.let { inside ->
            if (tunnel != null && inside != tunnel) out += if (inside) DrivingEvent.TunnelEntered else DrivingEvent.TunnelExited
            tunnel = inside
        }

        val total = s.totalDistanceM
        if (remaining != null && total != null && total > 0) {
            val ratio = (1f - remaining.toFloat() / total).coerceIn(0f, 1f)
            val step = (ratio * 20).toInt() // 5% 단위
            if (step > lastProgressStep) {
                lastProgressStep = step
                out += DrivingEvent.Progress(ratio)
            }
        }
        return out
    }

    /**
     * 출발 후 P단: 내비 목적지 근처면 바로, 경로가 없으면 [parkHoldMs] 동안 P를 유지해야 도착.
     * 경로가 있는데 목적지가 멀면 도착이 아니다 (중간 정차).
     */
    private fun isParkedAtEnd(s: DrivingSnapshot, remaining: Int?): Boolean {
        if (s.gear != Gear.P || !departed) {
            parkedSinceMs = null
            return false
        }
        if (remaining != null) return remaining <= 50
        val since = parkedSinceMs ?: s.timestampMs.also { parkedSinceMs = it }
        return s.timestampMs - since >= parkHoldMs
    }

    private fun detectMotion(s: DrivingSnapshot, out: MutableList<DrivingEvent>) {
        if (s.gear == Gear.R) {
            zeroSpeedSinceMs = null
            return
        }
        // AAOS 차속은 후진 때 음수가 될 수 있다.
        val speed = abs(s.speedKmh ?: return)
        when {
            !departed -> if (speed >= MOVING_KMH) {
                departed = true
                out += DrivingEvent.Departed
            }
            stopped -> if (speed >= RESUME_KMH) {
                stopped = false
                out += DrivingEvent.Resumed
            }
            speed < STOPPED_KMH -> {
                val since = zeroSpeedSinceMs ?: s.timestampMs.also { zeroSpeedSinceMs = it }
                if (s.timestampMs - since >= stopHoldMs) {
                    stopped = true
                    band = null
                    rawBand = null
                    out += DrivingEvent.Stopped
                }
            }
            speed >= MOVING_KMH -> zeroSpeedSinceMs = null
        }
        if (departed && !stopped && speed >= MOVING_KMH) detectBand(speed, s.timestampMs, out)
    }

    private fun detectBand(speed: Float, now: Long, out: MutableList<DrivingEvent>) {
        val next = bandWithMargin(rawBand, speed)
        if (next != rawBand) {
            rawBand = next
            rawBandSinceMs = now
        }
        if (next != band && now - rawBandSinceMs >= bandDwellMs) {
            band = next
            out += DrivingEvent.SpeedChanged(next)
        }
    }

    /** 지금 단계에 머무르려는 쪽으로 경계를 [BAND_MARGIN_KMH]만큼 민다. */
    private fun bandWithMargin(current: SpeedBand?, speed: Float): SpeedBand {
        fun boundary(at: Float, upper: SpeedBand) = when {
            current == null -> at
            current >= upper -> at - BAND_MARGIN_KMH
            else -> at + BAND_MARGIN_KMH
        }
        return when {
            speed < boundary(SLOW_MAX_KMH, SpeedBand.CRUISE) -> SpeedBand.SLOW
            speed < boundary(CRUISE_MAX_KMH, SpeedBand.FAST) -> SpeedBand.CRUISE
            else -> SpeedBand.FAST
        }
    }

    private fun detectTurn(s: DrivingSnapshot, out: MutableList<DrivingEvent>) {
        val turn = s.nextTurn
        val prev = prevTurn
        if (turn != null && turn.distanceM <= turnAnnounceM &&
            (prev == null || prev.direction != turn.direction || prev.distanceM > turnAnnounceM)
        ) {
            out += DrivingEvent.TurnAhead(turn.direction, turn.distanceM)
        }

        val angle = s.steeringAngleDeg
        if (angle != null) {
            if (steeringArmed && abs(angle) >= turnAngleDeg && (s.speedKmh ?: 0f) >= MOVING_KMH) {
                steeringArmed = false
                out += DrivingEvent.Turned(if (angle > 0) TurnDirection.RIGHT else TurnDirection.LEFT)
            } else if (abs(angle) < turnRearmDeg) {
                steeringArmed = true
            }
        } else if (prev != null && prev.distanceM <= 50 && turn != prev) {
            // 조향각을 못 받는 차량: TBT 안내가 바로 앞에서 넘어가면 회전한 것으로 본다.
            out += DrivingEvent.Turned(prev.direction)
        }
        prevTurn = turn
    }

    private companion object {
        const val MOVING_KMH = 3f
        const val STOPPED_KMH = 1f
        const val RESUME_KMH = 8f
        const val SLOW_MAX_KMH = 20f
        const val CRUISE_MAX_KMH = 50f
        const val BAND_MARGIN_KMH = 4f
    }
}
