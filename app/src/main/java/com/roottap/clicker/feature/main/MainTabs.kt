package com.roottap.clicker.feature.main

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.roottap.clicker.R
import com.roottap.clicker.data.AppSettings
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings

/** Top-level tabs; [removable] tabs can be hidden from the navigation bar. */
enum class MainTab(@StringRes val labelRes: Int, val removable: Boolean) {
    Home(R.string.tab_home, false),
    Components(R.string.tab_components, true),
    Settings(R.string.tab_settings, false),
}

/** Icon of a tab. */
fun mainTabIcon(tab: MainTab): ImageVector = when (tab) {
    MainTab.Home -> MiuixIcons.GridView
    MainTab.Components -> MiuixIcons.Layers
    MainTab.Settings -> MiuixIcons.Settings
}

/** Visible tabs in order and hidden tabs. */
data class NavLayout(val order: List<MainTab>, val hidden: List<MainTab>)

private fun parseTabs(csv: String): List<MainTab> =
    csv.split(",").mapNotNull { name -> MainTab.entries.firstOrNull { it.name == name } }.distinct()

/** Reads the navigation layout; tabs missing from both lists return to their default position. */
fun AppSettings.navLayout(): NavLayout {
    val all = MainTab.entries.toList()
    // Stored text is not trusted: a hand-edited or imported value must not be able
    // to hide Home or Settings and lock the user out.
    val hidden = parseTabs(navHidden).filter { it.removable }
    val saved = parseTabs(navOrder).filterNot { it in hidden }
    val order = saved.toMutableList()
    // A tab added in a later version is in neither saved list.
    all.filterNot { it in saved || it in hidden }.forEach { tab ->
        order.add(all.indexOf(tab).coerceAtMost(order.size), tab)
    }
    return NavLayout(order, hidden)
}
