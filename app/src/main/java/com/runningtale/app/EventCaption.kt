package com.runningtale.app

import com.runningtale.story.signal.DrivingEvent
import com.runningtale.story.signal.DrivingSnapshot
import com.runningtale.story.signal.SpeedBand
import com.runningtale.story.signal.TurnDirection

/**
 * 시연 영상용 자막: "어떤 차 신호 → 이야기 속 무엇". 심사위원이 영상만 보고도
 * 미리 짠 애니메이션이 아니라 주행 신호로 움직인다는 걸 알 수 있게 한다.
 * 진행률(Progress)처럼 자주 오는 사건은 자막을 띄우지 않는다.
 */
object EventCaption {
    fun of(event: DrivingEvent, s: DrivingSnapshot): String? = when (event) {
        DrivingEvent.Departed -> "🚗 기어 D · 출발  →  수레가 덜컹 출발"
        is DrivingEvent.SpeedChanged -> "🚗 차속 ${s.speedKmh?.let { "%.0f".format(it) } ?: "-"} km/h  →  " + when (event.band) {
            SpeedBand.SLOW -> "터벅터벅 천천히"
            SpeedBand.CRUISE -> "다그닥 다그닥"
            SpeedBand.FAST -> "휘이잉~ 바람이 불어요"
        }
        is DrivingEvent.TurnAhead -> "🧭 내비 안내: ${event.distanceM}m 앞 ${event.direction.turnWord()}  →  갈림길 예고"
        is DrivingEvent.Turned -> "🚗 핸들 ${event.direction.side()}  →  " +
            if (event.direction == TurnDirection.RIGHT) "숲길로 들어가요" else "오솔길로 들어가요"
        DrivingEvent.Stopped -> "🚗 정차 (차속 0)  →  숲속 친구 등장 · 선택지 열림"
        DrivingEvent.Resumed -> "🚗 다시 출발  →  선택지 닫힘 · 이야기 계속"
        DrivingEvent.RainStarted -> "🚗 와이퍼 ON  →  동화 속에도 비가 내려요"
        DrivingEvent.RainStopped -> "🚗 와이퍼 OFF  →  비가 그치고 무지개"
        DrivingEvent.TunnelEntered -> "🧭 내비 안내: 터널 진입  →  반딧불 동굴로"
        DrivingEvent.TunnelExited -> "🧭 터널 통과  →  동굴 밖으로 짠!"
        DrivingEvent.Arrived -> "🧭 목적지 도착 · 주차  →  별빛 성 엔딩"
        is DrivingEvent.Progress -> null
    }

    fun heard(text: String, chosen: String?): String =
        if (chosen != null) "🎙 음성 인식: \"$text\"  →  '$chosen' 선택" else "🎙 음성 인식: \"$text\"  →  다시 듣는 중"

    private fun TurnDirection.turnWord() = if (this == TurnDirection.RIGHT) "우회전" else "좌회전"
    private fun TurnDirection.side() = if (this == TurnDirection.RIGHT) "오른쪽" else "왼쪽"
}
