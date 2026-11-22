package com.odininputmirror

import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Display
import com.odininputmirror.data.InputMirrorGraph
import java.util.concurrent.Executors

/**
 * Quick Settings tile that switches Android between the panel's full resolution and 720p. See
 * [com.odininputmirror.data.RenderResolution] — this changes what Android renders at, not the signal
 * a monitor receives.
 */
class RenderResolutionTileService : TileService() {
    private val graph by lazy { InputMirrorGraph.of(this, BuildConfig.FORCE_DOCK_MODE_FOR_DEV) }
    private val main = Handler(Looper.getMainLooper())

    // The override changes the built-in display's size, which DisplayManager announces.
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) refresh()
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    override fun onStartListening() {
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, main)
        refresh()
    }

    override fun onStopListening() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
    }

    override fun onClick() {
        if (qsTile?.state == Tile.STATE_UNAVAILABLE) return
        worker.execute {
            val resolution = graph.renderResolution
            resolution.setReduced(!resolution.isReduced())
            refresh()
        }
    }

    private fun refresh() {
        worker.execute {
            val resolution = graph.renderResolution
            val supported = resolution.isSupported()
            val lines = resolution.currentLines()
            main.post { render(supported, lines) }
        }
    }

    private fun render(supported: Boolean, lines: Int?) {
        val tile = qsTile ?: return
        tile.state = if (supported) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (supported && lines != null) {
                getString(R.string.tile_resolution_value, lines)
            } else {
                getString(R.string.tile_resolution_unsupported)
            }
        }
        tile.updateTile()
    }

    private companion object {
        // Serialised so a quick double tap toggles from the first tap's result, not alongside it.
        val worker = Executors.newSingleThreadExecutor()
    }
}
