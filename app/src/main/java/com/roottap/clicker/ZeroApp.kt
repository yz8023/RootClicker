package com.roottap.clicker

import android.app.Application
import com.roottap.clicker.core.AppContainer
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.data.CrashLogger

/** Application entry that owns the dependency container. */
class ZeroApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        ClickerRuntime.init(this)
        CrashLogger.install(this, container.settings.settings.value.crashLog)
    }
}
