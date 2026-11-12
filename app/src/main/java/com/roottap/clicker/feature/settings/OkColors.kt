package com.roottap.clicker.feature.settings

import com.zeroui.kit.theme.LocalIsDark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalMonetEnabled = staticCompositionLocalOf { false }

private val DefaultOkAccent = Color(0xFF36D167)
private val DefaultOkContainerLight = Color(0xFFDFFAE4)
private val DefaultOkContainerDark = Color(0xFF1A3825)

val okAccentColor: Color
    @Composable get() =
        if (LocalMonetEnabled.current) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else DefaultOkAccent

val okContainerColor: Color
    @Composable get() = when {
        LocalMonetEnabled.current -> MiuixTheme.colorScheme.secondaryContainer
        LocalIsDark.current -> DefaultOkContainerDark
        else -> DefaultOkContainerLight
    }
