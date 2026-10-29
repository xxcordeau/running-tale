package com.runningtale.app

import com.runningtale.story.engine.EventKey
import com.runningtale.story.engine.StoryTheme
import com.runningtale.story.themes.ForestTheme

/**
 * 녹음된 낭독(달리는동화_voice.zip → res/raw, tools/export_voice.py). 낭독 문장 → 녹음 파일.
 * 테마 구조에서 문장을 꺼내 연결하므로 문장 순서가 바뀌어도 맞는다. 녹음이 없는 문장은 TTS로 읽는다.
 *
 * 회전 예고(`{distance}`)는 거리마다 문장이 달라 녹음 대신 TTS로 읽는다.
 */
object VoiceBank {
    private val byText: Map<String, Int> = build(ForestTheme)

    fun find(text: String): Int? = byText[text]

    private fun build(theme: StoryTheme): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        fun rule(key: EventKey, i: Int = 0) = theme.rules[key]?.getOrNull(i)
        fun put(text: String?, res: Int) {
            if (text != null) map[text] = res
        }

        put(theme.opening.narration, R.raw.voice_opening)
        put(rule(EventKey.DEPART)?.narration, R.raw.voice_depart)
        put(rule(EventKey.SPEED_SLOW)?.narration, R.raw.voice_speed_slow)
        put(rule(EventKey.SPEED_CRUISE)?.narration, R.raw.voice_speed_cruise)
        put(rule(EventKey.SPEED_FAST)?.narration, R.raw.voice_speed_fast)
        put(rule(EventKey.TURNED_RIGHT)?.narration, R.raw.voice_turned_right)
        put(rule(EventKey.TURNED_LEFT)?.narration, R.raw.voice_turned_left)

        val stopVoices = listOf(
            Triple(R.raw.voice_stop_dragon, R.raw.voice_dragon_apple, R.raw.voice_dragon_song),
            Triple(R.raw.voice_stop_fox, R.raw.voice_fox_moon, R.raw.voice_fox_firefly),
            Triple(R.raw.voice_stop_owl, R.raw.voice_owl_flower, R.raw.voice_owl_stream),
        )
        stopVoices.forEachIndexed { i, (stop, first, second) ->
            val beat = rule(EventKey.STOP, i) ?: return@forEachIndexed
            put(beat.narration, stop)
            put(beat.choice?.options?.getOrNull(0)?.narration, first)
            put(beat.choice?.options?.getOrNull(1)?.narration, second)
        }

        put(rule(EventKey.RESUME)?.narration, R.raw.voice_resume)
        put(theme.resumeWithoutAnswer.narration, R.raw.voice_resume_no_answer)
        put(rule(EventKey.RAIN_START)?.narration, R.raw.voice_rain_start)
        put(rule(EventKey.RAIN_STOP)?.narration, R.raw.voice_rain_stop)
        put(rule(EventKey.TUNNEL_IN)?.narration, R.raw.voice_tunnel_in)
        put(rule(EventKey.TUNNEL_OUT)?.narration, R.raw.voice_tunnel_out)
        put(theme.milestones[0.5f], R.raw.voice_milestone_half)
        put(theme.milestones[0.9f], R.raw.voice_milestone_flag)
        put(theme.ending.narration, R.raw.voice_ending)
        return map
    }
}
