package com.runningtale.app

import android.os.Handler
import android.os.Looper
import com.runningtale.story.signal.DrivingSignalSource
import com.runningtale.story.signal.DrivingSnapshot

/** 미리 만든 주행 기록을 실시간으로 재생한다. 시연 영상·PT용. */
class SimulatedSignalSource(
    private val snapshots: List<DrivingSnapshot>,
    private val speedFactor: Float = 1f,
) : DrivingSignalSource {
    private val handler = Handler(Looper.getMainLooper())
    private var index = 0

    override fun start(onSnapshot: (DrivingSnapshot) -> Unit) {
        stop()
        index = 0
        val tick = object : Runnable {
            override fun run() {
                val current = snapshots.getOrNull(index) ?: return
                onSnapshot(current)
                val next = snapshots.getOrNull(++index) ?: return
                val delay = ((next.timestampMs - current.timestampMs) / speedFactor).toLong()
                handler.postDelayed(this, delay)
            }
        }
        handler.post(tick)
    }

    override fun stop() {
        handler.removeCallbacksAndMessages(null)
    }
}
