package com.odininputmirror

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.odininputmirror.data.HandheldScreenSwitch
import com.odininputmirror.data.InputMirrorGraph
import java.util.concurrent.Executors

/**
 * Quick Settings tile for the firmware's "turn off the handheld screen while docked" option.
 *
 * Active means the handheld screen stays on. A tap flips the vendor's own setting (see
 * [HandheldScreenSwitch]), so the change applies at once and the firmware's menu agrees with it.
 * While the shade is open the tile follows the setting, whoever changes it.
 */
class HandheldScreenTileService : TileService() {
    private val graph by lazy { InputMirrorGraph.of(this, BuildConfig.FORCE_DOCK_MODE_FOR_DEV) }
    private val main = Handler(Looper.getMainLooper())

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    override fun onStartListening() {
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(HandheldScreenSwitch.KEY),
            false,
            observer,
        )
        refresh()
    }

    override fun onStopListening() {
        contentResolver.unregisterContentObserver(observer)
    }

    override fun onClick() {
        val tile = qsTile ?: return
        if (tile.state == Tile.STATE_UNAVAILABLE) return
        val show = tile.state != Tile.STATE_ACTIVE
        // Optimistic: the shade should answer the tap now, not after a binder round trip. The
        // observer (or the refresh below, if PServer never got it) corrects it.
        render(show)
        worker.execute {
            graph.handheldScreen.setShownWhenDocked(show)
            refresh()
        }
    }

    private fun refresh() {
        worker.execute {
            val shown = graph.handheldScreen.isShownWhenDocked()
            main.post { render(shown) }
        }
    }

    private fun render(shown: Boolean?) {
        val tile = qsTile ?: return
        tile.state = when (shown) {
            null -> Tile.STATE_UNAVAILABLE
            true -> Tile.STATE_ACTIVE
            false -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when (shown) {
                    null -> R.string.tile_handheld_screen_unsupported
                    true -> R.string.tile_handheld_screen_on
                    false -> R.string.tile_handheld_screen_off
                },
            )
        }
        tile.updateTile()
    }

    private companion object {
        // One thread for every instance: taps are serialised, so a quick double tap cannot land its
        // two writes out of order.
        val worker = Executors.newSingleThreadExecutor()
    }
}
