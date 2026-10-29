# 효과음·배경음악을 합성해서 app/src/main/res/raw 에 wav로 만든다 (저작권 걱정 없는 자체 제작음).
# 더 좋은 소리 파일이 생기면 같은 이름(sfx_*.wav / bgm_*.wav)으로 바꿔 넣으면 된다.
# 실행: python tools/make_sounds.py app/src/main/res/raw
import math
import os
import random
import struct
import sys
import wave

SR = 22050
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)


# ---------- 기본 재료 ----------

def silence(sec):
    return [0.0] * int(sec * SR)


def mix(base, other, at=0.0, gain=1.0):
    """base에 other를 at초 위치부터 더한다 (base가 짧으면 늘린다)."""
    start = int(at * SR)
    need = start + len(other)
    if need > len(base):
        base.extend([0.0] * (need - len(base)))
    for i, v in enumerate(other):
        base[start + i] += v * gain
    return base


def env(n, attack=0.005, decay=None, release=0.05):
    """attack → (decay가 있으면 지수 감쇠) → 끝 release."""
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    out = []
    for i in range(n):
        g = min(1.0, i / a)
        if decay:
            g *= math.exp(-(i / SR) / decay)
        if i > n - r:
            g *= (n - i) / r
        out.append(g)
    return out


def bell(freq, sec, decay=0.35, bright=1.0):
    """오르골·종 같은 소리: 기음 + 비정수배 배음, 지수 감쇠."""
    n = int(sec * SR)
    e = env(n, attack=0.002, decay=decay)
    parts = [(1.0, 1.0), (2.0, 0.35 * bright), (3.01, 0.12 * bright), (4.2, 0.06 * bright)]
    return [e[i] * sum(a * math.sin(2 * math.pi * freq * m * i / SR) for m, a in parts) * 0.5 for i in range(n)]


def tone(freq, sec, shape='sine', attack=0.01, release=0.08, decay=None):
    n = int(sec * SR)
    e = env(n, attack, decay, release)
    out = []
    for i in range(n):
        ph = (freq * i / SR) % 1.0
        if shape == 'sine':
            v = math.sin(2 * math.pi * ph)
        elif shape == 'tri':
            v = 4 * abs(ph - 0.5) - 1
        else:  # 부드러운 사각파 (금관 느낌)
            v = sum(math.sin(2 * math.pi * freq * k * i / SR) / k for k in (1, 3, 5, 7)) * 0.8
        out.append(v * e[i])
    return out


def sweep(f0, f1, sec, vibrato=0.0, shape='sine', attack=0.02, release=0.08):
    """주파수가 f0→f1로 미끄러지는 소리 (끼익, 휘이잉)."""
    n = int(sec * SR)
    e = env(n, attack, None, release)
    out, ph = [], 0.0
    for i in range(n):
        t = i / n
        f = f0 * (f1 / f0) ** t * (1 + vibrato * math.sin(2 * math.pi * 7 * i / SR))
        ph += f / SR
        v = math.sin(2 * math.pi * ph)
        if shape == 'squeak':
            v = math.sin(2 * math.pi * ph + 0.8 * math.sin(4 * math.pi * ph))
        out.append(v * e[i])
    return out


def noise(sec, cutoff=0.2, attack=0.01, release=0.1, decay=None, seed=1):
    """한 극 저역통과 필터를 거친 잡음. cutoff 0~1 (작을수록 둔탁)."""
    rnd = random.Random(seed)
    n = int(sec * SR)
    e = env(n, attack, decay, release)
    out, y = [], 0.0
    for i in range(n):
        y += cutoff * (rnd.uniform(-1, 1) - y)
        out.append(y * e[i] * 2.0)
    return out


def thump(sec=0.25, freq=90, gain=1.0):
    """나무 바퀴 덜컹·쿵: 떨어지는 저음 + 둔탁한 잡음."""
    body = sweep(freq * 1.8, freq, sec, attack=0.002, release=0.05)
    body = [v * e for v, e in zip(body, env(len(body), 0.002, sec / 4, 0.05))]
    mix(body, noise(sec * 0.5, cutoff=0.08, attack=0.001, decay=0.04, seed=freq), gain=0.6)
    return [v * gain for v in body]


def click(freq=1200, sec=0.06, seed=3):
    """발굽·나무토막 딱."""
    return mix(tone(freq, sec, attack=0.001, release=0.01, decay=0.015),
               noise(sec, cutoff=0.6, attack=0.001, decay=0.01, seed=seed), gain=0.4)


def echo(x, delay=0.18, feedback=0.4, wet=0.35, tail=1.0):
    d = int(delay * SR)
    out = x + [0.0] * int(tail * SR)
    buf = out[:]
    for i in range(d, len(buf)):
        buf[i] += buf[i - d] * feedback
    return [o * (1 - wet) + b * wet for o, b in zip(out, buf)]


def save(name, x, peak=0.85):
    m = max(1e-6, max(abs(v) for v in x))
    g = peak / m
    path = os.path.join(OUT, name + '.wav')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, v * g)) * 32767)) for v in x))
    print(name, f'{len(x) / SR:.1f}s', os.path.getsize(path) // 1024, 'KB')


def note(name):
    """'C5' → 주파수."""
    names = {'C': -9, 'D': -7, 'E': -5, 'F': -4, 'G': -2, 'A': 0, 'B': 2}
    semis = names[name[0]] + (1 if '#' in name else 0) + 12 * (int(name[-1]) - 4)
    return 440 * 2 ** (semis / 12)


# ---------- 효과음 ----------

def sfx():
    # 덜컹덜컹, 끼익~ 덜컹! (출발·재출발)
    x = silence(1.7)
    mix(x, thump(0.25, 80), 0.0)
    mix(x, thump(0.25, 95), 0.22)
    mix(x, sweep(900, 1300, 0.45, vibrato=0.03, shape='squeak'), 0.55, 0.25)
    mix(x, thump(0.35, 70, 1.2), 1.1)
    save('sfx_depart', x)

    # 터벅터벅, 사박사박 (느리게)
    x = silence(1.8)
    for i in range(4):
        mix(x, thump(0.12, 120, 0.5), i * 0.42)
        mix(x, noise(0.15, cutoff=0.35, attack=0.01, decay=0.05, seed=10 + i), i * 0.42 + 0.05, 0.35)
    save('sfx_slow', x)

    # 다그닥 다그닥 (보통 속도)
    x = silence(1.6)
    for bar in range(2):
        for j, t in enumerate((0.0, 0.12, 0.24)):
            mix(x, click(900 + 150 * j, seed=bar * 3 + j), bar * 0.7 + t)
    save('sfx_cruise', x)

    # 휘이잉~ 쌩쌩! (빠르게)
    x = noise(1.4, cutoff=0.12, attack=0.3, release=0.4, seed=7)
    mix(x, [v * 0.25 for v in sweep(500, 1400, 1.4, vibrato=0.02, attack=0.3, release=0.4)])
    mix(x, noise(0.25, cutoff=0.5, attack=0.01, decay=0.08, seed=8), 1.05, 0.6)
    mix(x, noise(0.25, cutoff=0.5, attack=0.01, decay=0.08, seed=9), 1.3, 0.6)
    save('sfx_fast', x)

    # 띠링! (회전 예고)
    x = bell(note('E6'), 0.9, decay=0.25)
    mix(x, bell(note('B6'), 0.9, decay=0.3), 0.11)
    save('sfx_chime', x)

    # 부스럭부스럭 (숲길 회전) / 사각사각 (오솔길 회전)
    x = silence(1.2)
    for i in range(6):
        mix(x, noise(0.14, cutoff=0.25, attack=0.02, decay=0.06, seed=20 + i), i * 0.17 + random.Random(i).uniform(0, 0.04), 0.8)
    save('sfx_rustle', x)
    x = silence(1.0)
    for i in range(4):
        mix(x, noise(0.08, cutoff=0.75, attack=0.003, decay=0.025, seed=30 + i), i * 0.2)
        mix(x, noise(0.06, cutoff=0.75, attack=0.003, decay=0.02, seed=40 + i), i * 0.2 + 0.07, 0.7)
    save('sfx_crunch', x)

    # 끼이익… 멈춤! (정차)
    x = sweep(1500, 700, 0.9, vibrato=0.04, shape='squeak', attack=0.03, release=0.2)
    x = [v * 0.5 for v in x]
    mix(x, thump(0.3, 75, 1.2), 0.95)
    save('sfx_stop', x)

    # 뾰로롱~ (선택지 열림)
    x = silence(1.3)
    for i, n in enumerate(('C6', 'E6', 'G6', 'C7', 'E7')):
        mix(x, bell(note(n), 0.8, decay=0.25), i * 0.07, 0.8)
    save('sfx_sparkle', x)

    # 딩동댕! (정답·선택 완료)
    x = silence(1.6)
    for i, n in enumerate(('E5', 'C5', 'G5')):
        mix(x, bell(note(n), 1.0 if i < 2 else 1.4, decay=0.35 if i < 2 else 0.6), i * 0.28)
    save('sfx_dingdong', x)

    # 후두둑 후두둑, 토독토독 (비 시작)
    rnd = random.Random(5)
    x = silence(2.2)
    for k in range(45):
        t = rnd.uniform(0, 2.0)
        mix(x, tone(rnd.uniform(1800, 3600), 0.05, attack=0.001, release=0.01, decay=0.01), t, rnd.uniform(0.3, 0.9))
    mix(x, noise(2.2, cutoff=0.3, attack=0.4, release=0.6, seed=11), gain=0.15)
    save('sfx_rain_start', x)

    # 반짝~ (비 그침, 무지개)
    x = silence(1.6)
    for i, n in enumerate(('G6', 'C7', 'E7', 'G7')):
        mix(x, bell(note(n), 1.0, decay=0.4, bright=0.6), i * 0.12, 0.6)
    save('sfx_twinkle', echo(x, 0.15, 0.35, 0.3, 0.5))

    # 우우웅… (터널 진입, 동굴 울림)
    x = tone(note('A2'), 2.2, attack=0.5, release=0.8)
    mix(x, [v * 0.5 for v in tone(note('E3') * 1.003, 2.2, attack=0.6, release=0.8)])
    mix(x, noise(2.2, cutoff=0.05, attack=0.5, release=0.8, seed=12), gain=0.4)
    save('sfx_cave', echo(x, 0.25, 0.45, 0.4, 1.0))

    # 짠! (터널 통과)
    x = silence(1.2)
    for n in ('C5', 'E5', 'G5', 'C6'):
        mix(x, tone(note(n), 0.9, 'square', attack=0.005, release=0.3, decay=0.5), 0, 0.3)
    mix(x, bell(note('C7'), 1.0, decay=0.3), 0.02, 0.5)
    save('sfx_tada', x)

    # 따라란~ (진행률 알림)
    x = silence(1.5)
    for i, n in enumerate(('G5', 'C6', 'E6')):
        mix(x, bell(note(n), 1.1 if i == 2 else 0.6, decay=0.5 if i == 2 else 0.25), i * 0.13)
    save('sfx_arpeggio', x)

    # 빠밤! 빠바밤! (도착 팡파레)
    x = silence(2.6)
    seq = [(0.0, 0.18, ('G4', 'B4', 'D5')), (0.22, 0.45, ('C5', 'E5', 'G5')),
           (0.85, 0.14, ('G4', 'B4', 'D5')), (1.02, 0.14, ('A4', 'C5', 'F5')), (1.2, 1.2, ('C5', 'E5', 'G5', 'C6'))]
    for t, d, chord in seq:
        for n in chord:
            mix(x, tone(note(n), d, 'square', attack=0.01, release=0.08), t, 0.25)
    mix(x, bell(note('C7'), 1.2, decay=0.5), 1.2, 0.4)
    save('sfx_fanfare', echo(x, 0.2, 0.25, 0.2, 0.6))

    # 끼이익~ 쿵! (성문)
    x = [v * 0.5 for v in sweep(220, 160, 1.4, vibrato=0.06, shape='squeak', attack=0.1, release=0.2)]
    mix(x, thump(0.8, 50, 1.6), 1.4)
    save('sfx_gate', echo(x, 0.3, 0.3, 0.3, 0.8))

    # 비 오는 동안 깔리는 빗소리 (반복 재생)
    rnd = random.Random(21)
    x = noise(8.0, cutoff=0.35, attack=0.0, release=0.0, seed=22)
    x = [v * 0.25 for v in x]
    for k in range(260):
        mix(x, tone(rnd.uniform(1800, 4200), 0.04, attack=0.001, release=0.01, decay=0.008), rnd.uniform(0, 7.95), rnd.uniform(0.1, 0.5))
    save('sfx_rain_loop', x[:int(8.0 * SR)], peak=0.6)


# ---------- 배경음악 (반복 재생용) ----------

CHORDS = {'C': ('C', 'E', 'G'), 'G': ('G', 'B', 'D'), 'Am': ('A', 'C', 'E'), 'F': ('F', 'A', 'C'),
          'Dm': ('D', 'F', 'A'), 'Em': ('E', 'G', 'B')}


def bgm(name, progression, bpm, octave, seed, density, pad_gain=0.18, bell_decay=0.5, reverb=0.3):
    """오르골 멜로디 + 부드러운 화음 깔개. 진행을 두 번 돌고 정확히 끊어서 이음매 없이 반복된다."""
    rnd = random.Random(seed)
    beat = 60 / bpm
    bars = progression * 2
    total = len(bars) * 4 * beat
    x = silence(total + 1.5)
    for b, chord in enumerate(bars):
        tones = CHORDS[chord]
        t0 = b * 4 * beat
        # 화음 깔개 (부드러운 삼각파)
        for k, n in enumerate(tones):
            mix(x, tone(note(f'{n}{octave - 1}'), 4 * beat, 'tri', attack=0.4, release=0.6), t0, pad_gain / (1 + k * 0.3))
        mix(x, tone(note(f'{tones[0]}{octave - 2}'), 4 * beat, 'sine', attack=0.05, release=0.3, decay=1.2), t0, 0.35)
        # 멜로디: 화음음 위주, 8분음표 단위로 듬성듬성
        for step in range(8):
            if rnd.random() > density:
                continue
            n = rnd.choice(tones) + str(octave + (1 if rnd.random() < 0.25 else 0))
            mix(x, bell(note(n), 1.4, decay=bell_decay), t0 + step * beat / 2, 0.55)
    x = echo(x, delay=beat * 0.75, feedback=0.3, wet=reverb, tail=0)
    # 끝에서 넘친 꼬리를 앞으로 접어 넣어 반복 이음매를 매끈하게
    n = int(total * SR)
    head, tail = x[:n], x[n:]
    for i, v in enumerate(tail[:n]):
        head[i] += v
    save(name, head, peak=0.7)


def music():
    bgm('bgm_day', ['C', 'G', 'Am', 'F'], bpm=100, octave=5, seed=1, density=0.6)
    bgm('bgm_forest', ['Am', 'F', 'C', 'G'], bpm=84, octave=5, seed=2, density=0.45)
    bgm('bgm_cave', ['Am', 'Em', 'F', 'Em'], bpm=66, octave=5, seed=3, density=0.25, pad_gain=0.22, bell_decay=0.9, reverb=0.5)
    bgm('bgm_ending', ['C', 'F', 'G', 'C'], bpm=108, octave=5, seed=4, density=0.75)


if __name__ == '__main__':
    sfx()
    music()
