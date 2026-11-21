package com.odininputmirror.data

/**
 * Teaches the kernel's `xpad` driver the USB ids of pads it already speaks to but does not know.
 *
 * Handheld kernels are old (the Retroid Pocket 5 runs 4.19), and `xpad` only learned many third-party
 * Xbox-protocol ids years later. Such a pad enumerates over USB, offers a perfectly ordinary Xbox 360
 * interface, and gets no driver — so no input device exists, and neither Android nor the mirror can
 * see it. The 8BitDo Ultimate receiver behind a dock's hub is the case that found this.
 *
 * The kernel takes extra ids at runtime through the driver's `new_id`, and PServer's root may write
 * it. Writing an id probes any unbound device that matches straight away and binds any plugged in
 * later, so it is done once per process, without waiting for the pad: the ids live until reboot, and
 * a reboot restarts this process too. Re-writing one the kernel already holds fails harmlessly
 * (`EEXIST`), as does the whole write on a kernel without `xpad`.
 */
internal class UsbPadDriverBinder(
    private val shell: MirrorShell,
    private val ids: List<UsbId> = KNOWN_XPAD_IDS,
) {
    @Volatile
    private var applied = false

    /** Writes the ids unless that already happened in this process. Cheap to call every tick. */
    fun ensureApplied() {
        if (applied || ids.isEmpty()) {
            return
        }
        // Retried on the next tick only when the command certainly did not run. PServer reports no
        // exit code, so a delivered write that the kernel refused is not retried — repeating it
        // every tick would not make it succeed.
        applied = shell.exec(command(ids))
    }

    data class UsbId(val vendor: Int, val product: Int)

    companion object {
        private const val NEW_ID = "/sys/bus/usb/drivers/xpad/new_id"

        /** Xbox-protocol pads newer kernels' `xpad` binds and old handheld kernels do not. */
        val KNOWN_XPAD_IDS = listOf(
            // 8BitDo Ultimate Wireless receiver (and Pro 2 Wired) in Xbox mode.
            UsbId(0x2dc8, 0x3106),
        )

        /**
         * One write per id, each in its own subshell so a refused one (already known, or no `xpad`
         * on this kernel) neither stops the rest nor spills an error anywhere.
         */
        internal fun command(ids: List<UsbId>): String =
            ids.joinToString("; ") { "(echo %04x %04x > $NEW_ID) 2>/dev/null".format(it.vendor, it.product) }
    }
}
