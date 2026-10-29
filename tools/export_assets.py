# 달리는 동화asset → app/src/main/res/drawable-nodpi (webp). 원본은 건드리지 않는다.
# 실행: python tools/export_assets.py "달리는 동화asset" app/src/main/res/drawable-nodpi
import os, sys
from PIL import Image
SRC = sys.argv[1]; OUT = sys.argv[2]; os.makedirs(OUT, exist_ok=True)
for old in os.listdir(OUT): os.remove(os.path.join(OUT, old))  # 매번 새로 만든다
def load(name): return Image.open(os.path.join(SRC, name)).convert('RGBA')
def trim(im, th=12):
    box = im.getchannel('A').point(lambda v: 255 if v > th else 0).getbbox()
    return im.crop(box) if box else im
def save(im, key, maxdim=900, opaque=False):
    im = im.copy(); im.thumbnail((maxdim, maxdim), Image.LANCZOS)
    path = os.path.join(OUT, key + '.webp')
    (im.convert('RGB') if opaque else im).save(path, 'WEBP', quality=86, method=6)
    print(key, im.size, os.path.getsize(path)//1024, 'KB')

# 배경 (불투명)
for key, f in {
    'bg_meadow': '햇살 가득한 풍차 마을 풍경.png',
    'bg_road': '토끼 표지판과 성으로 향하는 길.png',
    'bg_forest': '마법의 숲길과 작은 다리.png',
    'bg_cave': '반딧불이 빛나는 마법의 지하 동굴 마을.png',
    'bg_castle': '별빛 아래 토끼 깃발의 마법 성채.png',
    'bg_dragon': '용 바위 절벽길의 몽환적 풍경.png',
    'bg_fox': '따뜻한 여우의 아늑한 동굴 아지트.png',
    'bg_owl': '별빛 아래 아늑한 나무마을.png',
}.items():
    save(load(f), key, maxdim=1920, opaque=True)

# 한 장짜리 캐릭터·UI
for key, f, m in [
    ('hero_carriage', '분홍 후드 꼬마의 모험 마차.png', 900),
    ('cameo_fox_gift', '여우가 건네는 보석 선물_超碰.png', 800),
    ('cameo_fox_fireflies', '반짝이는 반딧불이와 숲속 친구들.png', 800),
    ('cameo_owl_butterflies', '다정한 숲속 친구들과 나비들.png', 800),
    ('cameo_owl_stream', '마법의 개울에서 만난 친구들.png', 800),
    ('npc_dragon', '귀여운 초록 아기 용 스티커.png', 800),
    ('npc_fox', '귀여운 숲속 여우 모험가.png', 800),
    ('npc_owl', '초승달 모자 올빼미 마법사.png', 800),
    ('cameo_squirrels', '도토리 여행을 떠나는 다람쥐 가족.png', 800),
    ('cameo_dragon_apple', '사과를 먹는 아기 드래곤.png', 800),
    ('cameo_dragon_sleep', '음악을 들으며 낮잠 자는 아기 용.png', 800),
    ('ui_logo', '달리는 동화와 숲속 친구들.png', 1100),
    ('ui_btn_start', '모험 시작 나무 간판 버튼.png', 700),
    ('ui_btn_demo', '귀여운 숲속 데모 코스 버튼.png', 700),
]:
    save(trim(load(f)), key, m)

# 시트에서 잘라 쓰는 것 (좌표는 원본 픽셀)
def piece(f, box, key, m=600):
    save(trim(load(f).crop(box)), key, m)
W = '파스텔 날씨와 방향표지 스티커 시트.png'
piece(W, (20, 36, 752, 330), 'fx_rainbow', 900)
# 비 오는 날 수레: 그림에 그려진 빗줄기까지 넣으면 수레가 작아 보여서 수레 둘레만 자른다
piece('비 내리는 멧돼지 마차 여행.png', (200, 40, 1500, 845), 'hero_rain', 900)
piece(W, (20, 608, 330, 1000), 'fx_sign_left', 500)
piece(W, (1225, 665, 1510, 1000), 'fx_sign_right', 500)
piece(W, (650, 505, 900, 680), 'fx_wind', 400)
R = '비 오는 날 토끼 우산 PNG 에셋 시트.png'
save(load(R).crop((0, 0, 1672, 330)), 'fx_rain', 1672)
F = '반짝이는 반딧불이 스티커 세트.png'
for i, b in enumerate([(112, 20, 524, 344), (996, 20, 1368, 264), (612, 408, 1004, 684)]):
    piece(F, b, f'fx_firefly{i}', 300)
B = '귀여운 숲속 선택 버튼 UI.png'
piece(B, (28, 0, 1750, 440), 'ui_choice_star', 900)
piece(B, (28, 440, 1750, 870), 'ui_choice_heart', 900)
I = '동화 속 알록달록 지도 아이콘 모음.png'
for key, b in zip(['icon_meadow', 'icon_road', 'icon_forest', 'icon_dragon', 'icon_fox', 'icon_owl', 'icon_cave', 'icon_castle'],
                  [(16, 32, 428, 420), (436, 72, 876, 416), (884, 48, 1324, 420), (1332, 36, 1760, 420),
                   (12, 444, 448, 828), (452, 444, 884, 832), (892, 436, 1340, 832), (1344, 428, 1764, 832)]):
    piece(I, b, key, 260)

# 앱 아이콘 (런처는 mipmap)
icon = trim(load('별빛 아래 모험하는 친구들.png')); icon.thumbnail((192, 192), Image.LANCZOS)
os.makedirs(os.path.join(OUT, '..', 'mipmap-nodpi'), exist_ok=True)
icon.save(os.path.join(OUT, '..', 'mipmap-nodpi', 'ic_launcher.webp'), 'WEBP', quality=90)
