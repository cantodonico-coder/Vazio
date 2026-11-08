package com.nicoshake.shortsautonext

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Botão nas Configurações Rápidas (puxar a barra de notificações) para ligar/desligar */
class ToggleTile : TileService() {
    override fun onStartListening() = paint()

    override fun onClick() {
        Prefs.setEnabled(this, !Prefs.enabled(this))
        paint()
    }

    private fun paint() {
        val t = qsTile ?: return
        t.state = if (Prefs.enabled(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Shorts Auto"
        t.updateTile()
    }
}
