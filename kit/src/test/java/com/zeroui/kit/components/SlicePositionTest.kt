package com.zeroui.kit.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlicePositionTest {
    @Test
    fun singleRowRoundsBothEnds() {
        val position = SlicePosition.of(index = 0, count = 1)
        assertEquals(SlicePosition.Single, position)
        assertTrue(position.roundTop)
        assertTrue(position.roundBottom)
    }

    @Test
    fun groupRoundsOuterEndsOnly() {
        assertEquals(SlicePosition.First, SlicePosition.of(index = 0, count = 3))
        assertEquals(SlicePosition.Middle, SlicePosition.of(index = 1, count = 3))
        assertEquals(SlicePosition.Last, SlicePosition.of(index = 2, count = 3))
        assertFalse(SlicePosition.Middle.roundTop)
        assertFalse(SlicePosition.Middle.roundBottom)
    }
}
