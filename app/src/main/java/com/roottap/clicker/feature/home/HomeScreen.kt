package com.roottap.clicker.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroui.kit.components.ArrowPreference
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.components.Card
import com.zeroui.kit.components.DraggableItem
import com.zeroui.kit.components.Icon
import com.zeroui.kit.components.IconButton
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.LoadingIndicator
import com.zeroui.kit.components.PreferenceIcon
import com.zeroui.kit.components.Scaffold
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.SettingIcons
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.components.TopAppBar
import com.zeroui.kit.components.dragContainer
import com.zeroui.kit.components.excludeFromDragContainer
import com.zeroui.kit.components.fastItemPlacement
import com.zeroui.kit.components.rememberDragDropState
import com.zeroui.kit.components.rememberTopBarScrollBehavior
import com.zeroui.kit.glass.BlurredTopBar
import com.zeroui.kit.glass.LocalPageBackground
import com.zeroui.kit.glass.rememberTopBarBackdrop
import com.zeroui.kit.glass.topBarBlurSource
import com.zeroui.kit.glass.topBarColor
import com.zeroui.kit.motion.ZeroMotion
import com.roottap.clicker.R
import com.roottap.clicker.core.appViewModel
import com.roottap.clicker.data.AppSettings
import com.roottap.clicker.data.DeviceInfo
import com.roottap.clicker.feature.gallery.GallerySection
import com.roottap.clicker.feature.settings.LocalAppController
import com.roottap.clicker.feature.settings.homeTitleText
import com.roottap.clicker.feature.settings.okAccentColor
import com.roottap.clicker.feature.settings.okContainerColor
import com.roottap.clicker.navigation.AppRoute
import com.zeroui.kit.components.PullToRefresh
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TooltipBox
import com.zeroui.kit.components.rememberPullRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.zeroui.kit.components.PressFeedback

/** Reorderable cards of the home page. */
enum class HomeCard { Status, Device, Components }

private data class HomeLayout(val visible: List<HomeCard>, val hidden: List<HomeCard>)

private fun parseCards(csv: String): List<HomeCard> =
    csv.split(",").mapNotNull { name -> HomeCard.entries.firstOrNull { it.name == name } }.distinct()

private fun AppSettings.homeLayout(): HomeLayout {
    val hidden = parseCards(homeHidden)
    val saved = parseCards(homeOrder).filterNot { it in hidden }
    val visible = saved.toMutableList()
    HomeCard.entries.filterNot { it in saved || it in hidden }.forEach { card ->
        visible.add(HomeCard.entries.indexOf(card).coerceAtMost(visible.size), card)
    }
    return HomeLayout(visible, hidden)
}

/** Visible home cards in their current order. */
internal fun AppSettings.visibleHomeCards(): List<HomeCard> = homeLayout().visible

/** Home tab with reorderable cards and the settings entry in the top bar. */
@Composable
fun HomeScreen(
    bottomPadding: Dp,
    hasUpdate: Boolean,
    onUpdateClick: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(setupChecks, deviceInfo) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val controller = LocalAppController.current
    val snackbar = remember { SnackbarHostState() }
    val pullState = rememberPullRefreshState()

    val saved = remember { controller.settings.homeLayout() }
    val order = remember { mutableStateListOf<HomeCard>().apply { addAll(saved.visible) } }
    val hidden = remember { mutableStateListOf<HomeCard>().apply { addAll(saved.hidden) } }
    var editing by rememberSaveable { mutableStateOf(false) }
    val persist = {
        controller.setHomeLayout(order.joinToString(",") { it.name }, hidden.joinToString(",") { it.name })
    }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(
        lazyListState = listState,
        canDrag = { index -> index in order.indices },
        onMove = { from, to ->
            order.add(to, order.removeAt(from))
            persist()
        },
    )
    LaunchedEffect(order.size) { if (order.isEmpty()) editing = true }
    BackHandler(enabled = editing && order.isNotEmpty()) { editing = false }
    val editHint = stringResource(R.string.home_edit_hint)
    LaunchedEffect(editing) { if (editing) snackbar.showSnackbar(editHint) }

    val homeBackground = LocalPageBackground.current ?: rememberPageBackground()
    CompositionLocalProvider(LocalPageBackground provides homeBackground) {
        val scrollBehavior = rememberTopBarScrollBehavior()
        val topBarBackdrop = rememberTopBarBackdrop()

        Scaffold(
            topBar = {
                BlurredTopBar(topBarBackdrop, scrollBehavior) {
                    TopAppBar(
                        color = topBarColor(topBarBackdrop),
                        title = if (editing) {
                            stringResource(R.string.home_edit_title)
                        } else {
                            homeTitleText(stringResource(R.string.app_name))
                        },
                        scrollBehavior = scrollBehavior,
                        actions = {
                            AnimatedContent(
                                targetState = editing,
                                transitionSpec = { ZeroMotion.fadeThrough(clip = false) },
                                contentAlignment = Alignment.CenterEnd,
                                label = "homeActions",
                            ) { inEditMode ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (inEditMode) {
                                        TextButton(
                                            text = stringResource(R.string.done),
                                            onClick = { editing = false },
                                            enabled = order.isNotEmpty(),
                                            variant = ButtonVariant.Primary,
                                            minWidth = 64.dp,
                                            minHeight = 34.dp,
                                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                                            modifier = Modifier.padding(end = 12.dp),
                                        )
                                    } else {
                                        if (hasUpdate) {
                                            val updateText = stringResource(R.string.update_available)
                                            TooltipBox(text = updateText) {
                                                IconButton(onClick = onUpdateClick) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_alert_circle),
                                                        contentDescription = updateText,
                                                        tint = MiuixTheme.colorScheme.error,
                                                    )
                                                }
                                            }
                                        }
                                        val settingsText = stringResource(R.string.app_settings)
                                        TooltipBox(text = settingsText) {
                                            IconButton(onClick = { onNavigate(AppRoute.AppSettings) }) {
                                                Icon(
                                                    imageVector = MiuixIcons.Settings,
                                                    contentDescription = settingsText,
                                                    tint = MiuixTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = bottomPadding)) },
        ) { innerPadding ->
            val renderCard: @Composable (HomeCard) -> Unit = { card ->
                when (card) {
                    HomeCard.Status -> StatusCard(
                        workState = state.workState,
                        device = state.device,
                        onStatusClick = { onNavigate(AppRoute.About) },
                        workingCardModifier = Modifier.excludeFromDragContainer(dragDropState),
                    )
                    HomeCard.Device -> DeviceCard(state.device)
                    HomeCard.Components -> ComponentsCard(
                        onOpen = { onNavigate(AppRoute.Components(GallerySection.Buttons)) },
                    )
                }
            }

            PullToRefresh(
                modifier = Modifier.topBarBlurSource(topBarBackdrop),
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                state = pullState,
                contentPadding = innerPadding,
                topBarScroll = scrollBehavior,
                refreshTexts = listOf(
                    stringResource(R.string.pull_to_refresh),
                    stringResource(R.string.release_to_refresh),
                    stringResource(R.string.refreshing),
                    stringResource(R.string.refresh_success),
                ),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .dragContainer(dragDropState, onLongPress = { editing = true }),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 6.dp,
                        bottom = innerPadding.calculateBottomPadding() + bottomPadding + 10.dp,
                    ),
                ) {
                    itemsIndexed(order, key = { _, card -> card.name }) { index, card ->
                        DraggableItem(dragDropState, index) { isDragging ->
                            HomeCardEditable(
                                editing = editing,
                                dragging = isDragging,
                                checked = true,
                                onToggle = {
                                    order.remove(card)
                                    if (card !in hidden) hidden.add(card)
                                    persist()
                                },
                                modifier = Modifier.padding(vertical = 6.dp),
                            ) { renderCard(card) }
                        }
                    }
                    if (editing && hidden.isNotEmpty()) {
                        item(key = "homeClosedTitle") {
                            SectionTitle(
                                text = stringResource(R.string.home_edit_closed_title),
                                modifier = fastItemPlacement(),
                            )
                        }
                        items(hidden, key = { it.name }) { card ->
                            HomeCardEditable(
                                editing = true,
                                dragging = false,
                                checked = false,
                                onToggle = {
                                    hidden.remove(card)
                                    if (card !in order) order.add(card)
                                    persist()
                                },
                                modifier = fastItemPlacement().padding(vertical = 6.dp),
                            ) { renderCard(card) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    workState: WorkState,
    device: DeviceInfo?,
    onStatusClick: () -> Unit,
    workingCardModifier: Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkingCard(
            state = workState,
            onClick = onStatusClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(workingCardModifier),
        )
        Column(modifier = Modifier.weight(1f)) {
            StatCard(
                label = stringResource(R.string.home_device_android),
                value = device?.androidVersion ?: "-",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            StatCard(
                label = stringResource(R.string.home_stat_api),
                value = device?.sdkLevel?.toString() ?: "-",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Large status card with crossfading states. */
@Composable
private fun WorkingCard(
    state: WorkState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetContainer = if (state == WorkState.Working) {
        okContainerColor
    } else {
        MiuixTheme.colorScheme.secondaryContainer
    }
    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(450),
        label = "container",
    )
    Card(
        modifier = modifier,
        color = container,
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedback.Tilt,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(38.dp, 45.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Crossfade(targetState = state, animationSpec = tween(450), label = "decor") { current ->
                    when (current) {
                        WorkState.Detecting -> Box(Modifier.size(170.dp))
                        WorkState.Working -> Icon(
                            painter = painterResource(R.drawable.ic_check_circle),
                            contentDescription = null,
                            tint = okAccentColor,
                            modifier = Modifier.size(170.dp),
                        )
                        WorkState.Idle -> Icon(
                            painter = painterResource(R.drawable.ic_x_circle),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.error,
                            modifier = Modifier.size(170.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Crossfade(targetState = state, animationSpec = tween(450), label = "text") { current ->
                    Column {
                        Text(
                            text = when (current) {
                                WorkState.Detecting -> stringResource(R.string.home_status_detecting)
                                WorkState.Working -> stringResource(R.string.home_status_working)
                                WorkState.Idle -> stringResource(R.string.home_status_idle)
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when (current) {
                                WorkState.Detecting -> stringResource(R.string.home_status_detecting_sub)
                                WorkState.Working -> stringResource(R.string.home_status_working_sub)
                                WorkState.Idle -> stringResource(R.string.home_status_idle_sub)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        pressFeedbackType = PressFeedback.Sink,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            AnimatedContent(
                targetState = value,
                transitionSpec = { ZeroMotion.textTicker() },
                label = "statValue",
            ) { current ->
                Text(
                    text = current,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo?) {
    ListCard {
        AnimatedContent(
            targetState = device,
            transitionSpec = { ZeroMotion.fadeThrough() },
            contentKey = { it != null },
            label = "deviceRow",
        ) { current ->
            if (current == null) {
                ListRow(
                    title = stringResource(R.string.home_device_loading),
                    endActions = { LoadingIndicator() },
                )
            } else {
                ListRow(
                    title = stringResource(R.string.home_device_model),
                    startAction = { PreferenceIcon(SettingIcons.about) },
                    summary = current.model,
                )
            }
        }
    }
}

@Composable
private fun ComponentsCard(onOpen: () -> Unit) {
    ListCard {
        ArrowPreference(
            title = stringResource(R.string.tab_components),
            startAction = { PreferenceIcon(SettingIcons.layers) },
            summary = stringResource(R.string.gallery_buttons_summary),
            onClick = onOpen,
        )
    }
}
