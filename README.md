# 달리는 동화

주행 신호가 동화 속 사건이 되는 Pleos Connect 키즈 앱. 기획은 [기획안.md](기획안.md).

## 구조

```
story/   순수 Kotlin 스토리 엔진 (Android 없이 빌드·테스트)
  signal/  DrivingSnapshot(주행 상태) → EventDetector → DrivingEvent(출발·정차·회전·비…)
  engine/  StoryEngine: 테마의 규칙 테이블대로 장면·낭독·선택지를 만든다
  themes/  ForestTheme (숲속 모험). 테마 추가 = 규칙과 리소스만 추가
  sim/     DemoCourse: 기획안 시연 계획의 3분 주행 시나리오
app/     Android Automotive 앱
  SimulatedSignalSource  데모 코스 재생 (시연 영상·PT)
  PleosSignalSource      Pleos Vehicle·NaviHelper + AAOS 차속
  MainActivity           낭독·정차 때만 열리는 선택지·오늘의 모험 카드·주행 신호 오버레이
  StoryStage             그림 층: 배경 교차 페이드, 주인공(속도만큼 들썩임), 친구 자리, 비·반딧불·바람
  Art                    엔진의 그림 키(Scene.art/npc, cameo) → drawable
달리는 동화asset/  원본 그림 (주인공 = 돼지 후드 꼬마 보리 + 멧돼지 수레)
tools/export_assets.py  원본에서 잘라 app/src/main/res/drawable-nodpi 로 내보낸다 (손으로 고치지 말고 다시 실행)
tools/export_voice.py   달리는동화_voice.zip → res/raw/voice_*.mp3 (낭독 녹음)
tools/make_sounds.py    효과음 sfx_*·배경음악 bgm_* 를 합성해 res/raw 에 wav로 만든다. 더 좋은 파일이 생기면 같은 이름으로 교체
```

## 빌드

- Gradle 데몬은 JDK 21로 고정돼 있다 (`gradle/gradle-daemon-jvm.properties`). Gradle 8.14는 JDK 25를 지원하지 않는다.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (ANDROID_HOME). SDK가 없으면 `:app`은 settings에서 빠진다.
- 엔진 테스트: `gradlew :story:test`
- 앱: `gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Pleos Connect Emulator 설치는 document.pleos.ai의 setup-connect-sdk-emulator 문서 참고.

**경로에 한글이 있으면 안 된다.** 한글 경로에서는 테스트 JVM이 클래스를 못 찾고 AGP도 비ASCII 경로를 막는다.
그래서 `03_PJ/현대자동차 공모전`에서 `03_PJ/running-tale`로 옮겼다.

## 남은 일

- 터널: NaviHelper TBT의 `TURN_TUNNEL` → inTunnel 연결
- 내비 경로로 회전 예고·진행률 실제 확인
- 낭독을 Pleos TTS로 교체, 내비 안내 음성 때 볼륨 낮추기
- 정차 선택지 음성 대답 (Pleos STT)
- Pleos 앱 키 (local.properties `pleos.tts.clientId` / `pleos.tts.clientSecret`): 없어도 기기 안 TTS·STT로 동작하지만, 있으면 서버 음성·인식(품질↑)을 쓴다
- Pleos TTS/STT 로케일은 반드시 `Locale.KOREA`(ko-KR). `Locale.KOREAN`(ko)이면 기기 안 엔진이 unsupported locale로 실패한다
