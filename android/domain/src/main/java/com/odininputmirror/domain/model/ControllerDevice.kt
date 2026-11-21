package com.odininputmirror.domain.model

data class ControllerDevice(
    val name: String,
    val path: String,
    val guid: String,
    val controllerNumber: Int,
    val handlers: List<String> = emptyList(),
    // Effective internal flag used to pick the mirror target (signature, manual, or default).
    val isInternal: Boolean = false,
    // True only when matched by a recognised handheld signature (e.g. Odin); such a pick is
    // locked and not user-editable. Default/manual picks set isInternal but not this.
    val isKnownInternal: Boolean = false,
    // The /dev/input node the hide-external feature unlinks to remove THIS external controller from
    // the framework's device list — the Odin quirk (vendor 0x2020) node bearing this controller's
    // name. It may be a separate re-exposed twin OR the very node the mirror reads as source (when
    // the firmware exposes only the re-exposed node in /dev); either way it is the framework-visible
    // one. Null for the internal controller and when no such node exists.
    val hideNodePath: String? = null,
    // Identity of the REAL controller behind this entry, used to key a saved mapping. [guid] cannot
    // do that job for an external pad: the firmware republishes every one of them under its own
    // vendor:product, so they all share a guid. Null when no distinct real device was found — an
    // internal controller, or a pad the firmware did not republish.
    val mappingKey: MappingKey? = null,
    // The real pad to read instead of [path], when the firmware's twin cannot carry all of its axes.
    // Null whenever the twin is faithful, which keeps every pad that works through it on that path.
    val directSource: DirectSource? = null,
)

/**
 * A real controller the mirror reads directly, bypassing the firmware's twin.
 *
 * The twin has one fixed axis set, so a pad that reports axes outside it loses them there: an
 * Xbox-protocol pad's right stick (RX/RY) has no slot once its triggers have taken Z/RZ. The real
 * device still has them, but the firmware deleted its node, so the daemon opens it through /sys and
 * checks [vendorId]:[productId] before trusting what it opened.
 */
data class DirectSource(
    val path: String,
    val vendorId: Int,
    val productId: Int,
)
