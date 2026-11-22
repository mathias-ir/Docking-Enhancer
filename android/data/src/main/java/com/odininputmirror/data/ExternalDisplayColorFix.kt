package com.odininputmirror.data

/**
 * Stops SurfaceFlinger from washing out the external display while docked.
 *
 * The Retroid Pocket 5's framework forces a wide-gamut composition mode (`persist.sys.sf.color_mode`
 * = 9, Display P3) for its built-in AMOLED. The built-in panel only offers NATIVE, so it never takes
 * effect there — but a DisplayPort monitor that advertises BT.2020 does, and SurfaceFlinger then
 * drives it in DISPLAY_BT2020: every sRGB frame is re-encoded into BT.2020 primaries, which an SDR
 * monitor shows as plain sRGB. The result is the faded, desaturated picture every docked RP5 gets.
 *
 * Setting the property would not stick: system_server rewrites it from a framework resource on every
 * boot. So this asks SurfaceFlinger directly for unmanaged colour (NATIVE, pass-through) while docked,
 * and on undock hands it back exactly what system_server last asked for — the same two values it
 * keeps in those properties — so the built-in screen is never left altered on any device.
 *
 * It keys on WHICH external displays are attached, not merely whether one is: Android gives a
 * reconnected monitor a new display id, and a replug quicker than a supervisor tick would otherwise
 * look like nothing happened while the reconnect put the managed setting back.
 */
internal class ExternalDisplayColorFix(private val shell: MirrorShell) {
    // The external displays the current SurfaceFlinger setting was last applied for; null until the
    // first sync, so a process that died mid-dock still restores on its first undocked tick.
    @Volatile
    private var appliedFor: Set<Int>? = null

    // Re-apply once more on the tick after a new dock: the framework finishes setting a reconnected
    // display up after it is announced, and may restore the managed setting while doing so.
    @Volatile
    private var confirmPending = false

    /** Applies or restores when the attached external displays change. Cheap to call every tick. */
    fun sync(externalDisplays: Set<Int>) {
        if (appliedFor == externalDisplays) {
            if (confirmPending && externalDisplays.isNotEmpty() && shell.exec(UNMANAGED)) {
                confirmPending = false
            }
            return
        }
        val docked = externalDisplays.isNotEmpty()
        // Delivery only (PServer has no exit code): retried next tick only if it certainly did not run.
        if (shell.exec(if (docked) UNMANAGED else RESTORE)) {
            appliedFor = externalDisplays
            confirmPending = docked
        }
    }

    companion object {
        // SurfaceFlinger's "set display color setting" debug transaction, the one system_server's
        // DisplayTransformManager uses: (DisplayColorSetting, forced composition ColorMode).
        private const val SET_COLOR = "service call SurfaceFlinger 1023"

        /** kUnmanaged, no forced mode: every display in NATIVE, frames sent as they are. */
        internal const val UNMANAGED = "$SET_COLOR i32 1 i32 0 >/dev/null 2>&1"

        /** What system_server applied before us, read back from the properties it maintains. */
        internal const val RESTORE =
            "n=\$(getprop persist.sys.sf.native_mode); c=\$(getprop persist.sys.sf.color_mode); " +
                "$SET_COLOR i32 \${n:-0} i32 \${c:-0} >/dev/null 2>&1"
    }
}
