package com.roottap.clicker.feature.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.ConfirmDialog
import com.zeroui.kit.components.Icon
import com.zeroui.kit.glass.LiquidBottomTab
import com.zeroui.kit.glass.LiquidBottomTabs
import com.zeroui.kit.glass.LocalLiquidBottomTabActive
import com.zeroui.kit.glass.LocalLiquidIntensity
import com.zeroui.kit.glass.LocalPageBackground
import com.zeroui.kit.glass.frostedGlass
import com.zeroui.kit.glass.isLiquidGlassSupported
import com.zeroui.kit.glass.liquidSource
import com.zeroui.kit.glass.rememberBlurBackdrop
import com.zeroui.kit.glass.rememberLiquidBackdrop
import com.zeroui.kit.glass.rememberSquircleShape
import com.zeroui.kit.motion.LocalReducedMotion
import com.zeroui.kit.theme.GlassMode
import com.zeroui.kit.theme.LocalGlassMode
import com.zeroui.kit.theme.isMaterial
import com.roottap.clicker.BuildConfig
import com.roottap.clicker.R
import com.roottap.clicker.data.AvailableUpdate
import com.roottap.clicker.data.GithubReleaseChecker
import com.roottap.clicker.data.UpdateResult
import com.roottap.clicker.feature.gallery.GalleryScreen
import com.roottap.clicker.feature.click.ClickScreen
import com.roottap.clicker.feature.record.RecordScreen
import com.roottap.clicker.feature.settings.ClickerSettingsScreen
import com.roottap.clicker.feature.home.rememberPageBackground
import com.roottap.clicker.feature.settings.AppRepository
import com.roottap.clicker.feature.settings.BackgroundScope
import com.roottap.clicker.feature.settings.LocalAppController
import com.roottap.clicker.feature.settings.LocalAppearanceExtras
import com.roottap.clicker.feature.settings.NavLabelMode
import com.roottap.clicker.feature.settings.observe
import com.roottap.clicker.feature.settings.WideNav
import com.roottap.clicker.feature.tools.ToolsScreen
import com.roottap.clicker.feature.update.UpdateDialog
import com.roottap.clicker.feature.update.openDownload
import com.roottap.clicker.navigation.AppRoute
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import com.zeroui.kit.glass.blurSource
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import androidx.compose.material3.Badge as MaterialBadge
import androidx.compose.material3.BadgedBox as MaterialBadgedBox
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme as Material
import androidx.compose.material3.NavigationBar as MaterialNavigationBar
import androidx.compose.material3.NavigationBarItem as MaterialNavigationBarItem
import androidx.compose.material3.NavigationRail as MaterialNavigationRail
import androidx.compose.material3.NavigationRailItem as MaterialNavigationRailItem
import androidx.compose.material3.Text as MaterialText

private val NavBottomPadding = 96.dp
private val UpdateDot: @Composable () -> Unit = { Badge() }
private val DockedNavBottomPadding = NavigationBarDefaults.ItemHeight
private val MaterialNavBottomPadding = 80.dp
private val RailMinWidth = 600.dp
private val NavGlassWidth = 200.dp
private val NavGlassHeight = 56.dp
private val LiquidNavBottomGap = 28.dp
private const val AutoCheckIntervalMs = 6 * 60 * 60 * 1000L
private const val NavRetreatSpan = 0.6f

/** Navigation bar placement, used to pick the retreat direction. */
private enum class NavEdge { Bottom, Start }

/**
 * Moves a navigation bar off its edge and fades it as this page gets covered.
 *
 * @param extraDistance Gap between the bar and the screen edge, added to the travel distance.
 */
private fun Modifier.retreatWhenCovered(
    transition: NavTransitionScope,
    edge: NavEdge,
    reduced: Boolean,
    extraDistance: Dp = 0.dp,
): Modifier = graphicsLayer {
    val covered = (transition.relativeDepth / NavRetreatSpan).coerceIn(0f, 1f)
    val eased = covered * covered * (3f - 2f * covered)
    alpha = 1f - eased
    if (!reduced) {
        when (edge) {
            NavEdge.Bottom -> translationY = eased * (size.height + extraDistance.toPx())
            NavEdge.Start -> {
                val direction = if (transition.layoutDirection == LayoutDirection.Rtl) 1f else -1f
                translationX = direction * eased * size.width
            }
        }
    }
}

/**
 * Main frame: tab pager with a docked bar, floating bar, Liquid Glass bar or side rail.
 *
 * @param isActive True while this page is the top of the navigation stack.
 */
@Composable
fun MainScreen(
    isActive: Boolean,
    onNavigate: (AppRoute) -> Unit,
) {
    val context = LocalContext.current
    val controller = LocalAppController.current
    val visibleTabs = controller.observe { it.navLayout().order }
    val pagerState = rememberPagerState(
        initialPage = visibleTabs.indexOf(MainTab.Home).coerceAtLeast(0),
        pageCount = { visibleTabs.size },
    )
    val pagerScope = rememberCoroutineScope()
    val tab = visibleTabs.getOrElse(pagerState.targetPage) { MainTab.Home }

    var activeTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    val visibleTabsState = rememberUpdatedState(visibleTabs)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            visibleTabsState.value.getOrNull(page)?.let { activeTab = it }
        }
    }
    LaunchedEffect(visibleTabs) {
        val index = visibleTabs.indexOf(activeTab).let { if (it >= 0) it else 0 }
        if (index != pagerState.currentPage) pagerState.scrollToPage(index)
    }
    var neighborsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        neighborsReady = true
    }
    val goToTab: (Int) -> Unit = { index ->
        if (index != pagerState.targetPage) pagerScope.launch { pagerState.springAnimateToPage(index) }
    }

    var showExit by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val settings = controller.settings
        if (!settings.autoCheckUpdate) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck < AutoCheckIntervalMs) return@LaunchedEffect
        when (val result = GithubReleaseChecker(AppRepository).check(BuildConfig.VERSION_NAME)) {
            is UpdateResult.Available -> {
                availableUpdate = result.update
                showUpdateDialog = true
                controller.setLastUpdateCheck(now)
            }
            UpdateResult.UpToDate -> {
                availableUpdate = null
                controller.setLastUpdateCheck(now)
            }
            UpdateResult.Failed -> Unit
        }
    }

    val glassMode = LocalGlassMode.current
    val liquid = glassMode == GlassMode.Liquid && isLiquidGlassSupported()
    val docked = glassMode == GlassMode.Off
    val backdrop = if (liquid) null else rememberBlurBackdrop()
    val liquidBackdrop = rememberLiquidBackdrop()
    val intensity = LocalLiquidIntensity.current
    val selectedTabProvider = remember(pagerState) { { pagerState.targetPage } }
    val navShape = rememberSquircleShape(FloatingToolbarDefaults.CornerRadius)

    BackHandler(enabled = isActive) {
        if (tab != MainTab.Home) goToTab(visibleTabs.indexOf(MainTab.Home)) else showExit = true
    }

    val material = isMaterial()
    val background = if (material) Material.colorScheme.surfaceContainer else MiuixTheme.colorScheme.background

    val tabs: @Composable (bottomPadding: Dp) -> Unit = { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = if (neighborsReady) 1 else 0,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = PagerNavigationSpringSpec,
            ),
            key = { visibleTabs.getOrNull(it)?.name ?: "page-$it" },
        ) { index ->
            when (visibleTabs.getOrNull(index)) {
                MainTab.Home -> ClickScreen(bottomPadding = padding)
                MainTab.Components -> RecordScreen(bottomPadding = padding)
                MainTab.Settings -> ClickerSettingsScreen(bottomPadding = padding)
                null -> Unit
            }
        }
    }

    val tabBadge: (MainTab) -> (@Composable () -> Unit)? = { entry ->
        if (entry == MainTab.Home && availableUpdate != null) UpdateDot else null
    }
    val navLabels = LocalAppearanceExtras.current.navLabels
    val railAllowed = LocalAppearanceExtras.current.wideNav == WideNav.Rail

    val floatingBar: @Composable () -> Unit = {
        FloatingNavigationBar(
            color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            modifier = Modifier.frostedGlass(
                backdrop = backdrop,
                shape = navShape,
                tint = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
            ),
        ) {
            visibleTabs.forEach { entry ->
                FloatingNavigationBarItem(
                    selected = tab == entry,
                    onClick = { goToTab(visibleTabs.indexOf(entry)) },
                    icon = mainTabIcon(entry),
                    label = stringResource(entry.labelRes),
                    badge = tabBadge(entry),
                )
            }
        }
    }
    val dockedBar: @Composable () -> Unit = {
        NavigationBar(
            mode = when (navLabels) {
                NavLabelMode.IconAndText -> NavigationBarDisplayMode.IconAndText
                NavLabelMode.SelectedOnly -> NavigationBarDisplayMode.IconWithSelectedLabel
                NavLabelMode.IconOnly -> NavigationBarDisplayMode.IconOnly
            },
        ) {
            visibleTabs.forEach { entry ->
                NavigationBarItem(
                    selected = tab == entry,
                    onClick = { goToTab(visibleTabs.indexOf(entry)) },
                    icon = mainTabIcon(entry),
                    label = stringResource(entry.labelRes),
                    badge = tabBadge(entry),
                )
            }
        }
    }
    val rail: @Composable () -> Unit = {
        NavigationRail {
            visibleTabs.forEach { entry ->
                NavigationRailItem(
                    selected = tab == entry,
                    onClick = { goToTab(visibleTabs.indexOf(entry)) },
                    icon = mainTabIcon(entry),
                    label = stringResource(entry.labelRes),
                    badge = tabBadge(entry),
                )
            }
        }
    }
    val materialIcon: @Composable (MainTab) -> Unit = { entry ->
        MaterialBadgedBox(
            badge = { if (entry == MainTab.Home && availableUpdate != null) MaterialBadge() },
        ) {
            MaterialIcon(imageVector = mainTabIcon(entry), contentDescription = null)
        }
    }
    val materialBar: @Composable () -> Unit = {
        MaterialNavigationBar {
            visibleTabs.forEach { entry ->
                MaterialNavigationBarItem(
                    selected = tab == entry,
                    onClick = { goToTab(visibleTabs.indexOf(entry)) },
                    icon = { materialIcon(entry) },
                    label = if (navLabels == NavLabelMode.IconOnly) {
                        null
                    } else {
                        { MaterialText(stringResource(entry.labelRes)) }
                    },
                    alwaysShowLabel = navLabels == NavLabelMode.IconAndText,
                )
            }
        }
    }
    val materialRail: @Composable () -> Unit = {
        MaterialNavigationRail {
            visibleTabs.forEach { entry ->
                MaterialNavigationRailItem(
                    selected = tab == entry,
                    onClick = { goToTab(visibleTabs.indexOf(entry)) },
                    icon = { materialIcon(entry) },
                    label = { MaterialText(stringResource(entry.labelRes)) },
                )
            }
        }
    }

    val navTransition = LocalNavTransitionScope.current
    val reducedMotion = LocalReducedMotion.current
    val bottomRetreat = Modifier.retreatWhenCovered(navTransition, NavEdge.Bottom, reducedMotion)

    val pageBackground = rememberPageBackground()
        ?.takeIf { LocalAppearanceExtras.current.bgScope == BackgroundScope.All }
    CompositionLocalProvider(LocalPageBackground provides pageBackground) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(background)) {
            when {
                maxWidth >= RailMinWidth && railAllowed -> Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.retreatWhenCovered(navTransition, NavEdge.Start, reducedMotion)) {
                        if (material) materialRail() else rail()
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { tabs(0.dp) }
                }
                material -> Box(modifier = Modifier.fillMaxSize()) {
                    tabs(MaterialNavBottomPadding)
                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(bottomRetreat)) {
                        materialBar()
                    }
                }
                liquid && liquidBackdrop != null -> Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().liquidSource(liquidBackdrop)) { tabs(NavBottomPadding) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = LiquidNavBottomGap)
                            .retreatWhenCovered(navTransition, NavEdge.Bottom, reducedMotion, LiquidNavBottomGap),
                    ) {
                        LiquidBottomTabs(
                            selectedTabIndex = selectedTabProvider,
                            onTabSelected = goToTab,
                            backdrop = liquidBackdrop,
                            tabsCount = visibleTabs.size,
                            modifier = Modifier.width(NavGlassWidth),
                            barHeight = NavGlassHeight,
                            intensity = intensity,
                        ) {
                            val iconColor = MiuixTheme.colorScheme.onSurfaceContainer
                            visibleTabs.forEach { entry ->
                                LiquidBottomTab(onClick = { goToTab(visibleTabs.indexOf(entry)) }) {
                                    val active = LocalLiquidBottomTabActive.current
                                    Icon(
                                        imageVector = mainTabIcon(entry),
                                        contentDescription = stringResource(entry.labelRes),
                                        tint = if (active) iconColor else iconColor.copy(alpha = 0.4f),
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (backdrop != null) Modifier.blurSource(backdrop) else Modifier),
                    ) {
                        tabs(if (docked) DockedNavBottomPadding else NavBottomPadding)
                    }
                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(bottomRetreat)) {
                        if (docked) dockedBar() else floatingBar()
                    }
                }
            }
        }
    }

    if (showExit) {
        ConfirmDialog(
            title = stringResource(R.string.exit_app),
            message = stringResource(R.string.exit_app_msg),
            confirmText = stringResource(R.string.exit),
            onDismiss = { showExit = false },
            onConfirm = {
                showExit = false
                (context as? Activity)?.finish()
            },
        )
    }

    UpdateDialog(
        update = if (showUpdateDialog) availableUpdate else null,
        onDismiss = { showUpdateDialog = false },
        onUpdate = {
            availableUpdate?.let { openDownload(context, it.downloadUrl) }
            showUpdateDialog = false
        },
    )
}
