# 달리는 동화
<img width="997" height="710" alt="image" src="https://github.com/user-attachments/assets/8c6601c0-83ce-4380-afae-7053ba5766c8" />


**실제 주행 신호가 동화 속 사건이 되는 뒷좌석 키즈 앱** (Pleos Connect, 차량용 앱 개발 공모전 출품작)

차가 출발하면 멧돼지 수레가 덜컹 출발하고, 브레이크를 밟아 멈추면 숲속 친구가 나타나 선택지를 내고,
와이퍼가 켜지면 이야기 속에도 비가 내립니다. 아이는 정차 중에 화면을 누르거나 **말로 대답**해서 이야기를 고릅니다.
같은 길도 매번 다른 이야기가 되고, 도착하면 지나온 장면이 "오늘의 모험" 카드로 남습니다.

- 시연 영상: https://youtu.be/qGSsbPX2o2w
- 설치 파일: [`apk/허정연_달리는동화_release.apk`](apk/)
- 기획안: [기획안.md](기획안.md)

## 주행 신호 → 이야기

| 차 신호 (Pleos SDK) | 이야기 속 사건 |
|---|---|
| 기어 D + 차속 (Vehicle, AAOS 차속) | 출발 "덜컹덜컹!", 속도 단계(터벅터벅 / 다그닥 / 휘이잉~) |
| 차속 0 유지 (Vehicle) | 정차 → 숲속 친구 등장, **선택지는 정차 중에만** 열림 |
| 다음 회전 TBT (NaviHelper) | 회전 예고 "띠링!", 갈림길 표지판 |
| 조향각 (Vehicle) | 실제로 꺾은 방향으로 이야기도 꺾임 |
| 앞 와이퍼 (Vehicle) | 비가 오기 시작 / 그침 → 무지개 |
| 터널 TBT (NaviHelper `TURN_TUNNEL`) | 반딧불 동굴로 들어갔다 나옴 |
| 남은 거리·도착 (NaviHelper) | 진행률 표시, 도착하면 별빛 성 + 오늘의 모험 카드 |
| 음성 (TextToSpeech / SpeechToText) | 녹음이 없는 대사는 Pleos TTS로 읽고, 아이의 대답("사과!")을 Pleos STT로 알아들음 |

## 차량 환경을 위한 설계

- **운전자 주의 분산 최소화**: 주행 중(3 km/h 이상)에는 페이드·튀어나오기·비 내리는 애니메이션을 모두 멈추고 정지 그림만 바꿉니다. 조작(선택지)은 정차 중에만 열립니다.
- **오디오 포커스**: 내비 안내가 나오면 배경음악을 낮추고(duck), 포커스를 잃으면 낭독을 멈춥니다. 낭독 중에도 음악을 낮춥니다.
- **신호 흔들림 보정**: 속도 경계 ±4 km/h 여유와 6초 유지 조건, 5초 정차 판정, 후진(R) 무시, 주차 60초 후 종료 등으로 실제 주행에서 사건이 남발되지 않게 했습니다.
- **앱 키 없이도 동작**: TTS/STT는 기기 안 엔진으로 동작하고, 앱 키가 있으면 서버 엔진을 씁니다.

## 실행해 보기

1. Pleos Connect Emulator에 APK 설치: `adb install apk/허정연_달리는동화_release.apk`
2. **데모 코스**: 4분 30초짜리 주행 시나리오(출발 → 가속 → 우회전 → 터널 → 정차: 용 → 비 → 정차: 여우 → 무지개 → 정차: 올빼미 → 도착)를 재생합니다. 오른쪽 위에 지금 들어오는 차 신호가, 위쪽 가운데에 "와이퍼 ON → 비가 내려요" 같은 신호→사건 자막이 표시됩니다.
3. **모험 시작**: Virtual Console의 차속·기어·조향·와이퍼와 내비 경로 신호로 실제 주행처럼 이야기가 흘러갑니다. 장면 제목을 길게 누르면 신호 표시를 켜고 끌 수 있습니다.
4. 선택지가 열리면 버튼을 누르거나 말로 대답합니다 (에뮬레이터는 Extended controls → Microphone에서 호스트 마이크를 켜야 함).

## 구조

```
story/   순수 Kotlin 스토리 엔진 (Android 없이 빌드·테스트)
  signal/  DrivingSnapshot(주행 상태) → EventDetector → DrivingEvent(출발·정차·회전·비·터널…)
  engine/  StoryEngine: 테마의 규칙 테이블대로 장면·낭독·선택지를 만든다
  themes/  ForestTheme (숲속 모험). 테마 추가 = 규칙과 리소스만 추가
  sim/     DemoCourse: 4분 30초 시연 주행 시나리오 (세 번 정차)
app/     Android Automotive 앱
  PleosSignalSource      Pleos Vehicle·NaviHelper + AAOS 차속 → DrivingSnapshot
  SimulatedSignalSource  데모 코스 재생
  MainActivity           화면·선택지·오늘의 모험 카드
  StoryStage             그림 층(배경, 주인공, 친구, 비·반딧불·바람). 주행 중엔 애니메이션 정지
  Narrator / VoiceBank   효과음 → 녹음 낭독 → Pleos TTS 순으로 줄마다 재생
  VoiceAnswer            Pleos STT로 선택지 음성 대답
  Soundtrack             배경음악·빗소리, 오디오 포커스
tools/
  export_assets.py  원본 그림(달리는 동화asset/)을 잘라 res/drawable-nodpi 로 내보낸다
  export_voice.py   달리는동화_voice.zip → res/raw/voice_*.mp3
  make_sounds.py    효과음 sfx_*·배경음악 bgm_* 합성 → res/raw
```

## 빌드

- JDK 21 (Gradle 데몬 고정), Android SDK는 `%LOCALAPPDATA%\Android\Sdk`
- 엔진 테스트: `gradlew :story:test`
- 디버그: `gradlew :app:assembleDebug`
- 릴리스: `gradlew clean :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  - 서명 정보는 `local.properties`에만 둔다 (커밋 안 됨): `release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`
  - 키 파일은 저장소 밖 `../running-tale-keys/release.jks`. **잃어버리면 같은 앱으로 업데이트할 수 없으니 따로 백업할 것**
- Pleos 앱 키(선택): `local.properties`의 `pleos.tts.clientId` / `pleos.tts.clientSecret`

### 주의

- **경로에 한글이 있으면 안 된다.** 테스트 JVM과 AGP가 비ASCII 경로에서 실패한다.
- Pleos TTS/STT 로케일은 반드시 `Locale.KOREA`(ko-KR). `Locale.KOREAN`(ko)이면 기기 안 엔진이 unsupported locale로 실패한다.
- 터널 판정은 TBT의 터널 입구 거리(30m 이내)로 들어가고, 안내가 사라진 뒤 최소 20초 후 나온다 (TBT에 터널 길이가 없음). 실제 경로 확인 필요.
