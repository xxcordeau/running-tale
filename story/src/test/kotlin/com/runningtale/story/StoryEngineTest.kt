package com.runningtale.story

import com.runningtale.story.engine.StoryEngine
import com.runningtale.story.signal.DrivingEvent
import com.runningtale.story.signal.TurnDirection
import com.runningtale.story.themes.ForestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryEngineTest {
    @Test
    fun `정차하면 선택지가 열리고 고르면 닫힌다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.Departed)
        assertNull(engine.state.openChoice)

        engine.onEvent(DrivingEvent.Stopped)
        assertNotNull(engine.state.openChoice)

        engine.choose(0)
        assertNull(engine.state.openChoice)
        assertTrue(engine.state.narration.contains("사과"))
    }

    @Test
    fun `대답 전에 출발하면 선택지가 자동으로 닫힌다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.Departed)
        engine.onEvent(DrivingEvent.Stopped)
        engine.onEvent(DrivingEvent.Resumed)
        assertNull(engine.state.openChoice)
        assertNull(engine.choose(0))
    }

    @Test
    fun `정차할 때마다 다른 친구를 만난다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.Departed)
        val friends = (1..4).map {
            engine.onEvent(DrivingEvent.Stopped)
            val title = engine.state.scene.title
            engine.onEvent(DrivingEvent.Resumed)
            title
        }
        assertEquals(listOf("용의 고개", "여우 굴", "부엉이 나무", "용의 고개"), friends)
    }

    @Test
    fun `회전 예고에 거리가 들어간다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.TurnAhead(TurnDirection.RIGHT, 263))
        assertTrue(engine.state.narration.contains("250미터"), engine.state.narration)
    }

    @Test
    fun `와이퍼가 켜지면 비가 내린다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.RainStarted)
        assertTrue(engine.state.raining)
        engine.onEvent(DrivingEvent.RainStopped)
        assertFalse(engine.state.raining)
    }

    @Test
    fun `진행률 이정표는 한 번만 낭독한다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.Progress(0.55f))
        assertTrue(engine.state.narration.startsWith("벌써 반"))
        engine.onEvent(DrivingEvent.Turned(TurnDirection.LEFT))
        engine.onEvent(DrivingEvent.Progress(0.6f))
        assertFalse(engine.state.narration.startsWith("벌써 반"))
        assertEquals(0.6f, engine.state.progress)
    }

    @Test
    fun `도착 후에는 사건을 무시한다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.Arrived)
        assertTrue(engine.state.finished)
        assertNull(engine.onEvent(DrivingEvent.Stopped))
    }

    @Test
    fun `낭독마다 앞뒤 효과음과 대사가 따라오고 다음 낭독에서 바뀐다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.Departed)
        engine.onEvent(DrivingEvent.Stopped)
        assertEquals("끼이익… 멈춤!", engine.state.before.first())
        // 효과음 뒤에 보기를 소리로 묻는다
        assertEquals(listOf("뾰로롱~", "사과를 줄까? 아니면 노래를 불러줄까?"), engine.state.after.takeLast(2))

        engine.choose(0)
        assertEquals(listOf("딩동댕!"), engine.state.before)
        assertTrue("와삭! 냠냠…" in engine.state.after)

        // 이정표는 이전 대사를 이어받지 않는다
        engine.onEvent(DrivingEvent.Resumed)
        engine.onEvent(DrivingEvent.Progress(0.5f))
        assertEquals(listOf("따라란~"), engine.state.before)
        assertTrue(engine.state.after.isEmpty())
    }

    @Test
    fun `선택지가 열린 동안 비가 와도 비 상태는 이어진다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.Departed)
        engine.onEvent(DrivingEvent.Stopped)
        val narration = engine.state.narration

        engine.onEvent(DrivingEvent.RainStarted)
        assertTrue(engine.state.raining)
        assertEquals(narration, engine.state.narration) // 낭독은 끊지 않는다
        assertNotNull(engine.state.openChoice)
    }

    @Test
    fun `말로 고르면 보기 단어나 순서로 알아듣는다`() {
        fun dragon() = StoryEngine(ForestTheme).apply {
            onEvent(DrivingEvent.Departed)
            onEvent(DrivingEvent.Stopped)
        }
        assertTrue(dragon().chooseBySpeech("사과 줄래!")!!.narration.contains("사과"))
        assertTrue(dragon().chooseBySpeech("노래 불러 줄래")!!.narration.contains("노래"))
        assertTrue(dragon().chooseBySpeech("두 번째")!!.narration.contains("노래"))
        assertNull(dragon().chooseBySpeech("음 몰라"))
        assertNull(dragon().chooseBySpeech("사과도 주고 노래도"))

        val notOpen = StoryEngine(ForestTheme)
        assertNull(notOpen.chooseBySpeech("사과"))
    }

    @Test
    fun `반딧불이는 달님으로 잘못 알아듣지 않는다`() {
        val engine = StoryEngine(ForestTheme)
        engine.onEvent(DrivingEvent.Departed)
        engine.onEvent(DrivingEvent.Stopped)
        engine.onEvent(DrivingEvent.Resumed)
        engine.onEvent(DrivingEvent.Stopped) // 두 번째 정차 = 여우
        assertTrue(engine.chooseBySpeech("반딧불이")!!.narration.contains("반딧불이 친구"))
    }
}
