package com.runningtale.story.themes

import com.runningtale.story.engine.Backdrop
import com.runningtale.story.engine.Choice
import com.runningtale.story.engine.ChoiceOption
import com.runningtale.story.engine.EventKey
import com.runningtale.story.engine.Scene
import com.runningtale.story.engine.StoryBeat
import com.runningtale.story.engine.StoryTheme

/**
 * 숲속 모험의 효과음. 낭독 줄([StoryBeat.before]/[StoryBeat.after])에 글자로 들어가고,
 * 앱이 같은 글자를 소리 파일로 바꿔 튼다. 소리 파일이 없으면 TTS가 글자를 읽는다.
 */
object ForestSounds {
    const val DEPART = "덜컹덜컹, 끼익~ 덜컹!"
    const val SLOW = "터벅터벅, 사박사박"
    const val CRUISE = "다그닥 다그닥"
    const val FAST = "휘이잉~ 쌩쌩!"
    const val TURN_AHEAD = "띠링!"
    const val TURN_FOREST = "부스럭부스럭"
    const val TURN_PATH = "사각사각"
    const val STOP = "끼이익… 멈춤!"
    const val CHOICE = "뾰로롱~"
    const val CORRECT = "딩동댕!"
    const val RAIN_START = "후두둑 후두둑, 토독토독"
    const val RAIN_STOP = "반짝~"
    const val TUNNEL_IN = "우우웅…"
    const val TUNNEL_OUT = "짠!"
    const val MILESTONE = "따라란~"
    const val FANFARE = "빠밤! 빠바밤!"
    const val GATE = "끼이익~ 쿵!"
}

/** 숲속 모험: 돼지 후드를 쓴 꼬마 보리가 멧돼지 수레를 타고 별빛 성으로 가는 이야기. */
val ForestTheme = run {
    val stopSound = ForestSounds.STOP
    val choiceSound = ForestSounds.CHOICE
    val correctSound = ForestSounds.CORRECT

    val meadow = Scene("마을 들판", "🏡", Backdrop.MEADOW, art = "meadow")
    val road = Scene("꽃길", "🐗", Backdrop.ROAD, art = "road")
    val forest = Scene("깊은 숲", "🌲", Backdrop.FOREST, art = "forest")
    val cave = Scene("반딧불 동굴", "✨", Backdrop.CAVE, art = "cave")
    val castle = Scene("별빛 성", "🏰", Backdrop.CASTLE, art = "castle")

    StoryTheme(
        id = "forest",
        title = "숲속 모험",
        opening = StoryBeat(
            "옛날 옛적, 돼지 후드를 쓴 꼬마 보리가 멧돼지 수레를 타고 별빛 성으로 떠날 준비를 했어요. 수레가 움직이면 모험이 시작돼요!",
            scene = meadow,
        ),
        rules = mapOf(
            EventKey.DEPART to listOf(
                StoryBeat(
                    "덜컹! 수레가 출발했어요. 보리는 창밖을 보며 손을 흔들었어요.",
                    scene = road,
                    before = listOf(ForestSounds.DEPART),
                    after = listOf("가자, 멧돼지야!", "꿀꿀!"),
                ),
            ),
            EventKey.SPEED_SLOW to listOf(
                StoryBeat(
                    "멧돼지가 천천히 걸으며 들꽃 냄새를 킁킁 맡아요.",
                    before = listOf(ForestSounds.SLOW),
                    after = listOf("푸흐흐…"),
                ),
            ),
            EventKey.SPEED_CRUISE to listOf(
                StoryBeat(
                    "타닥타닥, 멧돼지가 신나게 달리기 시작했어요!",
                    before = listOf(ForestSounds.CRUISE),
                    after = listOf("꾸울꾸울~ 꿀!"),
                ),
            ),
            EventKey.SPEED_FAST to listOf(
                StoryBeat(
                    "휘이잉! 바람을 가르며 수레가 쌩쌩 달려요. 보리의 후드가 펄럭펄럭!",
                    before = listOf(ForestSounds.FAST),
                    after = listOf("꿀꿀꿀꿀!", "우와아~!"),
                ),
            ),
            EventKey.TURN_AHEAD_RIGHT to listOf(
                StoryBeat("저기 봐! {distance}미터 앞에 오른쪽 숲길이 보여요.", cameo = "sign_right", before = listOf(ForestSounds.TURN_AHEAD)),
            ),
            EventKey.TURN_AHEAD_LEFT to listOf(
                StoryBeat("저기 봐! {distance}미터 앞에 왼쪽 오솔길이 보여요.", cameo = "sign_left", before = listOf(ForestSounds.TURN_AHEAD)),
            ),
            EventKey.TURNED_RIGHT to listOf(
                StoryBeat(
                    "수레가 오른쪽 숲길로 쏙 들어갔어요. 키 큰 나무들이 인사해요.",
                    scene = forest,
                    before = listOf(ForestSounds.TURN_FOREST),
                    after = listOf("히히히!"),
                ),
            ),
            EventKey.TURNED_LEFT to listOf(
                StoryBeat(
                    "왼쪽 오솔길로 접어들자 다람쥐 가족이 졸졸 따라와요.",
                    scene = forest,
                    cameo = "squirrels",
                    before = listOf(ForestSounds.TURN_PATH),
                    after = listOf("찍찍! 찍찍찍!", "우리도 같이 가요!"),
                ),
            ),
            EventKey.STOP to listOf(
                StoryBeat(
                    "앗, 커다란 용이 길을 막고 앉아 있어요! 용이 배가 고프대요. 보리는 어떻게 할까?",
                    scene = Scene("용의 고개", "🐉", Backdrop.FOREST, art = "dragon", npc = "dragon"),
                    before = listOf(stopSound, "어엇?!", "크르르르… 배고파아…"),
                    after = listOf("음… 어떡하지?", choiceSound),
                    choice = Choice(
                        listOf(
                            ChoiceOption(
                                "사과를 준다",
                                "용이 사과를 와삭와삭 먹고 꼬리를 살랑살랑 흔들었어요.",
                                cameo = "dragon_apple",
                                keywords = listOf("사과", "먹"),
                                before = listOf(correctSound),
                                after = listOf("와삭! 냠냠…", "크하하하! 고마워, 꼬마야!"),
                            ),
                            ChoiceOption(
                                "노래를 불러준다",
                                "보리의 노래에 용이 꾸벅꾸벅 졸기 시작했어요.",
                                cameo = "dragon_sleep",
                                keywords = listOf("노래", "불러", "음악"),
                                before = listOf(correctSound),
                                after = listOf("드르렁… 쿠울…"),
                            ),
                        ),
                        prompt = "사과를 줄까? 아니면 노래를 불러줄까?",
                    ),
                ),
                StoryBeat(
                    "여우가 수수께끼를 내요. \"밤에 길을 밝혀주는 건 뭘까?\"",
                    scene = Scene("여우 굴", "🦊", Backdrop.FOREST, art = "fox", npc = "fox"),
                    before = listOf(stopSound, "호호, 꼬마 손님이네?"),
                    after = listOf("자~ 맞혀볼래?", choiceSound),
                    choice = Choice(
                        listOf(
                            ChoiceOption(
                                "달님",
                                "정답! 여우가 반짝이는 보석을 선물했어요.",
                                cameo = "fox_gift",
                                keywords = listOf("달님", "달이", "달"),
                                before = listOf(correctSound),
                                after = listOf("호호호! 똑똑한걸?"),
                            ),
                            ChoiceOption(
                                "반딧불이",
                                "그것도 정답! 여우가 반딧불이 친구를 불러줬어요.",
                                cameo = "fox_fireflies",
                                keywords = listOf("반딧불", "반디", "개똥벌레"),
                                before = listOf(correctSound),
                                after = listOf("호호호! 똑똑한걸?", "반짝반짝~ 이쪽이야!"),
                            ),
                        ),
                        prompt = "달님일까? 아니면 반딧불이일까?",
                    ),
                ),
                StoryBeat(
                    "올빼미 할아버지가 지도를 펼쳤어요. 어느 길로 갈까?",
                    scene = Scene("올빼미 나무", "🦉", Backdrop.FOREST, art = "owl", npc = "owl"),
                    before = listOf(stopSound, "부엉~ 부엉~ 어디 가니?"),
                    after = listOf("흠흠, 어디 보자…", choiceSound),
                    choice = Choice(
                        listOf(
                            ChoiceOption(
                                "꽃길",
                                "꽃길에는 나비들이 춤을 추고 있었어요.",
                                cameo = "owl_butterflies",
                                keywords = listOf("꽃"),
                                before = listOf(correctSound),
                                after = listOf("허허허, 좋은 선택이구나.", "팔랑팔랑~"),
                            ),
                            ChoiceOption(
                                "개울길",
                                "개울에서 물고기들이 퐁당퐁당 인사했어요.",
                                cameo = "owl_stream",
                                keywords = listOf("개울", "냇물", "시냇", "물"),
                                before = listOf(correctSound),
                                after = listOf("허허허, 좋은 선택이구나.", "뽀글뽀글~ 퐁당!"),
                            ),
                        ),
                        prompt = "꽃길로 갈까? 아니면 개울길로 갈까?",
                    ),
                ),
            ),
            EventKey.RESUME to listOf(
                StoryBeat(
                    "다시 출발! 친구가 손을 흔들며 배웅해 줬어요.",
                    scene = road,
                    before = listOf(ForestSounds.DEPART),
                    after = listOf("꿀꿀!"),
                ),
            ),
            EventKey.RAIN_START to listOf(
                StoryBeat(
                    "후두둑, 숲에 비가 내리기 시작했어요. 보리도 멧돼지도 비에 촉촉이 젖었어요!",
                    before = listOf(ForestSounds.RAIN_START),
                    after = listOf("꿀꿀꿀꿀!"),
                    clearCameo = true,
                ),
            ),
            EventKey.RAIN_STOP to listOf(
                StoryBeat(
                    "비가 그치고 하늘에 무지개가 떴어요!",
                    cameo = "rainbow",
                    before = listOf(ForestSounds.RAIN_STOP),
                    after = listOf("우와아~!"),
                ),
            ),
            EventKey.TUNNEL_IN to listOf(
                StoryBeat(
                    "수레가 동굴로 들어갔어요. 반딧불이들이 반짝반짝 길을 비춰줘요.",
                    scene = cave,
                    before = listOf(ForestSounds.TUNNEL_IN),
                    after = listOf("반짝반짝~ 이쪽이야!", "후후후~"),
                ),
            ),
            EventKey.TUNNEL_OUT to listOf(
                StoryBeat("짠! 동굴을 빠져나오니 햇살이 눈부셔요.", scene = road, before = listOf(ForestSounds.TUNNEL_OUT)),
            ),
        ),
        resumeWithoutAnswer = StoryBeat("친구가 \"다음에 또 만나!\" 하고 길을 비켜줬어요.", scene = road),
        milestones = mapOf(
            0.5f to "벌써 반이나 왔어요! 별빛 성까지 조금만 더!",
            0.9f to "저 멀리 별빛 성의 깃발이 보여요!",
        ),
        milestoneSound = listOf(ForestSounds.MILESTONE),
        ending = StoryBeat(
            "드디어 별빛 성에 도착했어요! 오늘 지나온 길을 모험 카드에 그려 넣었어요. 다음 모험에서 또 만나요!",
            scene = castle,
            before = listOf(ForestSounds.FANFARE, ForestSounds.GATE),
            after = listOf("히히히!", "꿀꿀!"),
        ),
    )
}
