package com.runningtale.app

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.runningtale.story.engine.StoryEngine
import com.runningtale.story.engine.StoryState
import com.runningtale.story.signal.DrivingEvent
import com.runningtale.story.signal.DrivingSignalSource
import com.runningtale.story.signal.DrivingSnapshot
import com.runningtale.story.signal.EventDetector
import com.runningtale.story.sim.DemoCourse
import com.runningtale.story.themes.ForestTheme
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var stage: StoryStage
    private lateinit var sceneTitle: TextView
    private lateinit var narration: TextView
    private lateinit var choices: LinearLayout
    private lateinit var startButtons: View
    private lateinit var logo: View
    private lateinit var storyPanel: View
    private lateinit var journeyScroll: View
    private lateinit var journey: LinearLayout
    private lateinit var signalOverlay: TextView
    private lateinit var eventCaption: TextView
    private val hideCaption = Runnable { eventCaption.visibility = View.GONE }

    private lateinit var narrator: Narrator
    private lateinit var soundtrack: Soundtrack
    private lateinit var voiceAnswer: VoiceAnswer
    private lateinit var listenHint: View
    /** 선택지 하나에서 못 알아듣고 다시 들은 횟수. 너무 오래 마이크를 열어 두지 않게 한다. */
    private var listenTries = 0
    private var source: DrivingSignalSource? = null
    private var engine = StoryEngine(ForestTheme)
    private var detector = EventDetector()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        stage = StoryStage(findViewById(R.id.root))
        sceneTitle = findViewById(R.id.sceneTitle)
        narration = findViewById(R.id.narration)
        choices = findViewById(R.id.choices)
        startButtons = findViewById(R.id.startButtons)
        logo = findViewById(R.id.logo)
        storyPanel = findViewById(R.id.storyPanel)
        journeyScroll = findViewById(R.id.journeyScroll)
        journey = findViewById(R.id.journey)
        signalOverlay = findViewById(R.id.signalOverlay)
        eventCaption = findViewById(R.id.eventCaption)
        listenHint = findViewById(R.id.listenHint)

        // 내비 안내 등에 소리를 뺏기면 낭독도 멈춘다.
        soundtrack = Soundtrack(this) { narrator.stop() }
        narrator = Narrator(this) { speaking ->
            soundtrack.duck(speaking)
            // 질문까지 다 읽고 나면 아이의 대답을 듣는다.
            if (!speaking) listenForAnswer()
        }
        voiceAnswer = VoiceAnswer(
            this,
            onListening = { on -> listenHint.visibility = if (on) View.VISIBLE else View.GONE },
            onHeard = ::onHeard,
        )
        render(engine.state, speak = false)
        // 시작 화면: 로고와 버튼만
        sceneTitle.visibility = View.GONE
        storyPanel.visibility = View.GONE
        stage.showHero(false)
        // 차 신호 표시는 시연·점검용. 장면 제목을 길게 누르면 켜고 끈다.
        sceneTitle.setOnLongClickListener {
            signalOverlay.visibility = if (signalOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            true
        }
        findViewById<ImageButton>(R.id.startButton).setOnClickListener {
            startDrive(SimulatedSignalSource(DemoCourse.snapshots()))
        }
        findViewById<ImageButton>(R.id.vehicleButton).setOnClickListener {
            // 차속 권한은 런타임 권한. 거절돼도 주행거리로 차속을 계산하므로 결과와 상관없이 시작한다.
            if (checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED) {
                startDrive(PleosSignalSource(this))
            } else {
                requestPermissions(arrayOf(CAR_SPEED), REQUEST_CAR_SPEED)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAR_SPEED) startDrive(PleosSignalSource(this))
    }

    override fun onStart() {
        super.onStart()
        soundtrack.resume()
    }

    /** 다른 앱으로 가면 소리를 멈춘다 (이야기는 그대로 이어진다). */
    override fun onStop() {
        voiceAnswer.stop()
        narrator.stop()
        soundtrack.pause()
        super.onStop()
    }

    override fun onDestroy() {
        source?.stop()
        narrator.shutdown()
        voiceAnswer.release()
        soundtrack.release()
        super.onDestroy()
    }

    private fun startDrive(newSource: DrivingSignalSource) {
        source?.stop()
        engine = StoryEngine(ForestTheme)
        detector = EventDetector()
        startButtons.visibility = View.GONE
        logo.visibility = View.GONE
        sceneTitle.visibility = View.VISIBLE
        storyPanel.visibility = View.VISIBLE
        // 데모 코스에선 어떤 신호로 이야기가 흐르는지 보여 주고, 실제 주행에선 숨긴다.
        signalOverlay.visibility = if (newSource is SimulatedSignalSource) View.VISIBLE else View.GONE
        render(engine.state)
        source = newSource.also { it.start(::onSnapshot) }
    }

    private fun listenForAnswer() {
        if (engine.state.openChoice != null && listenTries < MAX_LISTEN_TRIES) voiceAnswer.listen()
    }

    /** 알아들으면 고르고, 못 알아들으면 몇 번 더 듣는다. 버튼은 그동안에도 누를 수 있다. */
    private fun onHeard(text: String) {
        val options = engine.state.openChoice?.options.orEmpty()
        val chosen = engine.chooseBySpeech(text)
        showCaption(EventCaption.heard(text, chosen?.let { s -> options.firstOrNull { it.narration == s.narration }?.label }))
        if (chosen != null) {
            render(chosen)
        } else {
            listenTries++
            listenForAnswer()
        }
    }

    private fun onSnapshot(snapshot: DrivingSnapshot) {
        showSignal(snapshot)
        // 장면 전환보다 먼저 정해야 주행 중 전환이 페이드 없이 바로 된다.
        stage.setMoving(abs(snapshot.speedKmh ?: 0f) >= MOVING_KMH)
        stage.setSpeed(snapshot.speedKmh)
        for (event in detector.onSnapshot(snapshot)) {
            EventCaption.of(event, snapshot)?.let(::showCaption)
            val before = engine.state.narration
            // 속도 낭독은 곁가지라 이야기 낭독을 끊지 않는다.
            val interrupt = event !is DrivingEvent.SpeedChanged
            engine.onEvent(event)?.let { render(it, speak = it.narration != before, interrupt = interrupt) }
        }
        if (engine.state.finished) {
            source?.stop()
            source = null
            startButtons.visibility = View.VISIBLE
        }
    }

    private fun render(state: StoryState, speak: Boolean = true, interrupt: Boolean = true) {
        val scene = state.scene
        sceneTitle.text = scene.title
        narration.text = state.narration
        stage.showBackground(Art.background(scene.art))
        val companionKey = state.cameo ?: scene.npc
        stage.showCompanion(companionKey?.let(Art::sprite))
        stage.showHero(!Art.includesHero(companionKey))
        stage.setRaining(state.raining)
        stage.setCave(scene.art == "cave")
        stage.setProgress(state.progress)

        choices.removeAllViews()
        val choice = state.openChoice
        if (choice == null) voiceAnswer.stop() else if (speak) listenTries = 0
        choices.visibility = if (choice != null) View.VISIBLE else View.GONE
        choice?.options?.forEachIndexed { i, option ->
            choices.addView(Button(this).apply {
                text = option.label
                textSize = 30f
                setTextColor(Color.parseColor("#5B3A1E"))
                setBackgroundResource(if (i % 2 == 0) R.drawable.ui_choice_star else R.drawable.ui_choice_heart)
                // 버튼 그림 왼쪽 별·하트와 오른쪽 화살표를 피해서 글자를 가운데에
                setPadding(130, 0, 115, 0)
                stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(560, 132).apply { marginEnd = if (i == 0) 32 else 0 }
                setOnClickListener { engine.choose(i)?.let { render(it) } }
            })
        }

        journeyScroll.visibility = if (state.finished) View.VISIBLE else View.GONE
        if (state.finished) showJourney(state)
        soundtrack.playFor(scene.art, state.finished)
        soundtrack.setRaining(state.raining)
        if (speak) narrator.say(state.before + state.narration + state.after, interrupt)
    }

    /** 오늘의 모험 카드: 지나온 장면 아이콘을 길처럼 잇는다. */
    private fun showJourney(state: StoryState) {
        journey.removeAllViews()
        state.trail.forEachIndexed { i, scene ->
            if (i > 0) {
                journey.addView(TextView(this).apply {
                    text = "›"
                    textSize = 40f
                    setTextColor(Color.parseColor("#C9A06A"))
                    gravity = Gravity.CENTER
                    setPadding(8, 0, 8, 0)
                })
            }
            val icon = Art.icon(scene.art)
            journey.addView(
                if (icon != null) {
                    ImageView(this).apply {
                        setImageResource(icon)
                        contentDescription = scene.title
                        layoutParams = LinearLayout.LayoutParams(110, 104)
                    }
                } else {
                    TextView(this).apply { text = scene.title; textSize = 24f }
                },
            )
        }
    }

    /** 신호 표시를 켠 시연 때만 자막을 잠깐 띄운다 (움직임 없이 나타났다 사라짐). */
    private fun showCaption(text: String) {
        if (signalOverlay.visibility != View.VISIBLE) return
        eventCaption.text = text
        eventCaption.visibility = View.VISIBLE
        eventCaption.removeCallbacks(hideCaption)
        eventCaption.postDelayed(hideCaption, CAPTION_MS)
    }

    private fun showSignal(s: DrivingSnapshot) {
        signalOverlay.text = buildString {
            appendLine("차속  ${s.speedKmh?.let { "%.0f km/h".format(it) } ?: "-"}")
            appendLine("기어  ${s.gear}")
            appendLine("조향  ${s.steeringAngleDeg?.let { "%.0f°".format(it) } ?: "-"}")
            appendLine("와이퍼 ${s.wiperOn?.let { if (it) "ON" else "OFF" } ?: "-"}")
            appendLine("회전  ${s.nextTurn?.let { "${it.direction} ${it.distanceM}m" } ?: "-"}")
            append("남은길 ${s.remainingDistanceM?.let { "${it}m" } ?: "-"}")
            (source as? PleosSignalSource)?.let { append("\n${it.describe()}") }
        }
    }

    private companion object {
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
        const val REQUEST_CAR_SPEED = 1
        const val MAX_LISTEN_TRIES = 3
        const val CAPTION_MS = 5000L
        /** 이 속도부터는 주행 중으로 보고 애니메이션을 멈춘다. */
        const val MOVING_KMH = 3f
    }
}
