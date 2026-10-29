package com.runningtale.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.ProgressBar

/**
 * 그림 층을 움직이는 무대. 이야기 상태가 아니라 "무엇을 보여줄지"만 받는다.
 * 같은 값을 다시 받으면 아무것도 하지 않아서 0.5초마다 오는 신호에도 깜빡이지 않는다.
 *
 * 주행 중([setMoving] true)에는 움직이는 그림을 모두 멈춘다 (Pleos 운전자 주의 분산 가이드:
 * 주행 중 애니메이션·자동 전환 최소화). 장면은 바뀌되 페이드 없이 바로 바뀌고,
 * 비·반딧불이는 멈춘 그림으로 남는다. 정차하면 다시 움직인다.
 */
class StoryStage(root: View) {
    private val backgroundBack: ImageView = root.findViewById(R.id.backgroundBack)
    private val backgroundFront: ImageView = root.findViewById(R.id.backgroundFront)
    private val rainShade: View = root.findViewById(R.id.rainShade)
    private val rain: View = root.findViewById(R.id.rain)
    private val fireflies: ViewGroup = root.findViewById(R.id.fireflies)
    private val hero: ImageView = root.findViewById(R.id.hero)
    private val wind: View = root.findViewById(R.id.wind)
    private val companion: ImageView = root.findViewById(R.id.companion)
    private val sky: ImageView = root.findViewById(R.id.sky)
    private val progress: ProgressBar = root.findViewById(R.id.progress)
    private val progressMarker: View = root.findViewById(R.id.progressMarker)

    private var backgroundRes: Int? = null
    private var companionRes: Int? = null
    private var raining = false
    private var cave = false
    private var moving = false

    private val rainFall = ObjectAnimator.ofFloat(rain, View.TRANSLATION_Y, -RAIN_LOOP_PX, 0f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
    }
    private val fireflyFloats = (0 until fireflies.childCount).map { i ->
        ObjectAnimator.ofFloat(fireflies.getChildAt(i), View.TRANSLATION_Y, 0f, -40f - 15f * i).apply {
            duration = 1400L + 300L * i
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
    }

    init {
        val tile = BitmapDrawable(root.resources, BitmapFactory.decodeResource(root.resources, R.drawable.fx_rain))
        tile.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        rain.background = tile
        hero.translationX = HERO_ALONE_SHIFT_PX // 처음엔 친구가 없다
    }

    /** 차가 움직이는 중인지. 바뀔 때 반복 애니메이션을 켜고 끈다. */
    fun setMoving(value: Boolean) {
        if (value == moving) return
        moving = value
        updateLoops()
    }

    fun showBackground(res: Int?) {
        if (res == null || res == backgroundRes) return
        backgroundBack.setImageDrawable(backgroundFront.drawable)
        backgroundFront.setImageResource(res)
        if (moving || backgroundRes == null) {
            backgroundFront.alpha = 1f
        } else {
            backgroundFront.alpha = 0f
            backgroundFront.animate().alpha(1f).setDuration(700).start()
        }
        backgroundRes = res
    }

    /**
     * 오른쪽 친구 자리. 정차 중이면 통 튀어나오듯, 주행 중이면 그냥 나타난다.
     * 친구가 없으면 수레가 화면 가운데 쪽으로 나서고, 친구가 오면 왼쪽으로 비켜서 마주 본다.
     */
    fun showCompanion(res: Int?) {
        if (res == companionRes) return
        companionRes = res
        // 무지개 같은 하늘 그림은 친구 자리가 아니라 하늘에 띄우고, 수레는 친구가 없을 때처럼 가운데에 둔다.
        val inSky = res in SKY_ART
        val friend = res.takeUnless { inSky }
        placeHero(alone = friend == null)
        popIn(sky, res.takeIf { inSky })
        popIn(companion, friend)
    }

    /** 정차 중이면 통 튀어나오듯, 주행 중이면 그냥 나타난다. */
    private fun popIn(view: ImageView, res: Int?) {
        view.animate().cancel()
        if (res == null) {
            view.visibility = View.INVISIBLE
            return
        }
        view.setImageResource(res)
        view.visibility = View.VISIBLE
        if (moving) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.alpha = 0f
        view.scaleX = 0.7f
        view.scaleY = 0.7f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator()).setDuration(500).start()
    }

    private fun placeHero(alone: Boolean) {
        val x = if (alone) HERO_ALONE_SHIFT_PX else 0f
        hero.animate().cancel()
        if (moving) hero.translationX = x else hero.animate().translationX(x).setDuration(600).start()
    }

    fun showHero(visible: Boolean) {
        hero.alpha = if (visible) 1f else 0f
    }

    fun setRaining(on: Boolean) {
        if (on == raining) return
        raining = on
        hero.setImageResource(if (on) R.drawable.hero_rain else R.drawable.hero_carriage)
        rainShade.alpha = if (on) 1f else 0f
        rain.visibility = if (on) View.VISIBLE else View.GONE
        updateLoops()
    }

    fun setCave(on: Boolean) {
        if (on == cave) return
        cave = on
        fireflies.visibility = if (on) View.VISIBLE else View.GONE
        updateLoops()
    }

    /** 고속이면 바람 그림을 띄운다 (움직이지 않는 그림). */
    fun setSpeed(kmh: Float?) {
        wind.alpha = if ((kmh ?: 0f) >= 50f) 1f else 0f
    }

    fun setProgress(ratio: Float) {
        progress.progress = (ratio * 100).toInt()
        val track = (progress.width - progressMarker.width).coerceAtLeast(0)
        progressMarker.translationX = track * ratio.coerceIn(0f, 1f)
    }

    private fun updateLoops() {
        setLoop(rainFall, raining && !moving)
        fireflyFloats.forEach { setLoop(it, cave && !moving) }
    }

    private fun setLoop(animator: ValueAnimator, run: Boolean) {
        if (run && !animator.isStarted) animator.start()
        // cancel은 멈춘 자리에 그대로 둔다 → 주행 중에도 비·반딧불이가 정지 그림으로 보인다.
        if (!run && animator.isStarted) animator.cancel()
    }

    private companion object {
        /** fx_rain 타일 높이(330px)의 배수여야 이음매 없이 반복된다. 비 뷰 높이 ≥ 화면 + 이 값. */
        const val RAIN_LOOP_PX = 1320f

        /** 친구가 없을 때 수레가 가운데 쪽으로 나서는 거리. 앱 영역 약 1680 폭 기준. */
        const val HERO_ALONE_SHIFT_PX = 260f

        /** 친구 자리 대신 하늘에 띄우는 그림. */
        val SKY_ART = setOf(R.drawable.fx_rainbow)
    }
}
