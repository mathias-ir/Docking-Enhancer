package com.odininputmirror.data

/**
 * The firmware's own "turn the handheld screen off while docked" option, reachable in one tap.
 *
 * The Retroid Pocket 5 keeps it in `Settings.System` as [KEY] (1 = handheld screen off while an
 * external display is connected) and its settings app applies a change immediately, docked or not,
 * by cutting the panel backlight. Its menu sits three levels deep, so this flips the same value
 * instead of reimplementing the behaviour: the firmware's toggle and this one can never disagree.
 *
 * Reading is open to any app because the key is the vendor's own rather than one Android declares.
 * Writing is not — an app may only put public `Settings.System` keys — so it goes through PServer.
 */
class HandheldScreenSwitch internal constructor(
    private val readSetting: () -> String?,
    private val isVendorSettingsInstalled: () -> Boolean,
    private val shell: MirrorShell,
) {
    /**
     * Whether the handheld screen stays on while docked; null on a device without this option.
     * Cheap and non-privileged, but it touches the settings provider, so not on the main thread.
     */
    fun isShownWhenDocked(): Boolean? {
        val raw = runCatching(readSetting).getOrElse { shell.read("settings get system $KEY") }
        return parse(raw, isVendorSettingsInstalled())
    }

    /** Privileged and blocking. True when the change was delivered to PServer. */
    fun setShownWhenDocked(shown: Boolean): Boolean = shell.exec(command(shown))

    companion object {
        const val KEY = "close_screen_when_output_video"

        // A Retroid that never touched the option may not have written the key yet, so the vendor's
        // settings app being present is enough; on any other device an absent key means unsupported.
        internal fun parse(raw: String?, vendorSettingsInstalled: Boolean): Boolean? =
            when (raw?.trim()) {
                "1" -> false
                "0" -> true
                else -> if (vendorSettingsInstalled) true else null
            }

        internal fun command(shown: Boolean): String =
            "settings put system $KEY ${if (shown) 0 else 1} >/dev/null 2>&1"

        /** The package that owns [KEY]; also what the manifest's `<queries>` names. */
        const val VENDOR_SETTINGS_PACKAGE = "com.rp.settings"
    }
}
