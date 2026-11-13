package com.roottap.clicker.core

/**
 * Built in parameter presets. They only carry click parameters: applying one never moves or
 * deletes the points the user placed on screen.
 */
object Templates {

    /** One line in plain language: what this preset is for, not just its numbers. */
    val builtIn: List<ClickTemplate> = listOf(
        ClickTemplate(
            id = -1L,
            name = "默认",
            note = "依次点：每 100ms 一轮，按 40ms，日常点按",
            profile = ClickProfile(),
        ),
        ClickTemplate(
            id = -2L,
            name = "极速 50ms",
            note = "抢速度：每 50ms 一下，按 20ms，适合抢购 / 连点",
            profile = ClickProfile(intervalMs = 50, pointGapMs = 15, pressMs = 20),
        ),
        ClickTemplate(
            id = -3L,
            name = "稳健 500ms",
            note = "慢而稳：每 0.5 秒一轮，按 60ms，页面加载慢也不漏",
            profile = ClickProfile(intervalMs = 500, pointGapMs = 120, pressMs = 60),
        ),
        ClickTemplate(
            id = -4L,
            name = "长按 800ms",
            note = "每个点按住 0.8 秒，适合要长按才生效的按钮",
            profile = ClickProfile(intervalMs = 300, pointGapMs = 100, pressMs = 800),
        ),
        ClickTemplate(
            id = -5L,
            name = "随机防检测",
            note = "节奏带随机：120ms 起、每次多等 0~180ms，坐标还抖 6px",
            profile = ClickProfile(intervalMs = 120, pointGapMs = 60, pressMs = 45, randomDelayMs = 180, jitterPx = 6),
        ),
        ClickTemplate(
            id = -6L,
            name = "同时多点",
            note = "所有点同一帧按下（多指），适合要一起按的地方",
            profile = ClickProfile(mode = ClickMode.SIMULTANEOUS, intervalMs = 800, pressMs = 60),
        ),
        ClickTemplate(
            id = -7L,
            name = "慢速单点",
            note = "1.5 秒点一次，用来挂机 / 签到",
            profile = ClickProfile(intervalMs = 1_500, pressMs = 80, repeatCount = 0),
        ),
    )

    /** Built in presets first, then whatever the user saved. */
    fun all(userTemplates: List<ClickTemplate>): List<ClickTemplate> = builtIn + userTemplates

    fun find(id: Long, userTemplates: List<ClickTemplate>): ClickTemplate? =
        all(userTemplates).firstOrNull { it.id == id }

    /** Name of the preset whose parameters match [profile], if any. */
    fun matching(profile: ClickProfile, userTemplates: List<ClickTemplate>): ClickTemplate? =
        all(userTemplates).firstOrNull { template ->
            val other = template.parameters()
            val mine = profile.copy(points = emptyList(), baseWidth = 0, baseHeight = 0)
            other.copy(color = ColorCondition()) == mine.copy(color = ColorCondition())
        }
}
