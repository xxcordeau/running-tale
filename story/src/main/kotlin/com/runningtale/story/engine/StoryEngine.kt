package com.runningtale.story.engine

import com.runningtale.story.signal.DrivingEvent

data class StoryState(
    val scene: Scene,
    val narration: String,
    /** 아이 선택지. 정차 중에만 non-null (기획안 안전 설계: 터치 잠금). */
    val openChoice: Choice? = null,
    val progress: Float = 0f,
    val raining: Boolean = false,
    val finished: Boolean = false,
    /** 이번에 읽을 낭독 앞뒤 효과음·대사. 낭독이 바뀔 때마다 새로 정해진다. */
    val before: List<String> = emptyList(),
    val after: List<String> = emptyList(),
    /** 친구 자리에 잠깐 보여줄 그림 키. null이면 장면의 [Scene.npc]. */
    val cameo: String? = null,
    /** 오늘의 모험 카드에 그릴 지나온 장면들. */
    val trail: List<Scene> = emptyList(),
) {
    val journey: List<String> get() = trail.map { it.title }
}

/** 주행 사건을 받아 테마의 규칙 테이블대로 이야기를 진행한다. */
class StoryEngine(private val theme: StoryTheme) {
    private val usage = mutableMapOf<EventKey, Int>()
    private val passedMilestones = mutableSetOf<Float>()

    var state: StoryState = run {
        val scene = requireNotNull(theme.opening.scene) { "opening에는 장면이 있어야 합니다" }
        StoryState(scene = scene, narration = theme.opening.narration, trail = listOf(scene))
    }
        private set

    /** 상태가 바뀌었으면 새 상태, 아니면 null. */
    fun onEvent(event: DrivingEvent): StoryState? {
        if (state.finished) return null
        val next = when (event) {
            DrivingEvent.Arrived -> applyBeat(theme.ending, state.copy(openChoice = null, progress = 1f))
                .copy(finished = true)
            is DrivingEvent.Progress -> onProgress(event.ratio)
            DrivingEvent.Departed, DrivingEvent.Resumed -> {
                if (state.openChoice != null) {
                    applyBeat(theme.resumeWithoutAnswer, state.copy(openChoice = null))
                } else {
                    beatFor(event)?.let { applyBeat(it, state) }
                }
            }
            else -> {
                // 선택지가 열려 있는 동안엔 다른 사건으로 낭독을 끊지 않는다.
                // 다만 날씨는 그림에 이어져야 하므로 상태만 바꾼다 (감지기는 같은 사건을 다시 보내지 않는다).
                if (state.openChoice != null) {
                    val weather = when (event) {
                        DrivingEvent.RainStarted -> state.copy(raining = true)
                        DrivingEvent.RainStopped -> state.copy(raining = false)
                        else -> return null
                    }
                    state = weather
                    return weather
                }
                beatFor(event)?.let { beat ->
                    val withWeather = when (event) {
                        DrivingEvent.RainStarted -> state.copy(raining = true)
                        DrivingEvent.RainStopped -> state.copy(raining = false)
                        else -> state
                    }
                    applyBeat(
                        beat, withWeather,
                        distanceM = (event as? DrivingEvent.TurnAhead)?.distanceM,
                        allowChoice = event == DrivingEvent.Stopped,
                    )
                }
            }
        } ?: return null
        state = next
        return next
    }

    /** 아이의 선택. 선택지가 닫혀 있으면 무시하고 null. */
    fun choose(index: Int): StoryState? {
        val option = state.openChoice?.options?.getOrNull(index) ?: return null
        state = state.copy(
            narration = option.narration,
            before = option.before,
            after = option.after,
            openChoice = null,
            cameo = option.cameo ?: state.cameo,
        )
        return state
    }

    /**
     * 아이가 말한 [heard]로 고른다. 보기 단어가 딱 하나에만 걸리면 그것, 아니면 "첫 번째/두 번째"로 본다.
     * 알아듣지 못했거나 두 보기에 다 걸리면(“사과도 노래도”) null.
     */
    fun chooseBySpeech(heard: String): StoryState? {
        val options = state.openChoice?.options ?: return null
        val text = heard.replace(" ", "")
        val hits = options.indices.filter { i ->
            val option = options[i]
            option.keywords.ifEmpty { listOf(option.label) }.any { it.replace(" ", "") in text }
        }
        val index = hits.singleOrNull()
            ?: if (hits.isEmpty()) ORDINALS.indexOfFirst { words -> words.any { it in text } }.takeIf { it in options.indices } else null
        return index?.let(::choose)
    }

    private fun onProgress(ratio: Float): StoryState {
        val milestone = theme.milestones.keys.sorted()
            .lastOrNull { it <= ratio && it !in passedMilestones }
        if (milestone == null || state.openChoice != null) return state.copy(progress = ratio)
        passedMilestones += theme.milestones.keys.filter { it <= ratio }
        return state.copy(
            progress = ratio,
            narration = theme.milestones.getValue(milestone),
            before = theme.milestoneSound,
            after = emptyList(),
        )
    }

    private fun beatFor(event: DrivingEvent): StoryBeat? {
        val key = EventKey.of(event) ?: return null
        val beats = theme.rules[key].orEmpty()
        if (beats.isEmpty()) return null
        val n = usage.getOrDefault(key, 0)
        usage[key] = n + 1
        return beats[n % beats.size]
    }

    /** 선택지는 정차 사건일 때만 연다. 테마가 다른 토막에 선택지를 넣어도 주행 중엔 열리지 않는다. */
    private fun applyBeat(
        beat: StoryBeat,
        base: StoryState,
        distanceM: Int? = null,
        allowChoice: Boolean = false,
    ): StoryState {
        val narration = distanceM?.let { beat.narration.replace("{distance}", roundDistance(it)) }
            ?: beat.narration
        val scene = beat.scene ?: base.scene
        return base.copy(
            scene = scene,
            narration = narration,
            before = beat.before,
            // 선택지가 열리면 낭독 끝에 보기를 묻는 질문을 붙인다.
            after = beat.after + listOfNotNull(beat.choice?.prompt?.takeIf { allowChoice }),
            openChoice = if (allowChoice) beat.choice else null,
            // 장면이 있는 토막은 등장 그림을 새로 정하고, 낭독만 바뀌는 토막은 이어간다.
            cameo = if (beat.scene != null || beat.clearCameo) beat.cameo else beat.cameo ?: base.cameo,
            trail = if (scene != base.scene) base.trail + scene else base.trail,
        )
    }

    private fun roundDistance(m: Int): String = if (m >= 100) "${m / 50 * 50}" else "$m"

    private companion object {
        /** 보기 순서로 고르는 말. 인덱스 = 보기 번호. */
        val ORDINALS = listOf(
            listOf("첫번째", "첫째", "처음", "1번", "일번", "하나"),
            listOf("두번째", "둘째", "2번", "이번", "둘"),
        )
    }
}
