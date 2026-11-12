package com.zeroui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.ArrowPreference
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.components.Card
import com.zeroui.kit.components.SwitchPreference
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.theme.UiStyle
import com.zeroui.kit.theme.ThemeSettings
import com.zeroui.kit.theme.ZeroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Renders the core components over screen size, layout direction, font scale and style. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompatMatrixTest {
    private data class Screen(val name: String, val qualifiers: String)

    private val screens = listOf(
        Screen("phone-portrait", "w360dp-h800dp-port"),
        Screen("phone-landscape", "w800dp-h360dp-land"),
        Screen("foldable-unfolded", "w840dp-h900dp-port"),
    )
    private val directions = listOf(LayoutDirection.Ltr to "en-ldltr", LayoutDirection.Rtl to "ar-ldrtl")
    private val fontScales = listOf(1.0f, 1.3f)

    @Composable
    private fun Sample() {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Card(Modifier.fillMaxWidth()) { Text("Card body") }
                TextButton(text = "Action", onClick = {}, variant = ButtonVariant.Primary)
                ArrowPreference(title = "Arrow row", summary = "Summary text", onClick = {})
                SwitchPreference(checked = true, onCheckedChange = {}, title = "Switch row")
            }
        }
    }

    @Test
    fun componentsStayInsideTheScreenInEveryCombination() {
        var combinations = 0
        for (screen in screens) for ((direction, directionQualifier) in directions) for (scale in fontScales) {
            for (style in UiStyle.entries) {
                RuntimeEnvironment.setQualifiers("$directionQualifier-${screen.qualifiers}")
                RuntimeEnvironment.setFontScale(scale)
                val label = "${screen.name}/$directionQualifier/font$scale/$style"
                var seenDirection: LayoutDirection? = null
                var seenFontScale = 0f
                runComposeUiTest {
                    setContent {
                        ZeroTheme(ThemeSettings(style = style)) {
                            seenDirection = LocalLayoutDirection.current
                            seenFontScale = LocalDensity.current.fontScale
                            Sample()
                        }
                    }
                    val root = onRoot().fetchSemanticsNode().size
                    for (text in listOf("Card body", "Action", "Arrow row", "Switch row")) {
                        val bounds = onNodeWithText(text).fetchSemanticsNode().boundsInRoot
                        assertTrue("$label: '$text' starts left of the screen", bounds.left >= 0f)
                        assertTrue("$label: '$text' ends right of the screen", bounds.right <= root.width.toFloat())
                    }
                }
                assertEquals("$label: layout direction", direction, seenDirection)
                assertEquals("$label: font scale", scale, seenFontScale, 0.001f)
                combinations++
            }
        }
        assertEquals(screens.size * directions.size * fontScales.size * UiStyle.entries.size, combinations)
    }
}
