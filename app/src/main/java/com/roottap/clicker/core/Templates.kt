package com.roottap.clicker.core

/**
 * Built in parameter presets. They only carry click parameters: applying one never moves or
 * deletes the points the user placed on screen.
 */
object Templates {

    val builtIn: List<ClickTemplate> = listOf(
        ClickTemplate(
            id = -1L,
            name = "默认",
            profile = ClickProfile(),
        ),
        ClickTemplate(
            id = -2L,
            name = "极速 50ms",
            profile = ClickProfile(intervalMs = 50, pointGapMs = 15, pressMs = 20),
        ),
        ClickTemplate(
            id = -3L,
            name = "稳健 500ms",
            profile = ClickProfile(intervalMs = 500, pointGapMs = 120, pressMs = 60),
        ),
        ClickTemplate(
            id = -4L,
            name = "长按 800ms",
            profile = ClickProfile(intervalMs = 300, pointGapMs = 100, pressMs = 800),
        ),
        ClickTemplate(
            id = -5L,
            name = "随机防检测",
            profile = ClickProfile(intervalMs = 120, pointGapMs = 60, pressMs = 45, randomDelayMs = 180, jitterPx = 6),
        ),
        ClickTemplate(
            id = -6L,
            name = "同时多点",
            profile = ClickProfile(mode = ClickMode.SIMULTANEOUS, intervalMs = 800, pressMs = 60),
        ),
        ClickTemplate(
            id = -7L,
            name = "慢速单点",
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
