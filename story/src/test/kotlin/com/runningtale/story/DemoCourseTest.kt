package com.runningtale.story

import com.runningtale.story.engine.StoryEngine
import com.runningtale.story.signal.DrivingEvent
import com.runningtale.story.signal.EventDetector
import com.runningtale.story.signal.TurnDirection
import com.runningtale.story.sim.DemoCourse
import com.runningtale.story.themes.ForestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoCourseTest {
    private fun demoEvents(): List<DrivingEvent> {
        val detector = EventDetector()
        return DemoCourse.snapshots().flatMap(detector::onSnapshot)
    }

    @Test
    fun `데모 코스는 시연 계획 순서대로 사건을 낸다`() {
        val events = demoEvents().filterNot { it is DrivingEvent.Progress || it is DrivingEvent.SpeedChanged }
        assertEquals(
            listOf("Departed", "TurnAhead", "Turned", "TunnelEntered", "TunnelExited", "Stopped", "Resumed", "RainStarted", "Arrived"),
            events.map { it::class.simpleName },
        )
        assertEquals(TurnDirection.RIGHT, events.filterIsInstance<DrivingEvent.Turned>().single().direction)
    }

    @Test
    fun `가속 구간에서 속도 단계가 올라간다`() {
        val bands = demoEvents().filterIsInstance<DrivingEvent.SpeedChanged>().map { it.band.name }
        assertEquals(listOf("SLOW", "CRUISE", "FAST"), bands.take(3))
    }

    @Test
    fun `데모 전체를 이야기로 돌리면 정차 때만 선택지가 열리고 도착으로 끝난다`() {
        val engine = StoryEngine(ForestTheme)
        val detector = EventDetector()
        var choiceSeen = false
        for (snapshot in DemoCourse.snapshots()) {
            for (event in detector.onSnapshot(snapshot)) {
                engine.onEvent(event)
                if (engine.state.openChoice != null) {
                    choiceSeen = true
                    assertTrue(snapshot.speedKmh!! < 1f, "주행 중에 선택지가 열림: $event at ${snapshot.timestampMs}")
                }
            }
        }
        assertTrue(choiceSeen)
        assertTrue(engine.state.finished)
        assertEquals("별빛 성", engine.state.scene.title)
        assertEquals(listOf("마을 들판", "꽃길", "깊은 숲", "반딧불 동굴", "꽃길", "용의 고개", "꽃길", "별빛 성"), engine.state.journey)
    }
}
