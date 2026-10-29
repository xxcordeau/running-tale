package com.runningtale.app

/**
 * 이야기 엔진의 그림 키 → 실제 그림. 그림은 tools/export_assets.py가 `달리는 동화asset`에서 잘라 만든다.
 * 없는 키는 null이라 그림 없이 낭독만 나온다.
 */
object Art {
    fun background(key: String): Int? = when (key) {
        "meadow" -> R.drawable.bg_meadow
        "road" -> R.drawable.bg_road
        "forest" -> R.drawable.bg_forest
        "cave" -> R.drawable.bg_cave
        "castle" -> R.drawable.bg_castle
        "dragon" -> R.drawable.bg_dragon
        "fox" -> R.drawable.bg_fox
        "owl" -> R.drawable.bg_owl
        else -> null
    }

    /** 오늘의 모험 카드에 쓰는 장면 아이콘. */
    fun icon(key: String): Int? = when (key) {
        "meadow" -> R.drawable.icon_meadow
        "road" -> R.drawable.icon_road
        "forest" -> R.drawable.icon_forest
        "cave" -> R.drawable.icon_cave
        "castle" -> R.drawable.icon_castle
        "dragon" -> R.drawable.icon_dragon
        "fox" -> R.drawable.icon_fox
        "owl" -> R.drawable.icon_owl
        else -> null
    }

    /** 오른쪽 친구 자리에 서는 그림 (장면 친구 + 잠깐 등장하는 그림). */
    fun sprite(key: String): Int? = when (key) {
        "dragon" -> R.drawable.npc_dragon
        "fox" -> R.drawable.npc_fox
        "owl" -> R.drawable.npc_owl
        "dragon_apple" -> R.drawable.cameo_dragon_apple
        "dragon_sleep" -> R.drawable.cameo_dragon_sleep
        "fox_gift" -> R.drawable.cameo_fox_gift
        "fox_fireflies" -> R.drawable.cameo_fox_fireflies
        "owl_butterflies" -> R.drawable.cameo_owl_butterflies
        "owl_stream" -> R.drawable.cameo_owl_stream
        "squirrels" -> R.drawable.cameo_squirrels
        "sign_left" -> R.drawable.fx_sign_left
        "sign_right" -> R.drawable.fx_sign_right
        "rainbow" -> R.drawable.fx_rainbow
        else -> null
    }

    /**
     * 주인공 그림이 함께 나오는 결과 그림. 이때는 왼쪽 주인공을 숨겨 보리가 둘로 보이지 않게 한다.
     */
    fun includesHero(key: String?): Boolean =
        key in setOf("fox_gift", "fox_fireflies", "owl_butterflies", "owl_stream")
}
