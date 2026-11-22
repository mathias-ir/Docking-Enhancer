package com.odininputmirror

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.odininputmirror.data.InputMirrorGraph
import java.util.concurrent.Executors

/**
 * Quick Settings tile that steps the built-in screen through its panel's refresh rates (60 / 120 Hz
 * on a Retroid Pocket 5 with the newer panel). See [com.odininputmirror.data.BuiltInRefreshRate].
 */
class RefreshRateTileService : TileService() {
    private val graph by lazy { InputMirrorGraph.of(this, BuildConfig.FORCE_DOCK_MODE_FOR_DEV) }
    private val main = Handler(Looper.getMainLooper())

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    override fun onStartListening() {
        contentResolver.registerContentObserver(Settings.System.getUriFor(PEAK_KEY), false, observer)
        refresh()
    }

    override fun onStopListening() {
        contentResolver.unregisterContentObserver(observer)
    }

    override fun onClick() {
        if (qsTile?.state == Tile.STATE_UNAVAILABLE) return
        worker.execute {
            val rate = graph.builtInRefreshRate
            val next = rate.next() ?: return@execute
            // Answer the tap at once; the observer confirms (or the refresh below corrects) it.
            main.post { render(next, available = true) }
            rate.set(next)
            refresh()
        }
    }

    private fun refresh() {
        worker.execute {
            val rate = graph.builtInRefreshRate
            val available = rate.supportedRates().size >= 2
            val current = rate.current()
            main.post { render(current, available) }
        }
    }

    private fun render(rate: Int?, available: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (available) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !available -> getString(R.string.tile_refresh_rate_unsupported)
                rate == null -> getString(R.string.tile_refresh_rate_auto)
                else -> getString(R.string.tile_refresh_rate_value, rate)
            }
        }
        tile.updateTile()
    }

    private companion object {
        const val PEAK_KEY = "peak_refresh_rate"

        // Serialised so a quick double tap reads the first tap's result before computing its own.
        val worker = Executors.newSingleThreadExecutor()
    }
}
