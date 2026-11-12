package com.roottap.clicker.service

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.roottap.clicker.R
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.ScriptPlayer

/**
 * Quick Settings tile: start / stop clicking without opening the app or the floating bar.
 * It shows up in the shade once the user adds it from the tile editor.
 */
class ClickerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        ClickerRuntime.init(applicationContext)
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        ClickerRuntime.init(applicationContext)
        val engine = ClickerRuntime.engine
        if (engine.isRunning) {
            ScriptPlayer.stop()
            engine.stop("已从快捷设置停止")
        } else {
            val size = ClickerRuntime.screenSize(this)
            val profile = ClickerStore.data.value.profile
            if (profile.points.isEmpty()) {
                // Nothing to click yet: open the app so the user can pick points.
                openApp()
                return
            }
            ClickerStore.normalizeTo(size.x, size.y)
            ClickerService.start(this)
            engine.startProfile({ ClickerStore.data.value.profile }, size.x, size.y)
        }
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val running = runCatching { ClickerRuntime.engine.isRunning }.getOrDefault(false)
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (running) "连点中" else "连点器"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_click)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .setComponent(ComponentName(packageName, "$packageName.MainActivity"))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivityAndCollapse(intent) }
    }
}
