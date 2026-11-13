package com.roottap.clicker

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroui.kit.components.HighRefreshRateEffect
import com.zeroui.kit.components.LocalRefreshRatePreference
import com.zeroui.kit.theme.ZeroTheme
import com.roottap.clicker.data.HomeBackground
import com.roottap.clicker.data.LocaleHelper
import com.roottap.clicker.data.toThemeSettings
import com.roottap.clicker.feature.settings.AppController
import com.roottap.clicker.feature.settings.LocalAppController
import com.roottap.clicker.feature.settings.LocalAppearanceExtras
import com.roottap.clicker.feature.settings.LocalMonetEnabled
import com.roottap.clicker.feature.settings.languageOf
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.Shortcuts
import com.roottap.clicker.service.ClickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens the UI even when the launch would normally go straight to the overlay. */
fun showUiIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.ExtraShowUi, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** Applies a plan coming from a pinned shortcut and starts clicking right away. */
private fun MainActivity.runPlanFrom(intent: Intent?): Boolean {
    val planId = Shortcuts.planIdOf(intent)
    if (planId == 0L) return false
    val plan = ClickerStore.data.value.plans.firstOrNull { it.id == planId } ?: return false
    ClickerStore.applyPlan(plan)
    ClickerService.start(this)
    val screen = ClickerRuntime.screenSize(this)
    ClickerStore.normalizeTo(screen.x, screen.y)
    ClickerRuntime.engine.startProfile({ ClickerStore.data.value.profile }, screen.x, screen.y)
    moveTaskToBack(true)
    return true
}

/** Single activity host. */
class MainActivity : ComponentActivity() {
    // The locale has to be in place before the activity loads any resource, and this
    // is the earliest hook there is. Compose is not running yet, hence languageOf.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, languageOf(newBase)))
    }

    companion object {
        const val ExtraShowUi = "show_ui"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (runPlanFrom(intent)) return
        if (shouldSkipUi(intent)) moveTaskToBack(true)
    }

    /**
     * Everything is already granted: opening the app should only put the floating bar on screen,
     * not a page the user has to leave. The overlay has an explicit "open app" action for the UI.
     */
    private fun shouldSkipUi(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(ExtraShowUi, false) == true) return false
        if (Shortcuts.planIdOf(intent) != 0L) return false
        val settings = (application as ZeroApp).container.settings.settings.value
        if (!settings.welcomeCompleted) return false
        if (!Settings.canDrawOverlays(this)) return false
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!notifications) return false
        return ClickerStore.data.value.options.lastRootOk
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A pinned shortcut starts clicking without waiting for the UI.
        if (runPlanFrom(intent)) return
        if (shouldSkipUi(intent)) {
            ClickerService.start(this)
            moveTaskToBack(true)
            return
        }
        val repository = (application as ZeroApp).container.settings
        setContent {
            val settingsState = repository.settings.collectAsStateWithLifecycle()
            val settings = settingsState.value
            val backgroundSeed = remember { mutableIntStateOf(0) }
            // Decodes the image, so it stays off the main thread; the theme starts on seed 0
            // and switches once the value arrives.
            LaunchedEffect(settings.homeBackground) {
                backgroundSeed.intValue = withContext(Dispatchers.IO) {
                    HomeBackground.cachedSeed(this@MainActivity, settings.homeBackground)
                }
            }
            HighRefreshRateEffect(settings.extras.refreshRate)
            val controller = remember(repository) {
                AppController(
                    settingsState = settingsState,
                    backgroundSeedState = backgroundSeed,
                    update = repository::update,
                    reset = repository::resetAppearance,
                )
            }
            ZeroTheme(settings.toThemeSettings(backgroundSeed.intValue)) {
                CompositionLocalProvider(
                    LocalAppController provides controller,
                    LocalAppearanceExtras provides settings.extras,
                    LocalMonetEnabled provides settings.monet,
                    LocalRefreshRatePreference provides settings.extras.refreshRate,
                ) {
                    AppRoot(
                        welcomeCompleted = settings.welcomeCompleted,
                        pageTransition = settings.extras.pageTransition,
                    )
                }
            }
        }
    }
}
