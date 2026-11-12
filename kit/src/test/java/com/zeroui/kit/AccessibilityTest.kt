package com.zeroui.kit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import com.zeroui.kit.components.DraggableItem
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.rememberDragDropState
import com.zeroui.kit.theme.ThemeSettings
import com.zeroui.kit.theme.UiStyle
import com.zeroui.kit.theme.ZeroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessibilityTest {
    @Test
    fun sectionTitleIsExposedAsAHeadingInBothStyles() {
        for (style in UiStyle.entries) {
            runComposeUiTest {
                setContent { ZeroTheme(ThemeSettings(style = style)) { SectionTitle("Group") } }
                val config = onNodeWithText("Group").fetchSemanticsNode().config
                assertTrue("$style: heading", config.contains(SemanticsProperties.Heading))
            }
        }
    }

    @Test
    fun reorderableItemsOfferMoveActionsWithinTheirBounds() {
        val moves = mutableListOf<Pair<Int, Int>>()
        runComposeUiTest {
            setContent {
                ZeroTheme(ThemeSettings()) {
                    val listState = rememberLazyListState()
                    val drag = rememberDragDropState(listState, onMove = { from, to -> moves += from to to })
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items(3) { index ->
                            DraggableItem(drag, index, Modifier.testTag("row$index")) { Text("Row $index") }
                        }
                    }
                }
            }
            waitForIdle()

            fun labels(index: Int) = onNode(hasTestTag("row$index")).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].map { it.label }

            val up = RuntimeEnvironment.getApplication().getString(R.string.move_up)
            val down = RuntimeEnvironment.getApplication().getString(R.string.move_down)
            assertEquals(listOf(down), labels(0))
            assertEquals(listOf(up, down), labels(1))
            assertEquals(listOf(up), labels(2))

            val moveDown = onNode(hasTestTag("row1")).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].first { it.label == down }
            assertTrue(moveDown.action())
            assertEquals(listOf(1 to 2), moves)
        }
    }
}
