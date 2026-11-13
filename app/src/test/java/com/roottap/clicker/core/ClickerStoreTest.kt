package com.roottap.clicker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClickerStoreTest {

    @Before
    fun setUp() {
        ClickerStore.resetForTest()
    }

    @Test
    fun undoRemovesTheLastAddedPpoint() {
        ClickerStore.addPoint(ClickPoint(x = 10, y = 20))
        ClickerStore.addPoint(ClickPoint(x = 30, y = 40))
        assertEquals(2, ClickerStore.data.value.profile.points.size)
        assertEquals(2, ClickerStore.undoCount.value)

        ClickerStore.undo()

        assertEquals(1, ClickerStore.data.value.profile.points.size)
        assertEquals(10, ClickerStore.data.value.profile.points.first().x)
        assertEquals(1, ClickerStore.undoCount.value)
    }

    @Test
    fun undoRestoresADeletedPointAtItsOriginalPosition() {
        val first = ClickerStore.addPoint(ClickPoint(x = 1, y = 1))
        ClickerStore.addPoint(ClickPoint(x = 2, y = 2))
        ClickerStore.addPoint(ClickPoint(x = 3, y = 3))

        ClickerStore.removePoint(first.id)
        assertEquals(2, ClickerStore.data.value.profile.points.size)

        ClickerStore.undo()

        val points = ClickerStore.data.value.profile.points
        assertEquals(3, points.size)
        assertEquals(1, points.first().x)
    }

    @Test
    fun undoRestoresEverythingAfterClear() {
        ClickerStore.addPoint(ClickPoint(x = 1, y = 1))
        ClickerStore.addPoint(ClickPoint(x = 2, y = 2))

        ClickerStore.clearPoints()
        assertTrue(ClickerStore.data.value.profile.points.isEmpty())

        ClickerStore.undo()
        assertEquals(2, ClickerStore.data.value.profile.points.size)
    }

    @Test
    fun undoRevertsADraggedPoint() {
        val point = ClickerStore.addPoint(ClickPoint(x = 100, y = 100))
        ClickerStore.updatePoint(point.copy(x = 300, y = 400))
        assertEquals(300, ClickerStore.data.value.profile.points.first().x)

        ClickerStore.undo()

        assertEquals(100, ClickerStore.data.value.profile.points.first().x)
    }

    @Test
    fun undoOnEmptyHistoryReturnsNull() {
        assertNull(ClickerStore.undo())
    }

    @Test
    fun applyingATemplateKeepsThePoints() {
        ClickerStore.addPoint(ClickPoint(x = 50, y = 60))
        ClickerStore.updateProfile { it.copy(intervalMs = 999, randomDelayMs = 0) }

        ClickerStore.applyTemplate(Templates.builtIn.first { it.name == "极速 50ms" })

        val profile = ClickerStore.data.value.profile
        assertEquals(50, profile.intervalMs)
        assertEquals(1, profile.points.size)
        assertEquals(50, profile.points.first().x)
    }

    @Test
    fun savedTemplateCanBeAppliedAndDeleted() {
        ClickerStore.updateProfile {
            it.copy(mode = ClickMode.SIMULTANEOUS, intervalMs = 321, randomDelayMs = 55)
        }
        val template = ClickerStore.saveTemplate("我的模板")
        assertEquals(1, ClickerStore.data.value.templates.size)

        ClickerStore.updateProfile { it.copy(intervalMs = 1, randomDelayMs = 0) }
        ClickerStore.applyTemplate(template)

        val profile = ClickerStore.data.value.profile
        assertEquals(321, profile.intervalMs)
        assertEquals(55, profile.randomDelayMs)
        assertEquals(ClickMode.SIMULTANEOUS, profile.mode)

        ClickerStore.deleteTemplate(template.id)
        assertTrue(ClickerStore.data.value.templates.isEmpty())
    }

    @Test
    fun perPointPressAndDelaySurviveAnEdit() {
        val point = ClickerStore.addPoint(ClickPoint(x = 10, y = 10))
        ClickerStore.updatePoint(point.copy(pressMs = 1_500, delayMs = 800))

        val stored = ClickerStore.data.value.profile.points.single()
        assertEquals(1_500, stored.pressMs)
        assertEquals(800, stored.delayMs)
        assertTrue(stored.enabled)

        ClickerStore.updatePoint(stored.copy(x = 11), recordUndo = false)
        assertEquals(800, ClickerStore.data.value.profile.points.single().delayMs)
    }

    @Test
    fun aDisabledPointIsKeptButMarked() {
        val point = ClickerStore.addPoint(ClickPoint(x = 5, y = 6))
        ClickerStore.updatePoint(point.copy(enabled = false))

        val stored = ClickerStore.data.value.profile.points.single()
        assertTrue(!stored.enabled)
        assertEquals(5, stored.x)
    }

    @Test
    fun sliderEditsDoNotFloodTheUndoHistory() {
        val point = ClickerStore.addPoint(ClickPoint(x = 1, y = 1))
        assertEquals(1, ClickerStore.undoCount.value)
        repeat(5) { step ->
            ClickerStore.updatePoint(point.copy(delayMs = step * 100), recordUndo = false)
        }
        assertEquals(1, ClickerStore.undoCount.value)
        assertEquals(400, ClickerStore.data.value.profile.points.single().delayMs)
    }

    @Test
    fun newPointsDefaultToEnabledWithNoDelay() {
        val point = ClickerStore.addPoint(ClickPoint(x = 1, y = 2))
        assertEquals(0, point.delayMs)
        assertTrue(point.enabled)
        assertEquals(0, point.pressMs)
    }

    @Test
    fun aPlanKeepsPointsAndParametersTogether() {
        ClickerStore.addPoint(ClickPoint(x = 10, y = 20, delayMs = 300))
        ClickerStore.addPoint(ClickPoint(x = 30, y = 40))
        ClickerStore.updateProfile { it.copy(mode = ClickMode.SIMULTANEOUS, intervalMs = 250) }
        val plan = ClickerStore.savePlan("游戏 A")

        // Wipe everything, then come back through the plan.
        ClickerStore.clearPoints()
        ClickerStore.updateProfile { it.copy(mode = ClickMode.SEQUENTIAL, intervalMs = 999) }
        assertTrue(ClickerStore.data.value.profile.points.isEmpty())

        ClickerStore.applyPlan(plan)

        val profile = ClickerStore.data.value.profile
        assertEquals(2, profile.points.size)
        assertEquals(300, profile.points.first().delayMs)
        assertEquals(ClickMode.SIMULTANEOUS, profile.mode)
        assertEquals(250, profile.intervalMs)
        assertEquals(plan.id, ClickerStore.data.value.activePlanId)
    }

    @Test
    fun updatingAPlanOverwritesItWithTheCurrentScreen() {
        ClickerStore.addPoint(ClickPoint(x = 1, y = 1))
        val plan = ClickerStore.savePlan("方案")
        ClickerStore.addPoint(ClickPoint(x = 2, y = 2))

        ClickerStore.updatePlanFromCurrent(plan.id)

        val stored = ClickerStore.data.value.plans.single()
        assertEquals(2, stored.pointCount)
    }

    @Test
    fun deletingTheActivePlanResetsTheSelection() {
        ClickerStore.addPoint(ClickPoint(x = 5, y = 5))
        val plan = ClickerStore.savePlan("方案")
        ClickerStore.deletePlan(plan.id)
        assertTrue(ClickerStore.data.value.plans.isEmpty())
        assertEquals(0L, ClickerStore.data.value.activePlanId)
    }

    @Test
    fun plansAndTemplatesStaySeparate() {
        ClickerStore.addPoint(ClickPoint(x = 7, y = 7))
        val plan = ClickerStore.savePlan("方案")
        ClickerStore.saveTemplate("参数模板")
        assertEquals(1, ClickerStore.data.value.plans.size)
        assertEquals(1, ClickerStore.data.value.templates.size)
        // A template never carries the points, a plan always does.
        assertEquals(0, ClickerStore.data.value.templates.single().parameters().points.size)
        ClickerStore.applyPlan(plan)
        assertEquals(1, ClickerStore.data.value.profile.points.size)
    }

    @Test
    fun aSwipePointKeepsItsEndPointAndDuration() {
        val point = ClickerStore.addPoint(
            ClickPoint(x = 100, y = 200, kind = PointKind.SWIPE, x2 = 400, y2 = 800, swipeMs = 350),
        )
        val stored = ClickerStore.data.value.profile.points.single()
        assertTrue(stored.isSwipe)
        assertEquals(400, stored.x2)
        assertEquals(800, stored.y2)
        assertEquals(350, stored.swipeMs)
        assertEquals("(100, 200) → (400, 800)", stored.label)

        ClickerStore.updatePoint(point.copy(swipeMs = 600))
        assertEquals(600, ClickerStore.data.value.profile.points.single().swipeMs)
    }

    @Test
    fun plansCarrySwipesAndTapsTogether() {
        ClickerStore.addPoint(ClickPoint(x = 1, y = 1))
        ClickerStore.addPoint(ClickPoint(x = 2, y = 2, kind = PointKind.SWIPE, x2 = 9, y2 = 9, swipeMs = 500))
        val plan = ClickerStore.savePlan("混合")
        ClickerStore.clearPoints()

        ClickerStore.applyPlan(plan)

        val points = ClickerStore.data.value.profile.points
        assertEquals(2, points.size)
        assertEquals(1, points.count { it.isSwipe })
        assertEquals(500, points.first { it.isSwipe }.swipeMs)
    }

    @Test
    fun bumpHelpersStayInsideTheirRanges() {
        ClickerStore.updateProfile { it.copy(randomDelayMs = 0, intervalMs = 5) }
        ClickerStore.bumpRandomDelay(-50)
        assertEquals(0, ClickerStore.data.value.profile.randomDelayMs)
        ClickerStore.bumpInterval(-500)
        assertEquals(1, ClickerStore.data.value.profile.intervalMs)
        ClickerStore.bumpRepeat(-1)
        assertEquals(0, ClickerStore.data.value.profile.repeatCount)
    }
}
