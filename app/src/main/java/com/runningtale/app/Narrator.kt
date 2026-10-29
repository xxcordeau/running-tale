package com.runningtale.app

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * 동화 낭독. 한 번에 여러 줄(효과음 → 낭독 → 캐릭터 대사)을 차례로 읽는다.
 * 효과음([Soundtrack.effect])·녹음된 목소리([VoiceBank])가 있는 줄은 그 파일로, 없는 줄은 Pleos TTS SDK로 읽는다.
 * (이 차의 TTS 엔진은 Pleos caas라 안드로이드 기본 TextToSpeech로는 연결되지 않는다.
 *  서버 음성은 앱 인증 키가 있어야 나온다: local.properties의 pleos.tts.clientId / clientSecret)
 *
 * 새 낭독이 오면 하던 낭독은 끊는다. [say]의 interrupt가 false면 하던 낭독이 있을 때 건너뛴다.
 * 읽는 동안 [onSpeakingChanged]로 알려서 배경음악을 낮춘다. 오디오 포커스는 [Soundtrack]이 맡는다.
 */
class Narrator(private val context: Context, private val onSpeakingChanged: (Boolean) -> Unit = {}) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var player: MediaPlayer? = null
    private var ttsSpeaking = false
    private val queue = ArrayDeque<String>()
    private val tts = TextToSpeech(context, Mode.HYBRID)

    private val ttsEvents = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() {
            main.post { finishTtsLine() }
        }
        override fun onStop() = Unit
        override fun onError(type: ErrorType) {
            Log.w(TAG, "TTS 오류 $type")
            main.post { finishTtsLine() }
        }
    }

    init {
        // 권한(TTS_SERVICE)이 없거나 엔진이 없으면 SecurityException. 녹음만 나올 뿐 앱은 계속 간다.
        runCatching {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.setLocale(Locale.KOREA) // 기기 안 엔진은 "ko"만으론 안 되고 ko-KR이어야 한다
                    tts.setAudioAttributes(Soundtrack.ATTRIBUTES_SPEECH)
                    tts.addEventListener(ttsEvents)
                    registerCredentials()
                    main.post { ready = true }
                }

                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "Pleos TTS 초기화 실패 $type")
                }
            })
        }.onFailure { Log.w(TAG, "Pleos TTS 사용 불가", it) }
    }

    val isSpeaking: Boolean get() = player != null || ttsSpeaking || queue.isNotEmpty()

    /** [lines]를 차례로 읽는다. 빈 줄은 건너뛴다. */
    fun say(lines: List<String>, interrupt: Boolean = true) {
        if (!interrupt && isSpeaking) return
        // 바로 이어 읽으므로 "다 읽었다"를 알리지 않는다 (알리면 그 사이에 마이크가 열린다).
        stop(notify = false)
        queue.addAll(lines.filter { it.isNotBlank() })
        onSpeakingChanged(queue.isNotEmpty())
        next()
    }

    /** 하던 낭독을 모두 멈춘다 (오디오 포커스를 뺏겼거나 화면을 떠날 때). */
    fun stop() = stop(notify = true)

    private fun stop(notify: Boolean) {
        val was = isSpeaking
        queue.clear()
        releasePlayer()
        if (ttsSpeaking) runCatching { tts.stop() }
        ttsSpeaking = false
        if (was && notify) onSpeakingChanged(false)
    }

    fun shutdown() {
        stop()
        runCatching { tts.release() }
    }

    private fun next() {
        val line = queue.removeFirstOrNull() ?: return onSpeakingChanged(false)
        val clip = Soundtrack.effect(line) ?: VoiceBank.find(line)
        if (clip != null) {
            player = MediaPlayer.create(context, clip, Soundtrack.ATTRIBUTES_SPEECH, 0)?.apply {
                setOnCompletionListener {
                    releasePlayer()
                    next()
                }
                start()
            }
            if (player != null) return
        }
        if (!ready) return next() // TTS를 못 쓰면 그 줄은 건너뛴다
        ttsSpeaking = true
        tts.speak(line, true)
    }

    private fun finishTtsLine() {
        if (!ttsSpeaking) return
        ttsSpeaking = false
        next()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun registerCredentials() {
        val id = BuildConfig.PLEOS_TTS_CLIENT_ID
        val secret = BuildConfig.PLEOS_TTS_CLIENT_SECRET
        if (id.isBlank() || secret.isBlank()) {
            Log.w(TAG, "Pleos TTS 앱 키가 없어 서버 음성을 쓸 수 없음 (local.properties pleos.tts.clientId/clientSecret)")
            return
        }
        tts.registerApp(id, secret, object : OnServerConnectionListener {
            override fun onConnected() {
                Log.i(TAG, "Pleos TTS 서버 연결")
            }

            override fun onFailed(type: ErrorType) {
                Log.w(TAG, "Pleos TTS 서버 연결 실패 $type")
            }
        })
    }

    private companion object {
        const val TAG = "Narrator"
    }
}
