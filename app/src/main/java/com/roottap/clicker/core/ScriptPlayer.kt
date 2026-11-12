package com.roottap.clicker.core

import android.content.Context

/** Thin wrapper so script playback can be started from the UI, the overlay and the service. */
object ScriptPlayer {

    fun play(context: Context, script: RecordedScript): Boolean {
        if (script.actions.isEmpty()) return false
        val options = ClickerStore.data.value.options
        val size = ClickerRuntime.screenSize(context)
        ClickerRuntime.engine.startScript(script, options.scriptSpeed, options.scriptRepeat, size.x, size.y)
        return true
    }

    fun stop() {
        ClickerRuntime.engine.stop("已停止")
    }

    val isPlaying: Boolean get() = ClickerRuntime.engine.state.value.playingScript
}
