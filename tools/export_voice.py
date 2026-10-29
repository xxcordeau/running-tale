# 달리는동화_voice.zip → app/src/main/res/raw/voice_*.mp3
# zip의 audio_N 번호는 ForestTheme 문장 순서. 문장↔파일 연결은 app/.../VoiceBank.kt.
# 회전 예고({distance}미터)는 거리가 매번 달라 TTS로 읽으므로 내보내지 않는다.
# 실행: python tools/export_voice.py 달리는동화_voice.zip app/src/main/res/raw
import os, re, sys, zipfile
NAMES = [
    'opening', 'depart', 'speed_slow', 'speed_cruise', 'speed_fast',
    'turn_ahead_right', 'turn_ahead_left', 'turned_right', 'turned_left',
    'stop_dragon', 'dragon_apple', 'dragon_song', 'stop_fox', 'fox_moon', 'fox_firefly',
    'stop_owl', 'owl_flower', 'owl_stream', 'resume', 'resume_no_answer',
    'rain_start', 'rain_stop', 'tunnel_in', 'tunnel_out', 'milestone_half', 'milestone_flag', 'ending',
]
# turn_ahead: {distance}가 들어가 TTS로 읽음. rain_start: 녹음은 '우산을 펼쳤어요'인데 그림이 비 맞는 수레로 바뀌어 TTS로 읽음.
# stop_owl: 녹음은 '부엉이 할아버지'인데 이름을 올빼미로 바꿔 TTS로 읽음
SKIP = {'turn_ahead_right', 'turn_ahead_left', 'rain_start', 'stop_owl'}
src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
with zipfile.ZipFile(src) as z:
    for info in z.infolist():
        m = re.match(r'audio_(\d+)_', info.filename)
        if not m:
            continue
        key = NAMES[int(m.group(1))]
        if key in SKIP:
            continue
        name = 'voice_' + key + '.mp3'
        with open(os.path.join(out, name), 'wb') as f:
            f.write(z.read(info))
        print(name, info.file_size)
