package com.odininputmirror.data

import kotlin.math.roundToInt

/**
 * The built-in screen's refresh rate, one tap per step through the rates its panel offers.
 *
 * Pins Android's own `min_refresh_rate` and `peak_refresh_rate` to the same value — what the Retroid
 * settings app writes for its refresh option — so the firmware's menu shows the same answer, and the
 * display framework switches the panel mode immediately. Those keys are hidden from apps for writing,
 * so the write goes through PServer.
 */
class BuiltInRefreshRate internal constructor(
    private val panelRates: () -> List<Float>,
    private val readPeak: () -> String?,
    private val shell: MirrorShell,
) {
    /** Distinct whole-number rates the panel supports, ascending (e.g. [60, 120]). */
    fun supportedRates(): List<Int> = rates(panelRates())

    /** The rate the screen is pinned to, or null when nothing pins it (the panel's own choice). */
    fun current(): Int? {
        val raw = runCatching(readPeak).getOrElse { shell.read("settings get system $PEAK") }
        return raw?.trim()?.toFloatOrNull()?.takeIf { it > 0f }?.roundToInt()
    }

    /** The rate a tap moves to: the next supported one, wrapping round. Null with fewer than two. */
    fun next(): Int? = next(supportedRates(), current())

    /** Privileged and blocking. True when the change was delivered to PServer. */
    fun set(rate: Int): Boolean = shell.exec(command(rate))

    companion object {
        internal const val PEAK = "peak_refresh_rate"
        private const val MIN = "min_refresh_rate"

        // Panel modes report rates like 60.000004 and 120.00001.
        internal fun rates(raw: List<Float>): List<Int> = raw.map { it.roundToInt() }.distinct().sorted()

        internal fun next(supported: List<Int>, current: Int?): Int? {
            if (supported.size < 2) return null
            val at = supported.indexOf(current)
            // Unpinned (or pinned to something the panel lacks): start from the top, the likelier ask.
            return if (at < 0) supported.last() else supported[(at + 1) % supported.size]
        }

        internal fun command(rate: Int): String =
            "settings put system $MIN $rate.0; settings put system $PEAK $rate.0"
    }
}
