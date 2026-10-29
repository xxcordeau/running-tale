package com.runningtale.story

import com.runningtale.story.signal.DrivingEvent
import com.runningtale.story.signal.DrivingSnapshot
import com.runningtale.story.signal.EventDetector
import com.runningtale.story.signal.Gear
import com.runningtale.story.signal.TurnDirection
import com.runningtale.story.signal.TurnInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventDetectorTest {
    private fun drive(vararg snapshots: DrivingSnapshot): List<DrivingEvent> {
        val detector = EventDetector()
        return snapshots.flatMap(detector::onSnapshot)
    }

    /** [fromMs]부터 [toMs]까지 0.5초마다 같은 상태를 보낸다. */
    private fun hold(fromMs: Long, toMs: Long, speed: Float, gear: Gear = Gear.D) =
        (fromMs..toMs step 500).map { DrivingSnapshot(it, speedKmh = speed, gear = gear) }

    @Test
    fun `내비 경로 없이 출발 후 P단을 1분 유지하면 도착`() {
        val events = drive(
            DrivingSnapshot(0, speedKmh = 0f, gear = Gear.P),
            DrivingSnapshot(1_000, speedKmh = 20f, gear = Gear.D),
            *hold(5_000, 65_000, 0f, Gear.P).toTypedArray(),
        )
        assertEquals(DrivingEvent.Arrived, events.last())
    }

    @Test
    fun `휴게소에 잠깐 P단으로 세우면 도착이 아니라 정차다`() {
        val events = drive(
            DrivingSnapshot(0, speedKmh = 20f, gear = Gear.D),
            *hold(1_000, 30_000, 0f, Gear.P).toTypedArray(),
            DrivingSnapshot(31_000, speedKmh = 20f, gear = Gear.D),
        )
        assertTrue(DrivingEvent.Arrived !in events)
        assertEquals(listOf(DrivingEvent.Stopped, DrivingEvent.Resumed), events.filter { it is DrivingEvent.Stopped || it is DrivingEvent.Resumed })
    }

    @Test
    fun `출발 전 P단은 도착이 아니다`() {
        val events = drive(*hold(0, 120_000, 0f, Gear.P).toTypedArray())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `정차는 차속 0이 5초 이어져야 잡힌다`() {
        val short = drive(DrivingSnapshot(0, speedKmh = 30f), *hold(1_000, 4_000, 0f).toTypedArray())
        assertTrue(DrivingEvent.Stopped !in short)

        val long = drive(DrivingSnapshot(0, speedKmh = 30f), *hold(1_000, 7_000, 0f).toTypedArray())
        assertEquals(1, long.count { it == DrivingEvent.Stopped })
    }

    @Test
    fun `막히는 길에서 엉금엉금 가다 서다 해도 정차·재출발이 반복되지 않는다`() {
        val creep = (0 until 20).flatMap { i ->
            val t = 2_000L + i * 8_000L
            hold(t, t + 6_000, 0f) + DrivingSnapshot(t + 7_000, speedKmh = 4f)
        }
        val events = drive(DrivingSnapshot(0, speedKmh = 30f), *creep.toTypedArray())
        assertEquals(1, events.count { it == DrivingEvent.Stopped })
        assertEquals(0, events.count { it == DrivingEvent.Resumed })
    }

    @Test
    fun `시속 50 근처를 오르내려도 속도 단계 알림이 반복되지 않는다`() {
        val wobble = (0 until 60).map { i -> DrivingSnapshot(i * 1_000L, speedKmh = if (i % 4 < 2) 48f else 52f) }
        val bands = drive(*wobble.toTypedArray()).filterIsInstance<DrivingEvent.SpeedChanged>()
        assertEquals(1, bands.size)
    }

    @Test
    fun `후진은 정차로 보지 않는다`() {
        val events = drive(
            DrivingSnapshot(0, speedKmh = 30f, gear = Gear.D),
            *hold(1_000, 20_000, -5f, Gear.R).toTypedArray(),
        )
        assertTrue(DrivingEvent.Stopped !in events)
    }

    @Test
    fun `조향각이 없으면 TBT가 넘어가는 순간을 회전으로 본다`() {
        val events = drive(
            DrivingSnapshot(0, speedKmh = 30f, nextTurn = TurnInfo(TurnDirection.LEFT, 120)),
            DrivingSnapshot(1_000, speedKmh = 30f, nextTurn = TurnInfo(TurnDirection.LEFT, 30)),
            DrivingSnapshot(2_000, speedKmh = 30f, nextTurn = null),
        )
        assertTrue(DrivingEvent.Turned(TurnDirection.LEFT) in events)
    }
}
