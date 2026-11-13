package com.roottap.clicker.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.DialogButtons
import com.zeroui.kit.components.ManagedDialog
import com.zeroui.kit.components.Slider
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextField

/**
 * Dialog with one numeric field; used for intervals, counts and coordinates.
 * Built from ZeroUI pieces only: ManagedDialog, TextField, Slider and DialogButtons.
 */
@Composable
fun NumberDialog(
    title: String,
    initial: Int,
    min: Int = 0,
    max: Int = 100_000,
    suffix: String = "",
    allowSlider: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()?.coerceIn(min, max)
    ManagedDialog(onDismiss = onDismiss, title = title) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(7) },
                singleLine = true,
                label = if (suffix.isNotEmpty()) "单位 $suffix" else "数值",
                useLabelAsPlaceholder = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (allowSlider) {
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = (parsed ?: initial).toFloat(),
                    onValueChange = { text = it.toInt().toString() },
                    valueRange = min.toFloat()..max.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = "范围 $min – $max$suffix",
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                confirmText = "确定",
                confirmEnabled = parsed != null,
                onConfirm = { close { parsed?.let(onConfirm) } },
                onCancel = { close(onDismiss) },
            )
        }
    }
}

/**
 * Edits one click point: coordinates, an optional press override and an optional delay
 * that only this point waits before it is clicked.
 */
@Composable
fun PointDialog(
    title: String,
    initialX: Int,
    initialY: Int,
    onDismiss: () -> Unit,
    initialPress: Int = 0,
    initialDelay: Int = 0,
    initialSwipeMs: Int = 300,
    /** Swipe points set how long the finger takes instead of how long it presses. */
    swipe: Boolean = false,
    showPress: Boolean = true,
    onConfirm: (x: Int, y: Int, pressMs: Int, delayMs: Int, swipeMs: Int) -> Unit,
) {
    var x by remember { mutableStateOf(initialX.toString()) }
    var y by remember { mutableStateOf(initialY.toString()) }
    var press by remember { mutableStateOf(initialPress) }
    var delay by remember { mutableStateOf(initialDelay) }
    var swipeMs by remember { mutableStateOf(initialSwipeMs) }
    val px = x.toIntOrNull()
    val py = y.toIntOrNull()

    ManagedDialog(onDismiss = onDismiss, title = title) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = x,
                    onValueChange = { input -> x = input.filter { it.isDigit() }.take(5) },
                    singleLine = true,
                    label = "X",
                    useLabelAsPlaceholder = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                TextField(
                    value = y,
                    onValueChange = { input -> y = input.filter { it.isDigit() }.take(5) },
                    singleLine = true,
                    label = "Y",
                    useLabelAsPlaceholder = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            if (swipe) {
                Spacer(Modifier.height(12.dp))
                Text(text = "滑动用时 · ${formatMs(swipeMs)}")
                Slider(
                    value = swipeMs.toFloat(),
                    onValueChange = { swipeMs = ((it.toInt() / 20) * 20).coerceAtLeast(20) },
                    valueRange = 20f..5_000f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = "从 A 滑到 B 用多久，手机上常见 0.2~0.5 秒")
            }
            if (showPress) {
                Spacer(Modifier.height(12.dp))
                Text(text = if (press == 0) "按压时长 · 用默认" else "按压时长 · ${formatMs(press)}")
                Slider(
                    value = press.toFloat(),
                    onValueChange = { press = (it.toInt() / 50) * 50 },
                    valueRange = 0f..10_000f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(text = if (delay == 0) "点击延迟 · 不额外等待" else "点击延迟 · ${formatMs(delay)}")
                Slider(
                    value = delay.toFloat(),
                    onValueChange = { delay = (it.toInt() / 50) * 50 },
                    valueRange = 0f..10_000f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                confirmText = "确定",
                confirmEnabled = px != null && py != null,
                onConfirm = {
                    close {
                        if (px != null && py != null) onConfirm(px, py, press, delay, swipeMs)
                    }
                },
                onCancel = { close(onDismiss) },
            )
        }
    }
}

/** Dialog with a single text field. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    hint: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    ManagedDialog(onDismiss = onDismiss, title = title) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = hint.ifEmpty { "名称" },
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                confirmText = "保存",
                confirmEnabled = text.isNotBlank(),
                onConfirm = { close { onConfirm(text.trim()) } },
                onCancel = { close(onDismiss) },
            )
        }
    }
}

fun formatMs(value: Int): String =
    if (value >= 1_000) String.format("%.2f 秒", value / 1000f) else "$value ms"
