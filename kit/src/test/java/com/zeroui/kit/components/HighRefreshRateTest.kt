package com.zeroui.kit.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HighRefreshRateTest {
    private val modes = listOf(RefreshMode(1, 60f), RefreshMode(2, 90f), RefreshMode(3, 120f))

    @Test
    fun maxSelectsTheHighestRate() {
        assertEquals(3, HighRefreshRate.pickModeId(modes, HighRefreshRate.MAX))
    }

    @Test
    fun fixedRateSelectsTheClosestMode() {
        assertEquals(2, HighRefreshRate.pickModeId(modes, 85))
        assertEquals(1, HighRefreshRate.pickModeId(modes, 30))
    }

    @Test
    fun systemLeavesTheChoiceToTheSystem() {
        assertEquals(0, HighRefreshRate.pickModeId(modes, HighRefreshRate.SYSTEM))
    }

    @Test
    fun fixedRateDisplaysAreNotTouched() {
        assertEquals(0, HighRefreshRate.pickModeId(listOf(RefreshMode(1, 60f)), HighRefreshRate.MAX))
    }
}
