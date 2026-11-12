package com.roottap.clicker.feature.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.ArrowPreference
import com.zeroui.kit.components.Badge
import com.zeroui.kit.components.Button
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.components.Card
import com.zeroui.kit.components.CardSlice
import com.zeroui.kit.components.Checkbox
import com.zeroui.kit.components.CircularProgressIndicator
import com.zeroui.kit.components.ConfirmDialogHost
import com.zeroui.kit.components.ConfirmDialogState
import com.zeroui.kit.components.ConfirmRequest
import com.zeroui.kit.components.DialogButtons
import com.zeroui.kit.components.DropdownPreference
import com.zeroui.kit.components.LinearProgressIndicator
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.LoadingIndicator
import com.zeroui.kit.components.ManagedBottomSheet
import com.zeroui.kit.components.ManagedDialog
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.SlicePosition
import com.zeroui.kit.components.Slider
import com.zeroui.kit.components.Switch
import com.zeroui.kit.components.SwitchPreference
import com.zeroui.kit.components.TabRow
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.components.TextField
import com.zeroui.kit.components.ZeroPage
import com.zeroui.kit.navigation.DeferUntilSettled
import com.zeroui.kit.theme.ZeroTheme
import com.roottap.clicker.R
import com.roottap.clicker.navigation.AppRoute
import kotlinx.coroutines.launch

/** Components tab listing the gallery sections. */
@Composable
fun GalleryScreen(bottomInset: Dp, onNavigate: (AppRoute) -> Unit) {
    ZeroPage(
        title = stringResource(R.string.tab_components),
        bottomInset = bottomInset,
    ) {
        item {
            ListCard(DemoCardModifier) {
                GallerySection.entries.forEach { section ->
                    ArrowPreference(
                        title = stringResource(section.titleRes),
                        summary = stringResource(section.summaryRes),
                        onClick = { onNavigate(AppRoute.Components(section)) },
                    )
                }
            }
        }
    }
}

/** Detail page of one gallery section. */
@Composable
fun GallerySectionScreen(section: GallerySection, onBack: () -> Unit) {
    ZeroPage(
        title = stringResource(section.titleRes),
        onBack = onBack,
    ) {
        when (section) {
            GallerySection.Buttons -> item { ButtonsDemo() }
            GallerySection.Inputs -> item { InputsDemo() }
            GallerySection.Lists -> {
                item { ListsDemo() }
                item { SectionTitle(stringResource(R.string.gallery_lazy_rows)) }
                itemsIndexed(DemoRows) { index, label ->
                    CardSlice(
                        position = SlicePosition.of(index, DemoRows.size),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        ListRow(title = label)
                    }
                }
            }
            GallerySection.Feedback -> item { DeferUntilSettled { FeedbackDemo() } }
            GallerySection.Windows -> item { WindowsDemo() }
        }
    }
}

private val DemoCardModifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)

private val DemoRows = listOf("Alpha", "Beta", "Gamma", "Delta")

@Composable
private fun ButtonsDemo() {
    Card(DemoCardModifier, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = stringResource(R.string.gallery_button_primary),
                onClick = {},
                variant = ButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(R.string.gallery_button_secondary),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(R.string.gallery_button_disabled),
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {},
                containerColor = ZeroTheme.colors.danger,
                contentColor = ZeroTheme.colors.onDanger,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.gallery_button_custom))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(stringResource(R.string.gallery_badge))
            }
        }
    }
}

@Composable
private fun InputsDemo() {
    var text by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf(false) }
    var slider by rememberSaveable { mutableFloatStateOf(0.4f) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Card(DemoCardModifier, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.gallery_text_field),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gallery_switch), Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = { checked = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gallery_checkbox), Modifier.weight(1f))
                Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = { selected = !selected },
                )
            }
            Slider(value = slider, onValueChange = { slider = it }, modifier = Modifier.fillMaxWidth())
            TabRow(
                tabs = listOf(
                    stringResource(R.string.gallery_tab_one),
                    stringResource(R.string.gallery_tab_two),
                ),
                selectedTabIndex = tab,
                onTabSelected = { tab = it },
            )
        }
    }
}

@Composable
private fun ListsDemo() {
    var enabled by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    ListCard(DemoCardModifier) {
        ListRow(
            title = stringResource(R.string.gallery_row),
            summary = stringResource(R.string.gallery_row_summary),
        )
        ArrowPreference(title = stringResource(R.string.gallery_arrow_row), onClick = {})
        SwitchPreference(
            checked = enabled,
            onCheckedChange = { enabled = it },
            title = stringResource(R.string.gallery_switch_row),
        )
        DropdownPreference(
            items = listOf(
                stringResource(R.string.gallery_tab_one),
                stringResource(R.string.gallery_tab_two),
            ),
            selectedIndex = selected,
            onSelectedIndexChange = { selected = it },
            title = stringResource(R.string.gallery_dropdown_row),
        )
    }
}

@Composable
private fun FeedbackDemo() {
    Card(DemoCardModifier, contentPadding = PaddingValues(16.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = 0.6f)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(progress = 0.6f)
            LoadingIndicator()
        }
    }
}

@Composable
private fun WindowsDemo() {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var confirmed by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val confirmState = remember { ConfirmDialogState() }
    val scope = rememberCoroutineScope()
    val confirmRequest = ConfirmRequest(
        title = stringResource(R.string.gallery_confirm_title),
        message = stringResource(R.string.gallery_confirm_message),
        confirmText = stringResource(R.string.action_confirm),
    )

    Card(DemoCardModifier, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = stringResource(R.string.gallery_open_dialog),
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(R.string.gallery_open_sheet),
                onClick = { showSheet = true },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(R.string.gallery_open_confirm),
                onClick = {
                    scope.launch { confirmed = confirmState.confirm(confirmRequest) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            confirmed?.let { answer ->
                Text(
                    text = stringResource(
                        if (answer) R.string.gallery_confirm_yes else R.string.gallery_confirm_no,
                    ),
                    style = ZeroTheme.typography.caption,
                    color = ZeroTheme.colors.contentMuted,
                )
            }
        }
    }

    if (showDialog) {
        ManagedDialog(
            onDismiss = { showDialog = false },
            title = stringResource(R.string.gallery_dialog_title),
            summary = stringResource(R.string.gallery_dialog_summary),
        ) {
            DialogButtons(
                confirmText = stringResource(R.string.action_confirm),
                onConfirm = { close { showDialog = false } },
                onCancel = { close { showDialog = false } },
            )
        }
    }
    if (showSheet) {
        ManagedBottomSheet(
            onDismiss = { showSheet = false },
            title = stringResource(R.string.gallery_sheet_title),
        ) {
            Text(stringResource(R.string.gallery_dialog_summary))
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(R.string.action_confirm),
                onClick = { close { showSheet = false } },
                variant = ButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    ConfirmDialogHost(confirmState)
}
