package com.roottap.clicker

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.ZeroPageFrame
import com.zeroui.kit.motion.ZeroMotion
import com.zeroui.kit.navigation.PageTransition
import com.zeroui.kit.navigation.ZeroNavHost
import com.zeroui.kit.navigation.page
import com.zeroui.kit.navigation.pushUnique
import com.roottap.clicker.feature.gallery.GallerySectionScreen
import com.roottap.clicker.feature.main.MainScreen
import com.roottap.clicker.feature.settings.AboutPage
import com.roottap.clicker.feature.settings.AppSettingsPage
import com.roottap.clicker.feature.settings.ContributorsPage
import com.roottap.clicker.feature.settings.GlassIntensityPage
import com.roottap.clicker.feature.settings.HomeBackgroundPage
import com.roottap.clicker.feature.settings.LicensesPage
import com.roottap.clicker.feature.settings.NavTabsPage
import com.roottap.clicker.feature.settings.ThemeSettingsPage
import com.roottap.clicker.feature.setup.SetupScreen
import com.roottap.clicker.navigation.AppRoute
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import kotlinx.coroutines.launch

/** Chooses between the welcome flow and the main navigation graph. */
@Composable
fun AppRoot(welcomeCompleted: Boolean, pageTransition: PageTransition) {
    AnimatedContent(
        targetState = welcomeCompleted,
        transitionSpec = { ZeroMotion.crossFade() },
        label = "appRoot",
    ) { completed ->
        if (completed) AppNavGraph(pageTransition) else SetupScreen(onDone = {})
    }
}

@Composable
private fun AppNavGraph(pageTransition: PageTransition) {
    val controller = rememberNavController<AppRoute>(AppRoute.Main)
    ZeroNavHost(
        controller = controller,
        transition = pageTransition,
    ) {
        page<AppRoute.Main>(controller) {
            MainScreen(
                // Main stays composed under pushed pages; this tells it when it is covered.
                isActive = controller.backStack.size == 1,
                onNavigate = { route -> controller.pushUnique(route) },
            )
        }
        page<AppRoute.Setup>(controller) {
            SetupScreen(onDone = { controller.pop() }, onBack = { controller.pop() })
        }
        page<AppRoute.AppSettings>(controller) {
            SettingsPage(controller, R.string.app_settings) { padding ->
                AppSettingsPage(
                    pad = padding,
                    onTheme = { controller.pushUnique(AppRoute.Theme) },
                    onAbout = { controller.pushUnique(AppRoute.About) },
                )
            }
        }
        page<AppRoute.Theme>(controller) {
            SettingsPage(controller, R.string.theme_settings) { padding ->
                ThemeSettingsPage(
                    pad = padding,
                    onGlassIntensity = { controller.pushUnique(AppRoute.GlassIntensity) },
                    onNavTabs = { controller.pushUnique(AppRoute.NavTabs) },
                    onHomeBackground = { controller.pushUnique(AppRoute.HomeBackground) },
                )
            }
        }
        page<AppRoute.NavTabs>(controller) {
            SettingsPage(controller, R.string.nav_tabs_title) { padding -> NavTabsPage(padding) }
        }
        page<AppRoute.HomeBackground>(controller) {
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            SettingsPage(controller, R.string.home_bg_title, snackbar) { padding ->
                HomeBackgroundPage(
                    pad = padding,
                    toast = { message -> scope.launch { snackbar.showSnackbar(message) } },
                )
            }
        }
        page<AppRoute.GlassIntensity>(controller) {
            SettingsPage(controller, R.string.glass_intensity) { padding -> GlassIntensityPage(padding) }
        }
        page<AppRoute.About>(controller) {
            SettingsPage(controller, R.string.about) { padding ->
                AboutPage(
                    pad = padding,
                    onLicenses = { controller.pushUnique(AppRoute.Licenses) },
                    onContributors = { controller.pushUnique(AppRoute.Contributors) },
                )
            }
        }
        page<AppRoute.Licenses>(controller) {
            SettingsPage(controller, R.string.open_source_licenses) { padding -> LicensesPage(padding) }
        }
        page<AppRoute.Contributors>(controller) {
            SettingsPage(controller, R.string.contributors_title) { padding -> ContributorsPage(padding) }
        }
        page<AppRoute.Components>(controller) { route ->
            GallerySectionScreen(section = route.section, onBack = { controller.pop() })
        }
    }
}

@Composable
private fun SettingsPage(
    controller: NavController,
    titleRes: Int,
    snackbar: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    ZeroPageFrame(
        title = stringResource(titleRes),
        onBack = { controller.pop() },
        bottomInset = 96.dp,
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        content = content,
    )
}
