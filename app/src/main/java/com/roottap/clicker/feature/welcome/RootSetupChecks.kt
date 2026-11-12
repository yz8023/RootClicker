package com.roottap.clicker.feature.welcome

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Welcome page checks for this app: root access, overlay permission and notifications.
 * None of them blocks the flow — the clicker simply explains what is still missing.
 */
class RootSetupChecks(private val context: Context) : SetupChecks {

    override suspend fun checkAccess(): Boolean = withContext(Dispatchers.IO) {
        val granted = ClickerRuntime.checkRoot()
        granted
    }

    override fun isSatisfied(id: SetupItemId): Boolean = when (id) {
        SetupItemId.SystemVersion -> ClickerRuntime.rootStatus.value == RootStatus.GRANTED
        SetupItemId.VisualEffects -> Settings.canDrawOverlays(context)
        SetupItemId.Notifications ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
