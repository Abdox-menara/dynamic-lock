package com.example.dynamiclock.locker

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick-settings tile to toggle app-lock protection on/off. */
class LockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (LockManager.isEnabled(this)) AppLockService.stop(this) else AppLockService.start(this)
        updateTile()
    }

    private fun updateTile() {
        val tile: Tile = qsTile ?: return
        val on = LockManager.isEnabled(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Dynamic Lock"
        tile.updateTile()
    }
}
