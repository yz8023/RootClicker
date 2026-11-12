package com.zeroui.kit.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

class PaletteStyleTest {
    @Test
    fun everyStyleMapsToTheSameNamedMiuixStyle() {
        for (style in PaletteStyle.entries) {
            assertEquals(style.name, style.toMiuix().name)
        }
    }

    @Test
    fun noMiuixStyleIsMissing() {
        assertEquals(ThemePaletteStyle.entries.map { it.name }, PaletteStyle.entries.map { it.name })
    }
}
