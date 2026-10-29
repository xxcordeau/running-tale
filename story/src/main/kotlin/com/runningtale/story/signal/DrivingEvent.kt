package com.runningtale.story.signal

enum class SpeedBand { SLOW, CRUISE, FAST }

/** 주행 신호에서 뽑아낸, 이야기가 반응하는 사건. */
sealed interface DrivingEvent {
    data object Departed : DrivingEvent
    data object Stopped : DrivingEvent
    data object Resumed : DrivingEvent
    data class SpeedChanged(val band: SpeedBand) : DrivingEvent
    data class TurnAhead(val direction: TurnDirection, val distanceM: Int) : DrivingEvent
    data class Turned(val direction: TurnDirection) : DrivingEvent
    data object RainStarted : DrivingEvent
    data object RainStopped : DrivingEvent
    data object TunnelEntered : DrivingEvent
    data object TunnelExited : DrivingEvent
    /** 0.0 ~ 1.0, 목적지까지 지나온 비율. */
    data class Progress(val ratio: Float) : DrivingEvent
    data object Arrived : DrivingEvent
}
