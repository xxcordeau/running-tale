package com.runningtale.app

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * 선택지 음성 대답 (Pleos SpeechToText SDK). 질문 낭독이 끝나면 [listen]으로 한 번 듣고,
 * 최종 인식 결과를 [onHeard]로 넘긴다. 못 알아들었으면 부른 쪽이 다시 [listen]한다.
 *
 * 서버 인식은 TTS와 같은 앱 인증 키(local.properties pleos.tts.clientId/clientSecret)가 필요하다.
 * 권한(STT_SERVICE)이나 엔진이 없으면 조용히 꺼지고 터치로만 고른다.
 */
class VoiceAnswer(
    context: Context,
    private val onListening: (Boolean) -> Unit,
    private val onHeard: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val stt = SpeechToText(context, Mode.HYBRID)
    private var ready = false
    private var listening = false

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (isFinal) main.post { finish(text) }
        }

        override fun onError(type: ErrorType) {
            Log.w(TAG, "음성 인식 오류 $type")
            main.post { finish(null) }
        }

        override fun onEndedRecognition() {
            main.post { finish(null) }
        }

        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        runCatching {
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    stt.setLocale(Locale.KOREA) // 기기 안 엔진은 "ko"만으론 안 되고 ko-KR이어야 한다
                    stt.addListener(results)
                    registerCredentials()
                    main.post { ready = true }
                }

                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "Pleos STT 초기화 실패 $type")
                }
            })
        }.onFailure { Log.w(TAG, "Pleos STT 사용 불가", it) }
    }

    fun listen() {
        if (!ready || listening) return
        listening = true
        onListening(true)
        runCatching { stt.request() }.onFailure {
            Log.w(TAG, "음성 인식 시작 실패", it)
            finish(null)
        }
    }

    fun stop() {
        if (!listening) return
        runCatching { stt.stop() }
        listening = false
        onListening(false)
    }

    fun release() {
        stop()
        runCatching { stt.release() }
    }

    /** 최종 결과(또는 실패)는 한 번만 처리한다. 결과 없이 끝나면 [onHeard]는 부르지 않는다. */
    private fun finish(text: String?) {
        if (!listening) return
        listening = false
        onListening(false)
        if (!text.isNullOrBlank()) onHeard(text)
    }

    private fun registerCredentials() {
        val id = BuildConfig.PLEOS_TTS_CLIENT_ID
        val secret = BuildConfig.PLEOS_TTS_CLIENT_SECRET
        if (id.isBlank() || secret.isBlank()) {
            Log.w(TAG, "Pleos 앱 키가 없어 서버 음성 인식을 쓸 수 없음")
            return
        }
        stt.registerApp(id, secret, object : OnServerConnectionListener {
            override fun onConnected() {
                Log.i(TAG, "Pleos STT 서버 연결")
            }

            override fun onFailed(type: ErrorType) {
                Log.w(TAG, "Pleos STT 서버 연결 실패 $type")
            }
        })
    }

    private companion object {
        const val TAG = "VoiceAnswer"
    }
}
