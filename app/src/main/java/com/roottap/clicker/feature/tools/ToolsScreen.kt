package com.roottap.clicker.feature.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.DropdownPreference
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.PreferenceIcon
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.SettingIcons
import com.zeroui.kit.components.SwitchPreference
import com.zeroui.kit.components.ZeroPageFrame
import com.roottap.clicker.R

private val CardModifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)

/** Sample tab for app-specific settings. */
@Composable
fun ToolsScreen(bottomPadding: Dp) {
    var enabled by rememberSaveable { mutableStateOf(true) }
    var mode by rememberSaveable { mutableIntStateOf(0) }

    ZeroPageFrame(
        title = stringResource(R.string.tab_settings),
        bottomInset = bottomPadding,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item { SectionTitle(stringResource(R.string.tools_section_general)) }
            item {
                ListCard(CardModifier) {
                    SwitchPreference(
                        title = stringResource(R.string.tools_enable),
                        summary = stringResource(R.string.tools_enable_sub),
                        startAction = { PreferenceIcon(SettingIcons.tune) },
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                    DropdownPreference(
                        title = stringResource(R.string.tools_mode),
                        summary = stringResource(R.string.tools_mode_sub),
                        startAction = { PreferenceIcon(SettingIcons.layers) },
                        items = listOf(
                            stringResource(R.string.tools_mode_standard),
                            stringResource(R.string.tools_mode_fast),
                        ),
                        selectedIndex = mode,
                        onSelectedIndexChange = { mode = it },
                    )
                }
            }
        }
    }
}
