package com.roottap.clicker.core

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roottap.clicker.ZeroApp
import com.roottap.clicker.data.AndroidDeviceInfoProvider
import com.roottap.clicker.data.DeviceInfoProvider
import com.roottap.clicker.data.SettingsRepository
import com.roottap.clicker.data.SharedPreferencesSettingsStorage
import com.roottap.clicker.feature.welcome.RootSetupChecks
import com.roottap.clicker.feature.welcome.SetupChecks

/** Application-wide dependencies. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings: SettingsRepository by lazy {
        SettingsRepository(SharedPreferencesSettingsStorage(appContext)) {
            Log.w("Settings", "Stored settings were unreadable; defaults loaded", it)
        }
    }
    val deviceInfo: DeviceInfoProvider by lazy { AndroidDeviceInfoProvider() }
    val setupChecks: SetupChecks by lazy { RootSetupChecks(appContext) }
}

/** Returns the [VM] of the current store owner, creating it from the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: AppContainer.() -> VM): VM {
    val container = (LocalContext.current.applicationContext as ZeroApp).container
    return viewModel<VM>(
        factory = viewModelFactory {
            initializer { container.create() }
        },
    )
}
