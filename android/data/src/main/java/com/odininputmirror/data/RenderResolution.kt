package com.odininputmirror.data

/**
 * Runs Android itself at 720p instead of the panel's full resolution, one tap each way.
 *
 * This is the render resolution, not the signal sent to a monitor: on the Retroid Pocket 5 the
 * DisplayPort output stays at 1080p whatever Android runs at (Android 13 will not re-mode an external
 * display while it is attached, and the kernel has no debugfs to force one), and the mirrored picture
 * is scaled up to it. What it buys is fewer pixels for apps and games that draw at screen size.
 *
 * It is Android's own display-size override (`wm size`), with the density scaled by the same factor
 * so the interface keeps its proportions instead of shrinking. Both persist across reboots until
 * reset, as they do when set from adb.
 */
class RenderResolution internal constructor(
    /** The built-in panel's physical size in its natural orientation, e.g. 1080 x 1920. */
    private val physicalSize: () -> Pair<Int, Int>?,
    /** The size Android currently lays out at, same orientation (the override when one is set). */
    private val logicalSize: () -> Pair<Int, Int>?,
    private val shell: MirrorShell,
) {
    /** The short side Android runs at (e.g. 1080 or 720); null when the display is unknown. */
    fun currentLines(): Int? = logicalSize()?.let { (w, h) -> minOf(w, h) }

    /** Whether reducing makes sense: the panel's short side must be above [REDUCED_LINES]. */
    fun isSupported(): Boolean = physicalSize()?.let { (w, h) -> minOf(w, h) > REDUCED_LINES } ?: false

    fun isReduced(): Boolean {
        val physical = physicalSize() ?: return false
        val current = currentLines() ?: return false
        return current < minOf(physical.first, physical.second)
    }

    /** Privileged and blocking. True when the change was delivered to PServer. */
    fun setReduced(reduced: Boolean): Boolean {
        if (!reduced) return shell.exec(RESET)
        val (w, h) = physicalSize() ?: return false
        return shell.exec(reduceCommand(w, h))
    }

    companion object {
        internal const val REDUCED_LINES = 720

        internal const val RESET = "wm size reset; wm density reset"

        /**
         * Size scaled so the short side is 720 (aspect kept, rounded to even), and the density scaled
         * by the same factor from the PHYSICAL density `wm density` reports, never the current one —
         * reapplying must not compound.
         */
        internal fun reduceCommand(physicalWidth: Int, physicalHeight: Int): String {
            val short = minOf(physicalWidth, physicalHeight)
            fun scaled(side: Int) = (side * REDUCED_LINES / short) and 1.inv()
            return "wm size ${scaled(physicalWidth)}x${scaled(physicalHeight)}; " +
                "d=\$(wm density | sed -n 's/^Physical density: //p'); " +
                "[ -n \"\$d\" ] && wm density \$((d * $REDUCED_LINES / $short))"
        }
    }
}
