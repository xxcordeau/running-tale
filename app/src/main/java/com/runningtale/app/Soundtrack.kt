package com.runningtale.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.runningtale.story.themes.ForestSounds

/**
 * 배경음악·빗소리 반복 재생과 오디오 포커스. 소리 파일은 tools/make_sounds.py가 만든다.
 *
 * Pleos 오디오 포커스 가이드대로 재생 전에 포커스를 받고,
 * 내비 안내(LOSS_TRANSIENT_CAN_DUCK)엔 소리를 줄이고, 잠깐 뺏기면(LOSS_TRANSIENT) 멈췄다가 돌아오면 다시 튼다.
 * 낭독 중에는 [duck]으로 음악을 낮춘다.
 */
class Soundtrack(private val context: Context, private val onInterrupted: () -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var music: MediaPlayer? = null
    private var musicRes: Int? = null
    private var rain: MediaPlayer? = null
    private var narrating = false
    private var navDucked = false
    private var paused = false

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(ATTRIBUTES)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    navDucked = false
                    resume()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    navDucked = true
                    applyVolume()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> {
                    pause()
                    onInterrupted()
                }
            }
        }
        .build()

    /** 장면 그림 키에 맞는 배경음악. 같은 곡이면 이어서 튼다. */
    fun playFor(sceneArt: String?, finished: Boolean = false) {
        val res = when {
            finished -> R.raw.bgm_ending
            sceneArt == null -> null
            sceneArt == "cave" -> R.raw.bgm_cave
            sceneArt == "castle" -> R.raw.bgm_ending
            sceneArt in setOf("forest", "dragon", "fox", "owl") -> R.raw.bgm_forest
            else -> R.raw.bgm_day
        }
        if (res == musicRes) return
        music?.release()
        musicRes = res
        music = res?.let { loop(it) }
        start()
    }

    fun setRaining(on: Boolean) {
        if (on == (rain != null)) return
        rain?.release()
        rain = if (on) loop(R.raw.sfx_rain_loop) else null
        start()
    }

    fun duck(on: Boolean) {
        narrating = on
        applyVolume()
    }

    fun pause() {
        paused = true
        music?.takeIf { it.isPlaying }?.pause()
        rain?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        paused = false
        start()
    }

    fun release() {
        music?.release()
        rain?.release()
        music = null
        rain = null
        musicRes = null
        audio.abandonAudioFocusRequest(focus)
    }

    private fun start() {
        if (paused) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        applyVolume()
        music?.takeIf { !it.isPlaying }?.start()
        rain?.takeIf { !it.isPlaying }?.start()
    }

    private fun applyVolume() {
        val ducked = narrating || navDucked
        val m = if (ducked) MUSIC_DUCKED else MUSIC_VOLUME
        val r = if (ducked) RAIN_DUCKED else RAIN_VOLUME
        music?.setVolume(m, m)
        rain?.setVolume(r, r)
    }

    private fun loop(res: Int): MediaPlayer? =
        MediaPlayer.create(context, res, ATTRIBUTES, audio.generateAudioSessionId())?.apply { isLooping = true }

    companion object {
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        /** 낭독·효과음. 같은 미디어 용도라 배경음악과 한 포커스를 쓴다. */
        val ATTRIBUTES_SPEECH: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        const val MUSIC_VOLUME = 0.35f
        const val MUSIC_DUCKED = 0.1f
        const val RAIN_VOLUME = 0.3f
        const val RAIN_DUCKED = 0.12f

        /** 낭독 줄의 효과음 글자 → 소리 파일. 여기 없는 줄은 녹음(VoiceBank)이나 TTS로 읽는다. */
        private val effects: Map<String, Int> = mapOf(
            ForestSounds.DEPART to R.raw.sfx_depart,
            ForestSounds.SLOW to R.raw.sfx_slow,
            ForestSounds.CRUISE to R.raw.sfx_cruise,
            ForestSounds.FAST to R.raw.sfx_fast,
            ForestSounds.TURN_AHEAD to R.raw.sfx_chime,
            ForestSounds.TURN_FOREST to R.raw.sfx_rustle,
            ForestSounds.TURN_PATH to R.raw.sfx_crunch,
            ForestSounds.STOP to R.raw.sfx_stop,
            ForestSounds.CHOICE to R.raw.sfx_sparkle,
            ForestSounds.CORRECT to R.raw.sfx_dingdong,
            ForestSounds.RAIN_START to R.raw.sfx_rain_start,
            ForestSounds.RAIN_STOP to R.raw.sfx_twinkle,
            ForestSounds.TUNNEL_IN to R.raw.sfx_cave,
            ForestSounds.TUNNEL_OUT to R.raw.sfx_tada,
            ForestSounds.MILESTONE to R.raw.sfx_arpeggio,
            ForestSounds.FANFARE to R.raw.sfx_fanfare,
            ForestSounds.GATE to R.raw.sfx_gate,
        )

        fun effect(line: String): Int? = effects[line]
    }
}
