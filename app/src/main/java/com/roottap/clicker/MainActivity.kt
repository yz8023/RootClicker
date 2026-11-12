package com.roottap.clicker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Single activity host. */
class MainActivity : ComponentActivity() {
    // The locale has to be in place before the activity loads any resource, and this
    // is the earliest hook there is. Compose is not running yet, hence languageOf.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, languageOf(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
