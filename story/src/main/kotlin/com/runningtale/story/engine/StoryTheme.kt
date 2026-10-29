package com.runningtale.story.engine

import com.runningtale.story.signal.DrivingEvent
import com.runningtale.story.signal.SpeedBand
import com.runningtale.story.signal.TurnDirection

/** 화면 배경 종류. 실제 그림 리소스는 앱이 고른다. */
enum class Backdrop { MEADOW, ROAD, FOREST, CAVE, CASTLE }

/**
 * [art]는 배경·지도 아이콘 키, [npc]는 이 장면에서 만나는 친구 그림 키. 키를 실제 그림으로 바꾸는 건 앱 몫이다.
 */
data class Scene(
    val title: String,
    val emoji: String,
    val backdrop: Backdrop,
    val art: String,
    val npc: String? = null,
)

/**
 * [cameo]는 이 답을 골랐을 때 친구 자리에 대신 보여줄 그림 키. [before]/[after]는 [StoryBeat]와 같다.
 * [keywords]는 아이가 말로 고를 때 알아듣는 단어 (띄어쓰기 무시, 포함되면 인정). 비우면 [label]을 쓴다.
 */
data class ChoiceOption(
    val label: String,
    val narration: String,
    val cameo: String? = null,
    val before: List<String> = emptyList(),
    val after: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

/**
 * [prompt]는 선택지가 열릴 때 보기를 소리로 알려주는 질문 ("사과를 줄까? 노래를 불러줄까?").
 * 글을 못 읽는 아이도 무엇을 고를 수 있는지 알고, 말로 대답할 수 있게 한다.
 */
data class Choice(val options: List<ChoiceOption>, val prompt: String? = null)

/**
 * 이야기 한 토막. [scene]이 null이면 지금 장면을 유지한 채 낭독만 바뀐다.
 * [narration]의 `{distance}`는 회전 예고 거리로 바뀐다.
 * [cameo]는 잠깐 등장하는 그림(표지판, 무지개 등). 장면이 바뀌면 사라진다.
 * [before]는 낭독 앞에 읽는 효과음·대사, [after]는 낭독 뒤에 읽는 캐릭터 대사.
 */
data class StoryBeat(
    val narration: String,
    val scene: Scene? = null,
    val choice: Choice? = null,
    val cameo: String? = null,
    val before: List<String> = emptyList(),
    val after: List<String> = emptyList(),
)

/** 주행 사건 → 이야기 토막을 고르는 규칙 키. */
enum class EventKey {
    DEPART, STOP, RESUME,
    SPEED_SLOW, SPEED_CRUISE, SPEED_FAST,
    TURN_AHEAD_LEFT, TURN_AHEAD_RIGHT, TURNED_LEFT, TURNED_RIGHT,
    RAIN_START, RAIN_STOP, TUNNEL_IN, TUNNEL_OUT;

    companion object {
        fun of(event: DrivingEvent): EventKey? = when (event) {
            DrivingEvent.Departed -> DEPART
            DrivingEvent.Stopped -> STOP
            DrivingEvent.Resumed -> RESUME
            is DrivingEvent.SpeedChanged -> when (event.band) {
                SpeedBand.SLOW -> SPEED_SLOW
                SpeedBand.CRUISE -> SPEED_CRUISE
                SpeedBand.FAST -> SPEED_FAST
            }
            is DrivingEvent.TurnAhead ->
                if (event.direction == TurnDirection.LEFT) TURN_AHEAD_LEFT else TURN_AHEAD_RIGHT
            is DrivingEvent.Turned ->
                if (event.direction == TurnDirection.LEFT) TURNED_LEFT else TURNED_RIGHT
            DrivingEvent.RainStarted -> RAIN_START
            DrivingEvent.RainStopped -> RAIN_STOP
            DrivingEvent.TunnelEntered -> TUNNEL_IN
            DrivingEvent.TunnelExited -> TUNNEL_OUT
            is DrivingEvent.Progress, DrivingEvent.Arrived -> null
        }
    }
}

/**
 * 동화 테마 = 규칙 테이블 + 리소스. 엔진 코드는 테마를 몰라도 된다.
 *
 * 같은 키에 토막이 여러 개면 나올 때마다 다음 것을 쓴다.
 * (정차를 몇 번 했느냐에 따라 만나는 친구가 달라지는 식)
 */
data class StoryTheme(
    val id: String,
    val title: String,
    val opening: StoryBeat,
    val rules: Map<EventKey, List<StoryBeat>>,
    /** 아이가 대답하기 전에 출발했을 때. */
    val resumeWithoutAnswer: StoryBeat,
    /** 진행률(0~1) 이정표 낭독. */
    val milestones: Map<Float, String>,
    /** 이정표 낭독 앞에 읽는 알림음. */
    val milestoneSound: List<String> = emptyList(),
    val ending: StoryBeat,
)
