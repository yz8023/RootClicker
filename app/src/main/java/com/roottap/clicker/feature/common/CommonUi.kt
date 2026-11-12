package com.roottap.clicker.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.Slider
import com.zeroui.kit.components.Text

/** Dialog with one numeric field; used for intervals, counts and coordinates. */
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { MaterialText(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (allowSlider) {
                    Slider(
                        value = (parsed ?: initial).toFloat(),
                        onValueChange = { text = it.toInt().toString() },
                        valueRange = min.toFloat()..max.toFloat(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (suffix.isNotEmpty()) {
                    Text(
                        text = "范围 $min – $max$suffix",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            MaterialTextButton(
                enabled = parsed != null,
                onClick = { parsed?.let(onConfirm) },
            ) { MaterialText("确定") }
        },
        dismissButton = {
            MaterialTextButton(onClick = onDismiss) { MaterialText("取消") }
        },
    )
}

/** Dialog that edits one click point: coordinates plus an optional press duration override. */
@Composable
fun PointDialog(
    title: String,
    initialX: Int,
    initialY: Int,
    onDismiss: () -> Unit,
    initialPress: Int = 0,
    showPress: Boolean = true,
    onConfirm: (Int, Int, Int) -> Unit,
) {
    var x by remember { mutableStateOf(initialX.toString()) }
    var y by remember { mutableStateOf(initialY.toString()) }
    var press by remember { mutableStateOf(initialPress.toString()) }
    val px = x.toIntOrNull()
    val py = y.toIntOrNull()
    val pressValue = press.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { MaterialText(title) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = x,
                        onValueChange = { input -> x = input.filter { it.isDigit() }.take(5) },
                        singleLine = true,
                        label = { MaterialText("X") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = y,
                        onValueChange = { input -> y = input.filter { it.isDigit() }.take(5) },
                        singleLine = true,
                        label = { MaterialText("Y") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showPress) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = press,
                        onValueChange = { input -> press = input.filter { it.isDigit() }.take(5) },
                        singleLine = true,
                        label = { MaterialText("单独按压时长 ms（0 = 用默认）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            MaterialTextButton(
                enabled = px != null && py != null,
                onClick = { if (px != null && py != null) onConfirm(px, py, pressValue) },
            ) { MaterialText("确定") }
        },
        dismissButton = {
            MaterialTextButton(onClick = onDismiss) { MaterialText("取消") }
        },
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { MaterialText(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { MaterialText(hint) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            MaterialTextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ) { MaterialText("确定") }
        },
        dismissButton = {
            MaterialTextButton(onClick = onDismiss) { MaterialText("取消") }
        },
    )
}
