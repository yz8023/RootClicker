package com.roottap.clicker.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roottap.clicker.data.DeviceInfo
import com.roottap.clicker.data.DeviceInfoProvider
import com.roottap.clicker.feature.welcome.SetupChecks
import com.roottap.clicker.feature.welcome.SetupItemId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Result of the status card. */
enum class WorkState { Detecting, Working, Idle }

/** State of the home page. */
@Immutable
data class HomeUiState(
    val workState: WorkState = WorkState.Detecting,
    val device: DeviceInfo? = null,
    val refreshing: Boolean = false,
)

/** Loads the data of the home page. */
class HomeViewModel(
    private val checks: SetupChecks,
    private val deviceInfo: DeviceInfoProvider,
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    private var loading: Job? = null

    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    init {
        load()
    }

    /** Reloads the page data for a pull to refresh. */
    fun refresh() {
        state.value = state.value.copy(refreshing = true)
        load()
    }

    private fun load() {
        // Two pulls in a row: the older load must not finish last and overwrite the newer.
        loading?.cancel()
        loading = viewModelScope.launch {
            val working = checks.checkAccess() && SetupItemId.entries.all { !it.required || checks.isSatisfied(it) }
            state.value = HomeUiState(
                workState = if (working) WorkState.Working else WorkState.Idle,
                device = deviceInfo.load(),
                refreshing = false,
            )
        }
    }
}
