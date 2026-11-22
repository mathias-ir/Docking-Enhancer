package com.odininputmirror.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import com.odininputmirror.domain.repository.DockStateRepository
import com.odininputmirror.domain.repository.InputDeviceRepository
import com.odininputmirror.domain.repository.MappingRepository
import com.odininputmirror.domain.repository.MirrorProcessRepository
import com.odininputmirror.domain.repository.MirrorSettingsRepository
import com.odininputmirror.domain.usecase.GetConnectedDevicesUseCase
import com.odininputmirror.domain.usecase.GetMirrorStatusUseCase
import com.odininputmirror.domain.usecase.ResolveAutoMirrorDecisionUseCase
import com.odininputmirror.domain.usecase.SetAutoMirrorEnabledUseCase
import com.odininputmirror.domain.usecase.SetComboHoldKillAppEnabledUseCase
import com.odininputmirror.domain.usecase.SetHomeAsBackEnabledUseCase
import com.odininputmirror.domain.usecase.SetManualExternalControllerUseCase
import com.odininputmirror.domain.usecase.SetManualInternalControllerUseCase
import com.odininputmirror.domain.usecase.SetMirrorWhenUndockedUseCase
import com.odininputmirror.domain.usecase.SetVirtualMouseEnabledUseCase
import com.odininputmirror.domain.usecase.StartMirrorUseCase
import com.odininputmirror.domain.usecase.StopMirrorUseCase

class InputMirrorGraph private constructor(context: Context, forceDockMode: Boolean) {
    private val appContext = context.applicationContext

    // The mirror runs entirely through the stock firmware's PServerBinder service (no root). On a
    // device that doesn't ship it, [isSupportedDevice] is false and the app surfaces that instead
    // of running.
    private val shell: MirrorShell = PServerShell(appContext)

    /** True when this device ships the PServerBinder service the mirror needs. */
    val isSupportedDevice: Boolean get() = shell.isAvailable

    /** True when the firmware publishes PServerBinder but it does not answer (Odin 2 Mini). */
    val isServiceUnresponsive: Boolean get() = shell.isRegisteredButUnresponsive

    val settingsRepository: MirrorSettingsRepository = AndroidMirrorSettingsRepository(appContext)
    val dockStateRepository: DockStateRepository = AndroidDisplayDockStateRepository(appContext, forceDockMode)
    val processRepository: MirrorProcessRepository = RootMirrorProcessRepository(
        mirrorSettingsRepository = settingsRepository,
        files = InputMirrorFiles(appContext),
        shell = shell,
    )
    val mappingRepository: MappingRepository = AndroidMappingRepository(appContext)
    val inputDeviceRepository: InputDeviceRepository = AndroidInputDeviceRepository(
        shell = shell,
        manualInternalGuidProvider = { settingsRepository.getSettings().manualInternalGuid },
        hiddenSourcePathProvider = { settingsRepository.getSettings().source },
        mirrorRunningProvider = { processRepository.isRunning() },
    )

    // First-time defaults from the bundled SDL_GameControllerDB (see third-party/gamecontrollerdb).
    // The asset is re-read per seeding because seeding happens at most once per new controller.
    internal val defaultMappingSeeder = DefaultMappingSeeder(
        databaseLines = {
            appContext.assets.open("gamecontrollerdb.txt").bufferedReader().readLines().asSequence()
        },
        devices = inputDeviceRepository,
        mappings = mappingRepository,
    )

    /** Seed a known controller's default mapping if the user has never mapped it. Safe to repeat. */
    fun seedDefaultMapping(key: com.odininputmirror.domain.model.MappingKey) {
        runCatching { defaultMappingSeeder.seedIfEmpty(key) }
    }

    private val usbPadDriverBinder = UsbPadDriverBinder(shell)

    /**
     * Lets the kernel drive USB pads it would otherwise leave without a driver (see
     * [UsbPadDriverBinder]). Privileged and blocking the first time, a no-op after that.
     */
    fun bindUnknownUsbPads() {
        runCatching { usbPadDriverBinder.ensureApplied() }
    }

    private val externalDisplayColorFix = ExternalDisplayColorFix(shell)

    /**
     * Keeps the external display out of the washed-out wide-gamut mode while docked and restores the
     * framework's own colour setting on undock (see [ExternalDisplayColorFix]). Acts on changes only.
     * Reads the real displays even in forced dock mode: with no monitor there is nothing to fix.
     */
    fun syncExternalDisplayColor() {
        runCatching {
            val externals = appContext.getSystemService(DisplayManager::class.java).displays
                .filter { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
                .mapTo(HashSet()) { it.displayId }
            externalDisplayColorFix.sync(externals)
        }
    }

    /** The firmware's "handheld screen off while docked" option (see [HandheldScreenSwitch]). */
    val handheldScreen = HandheldScreenSwitch(
        readSetting = { Settings.System.getString(appContext.contentResolver, HandheldScreenSwitch.KEY) },
        isVendorSettingsInstalled = {
            runCatching {
                appContext.packageManager.getPackageInfo(HandheldScreenSwitch.VENDOR_SETTINGS_PACKAGE, 0)
            }.isSuccess
        },
        shell = shell,
    )

    /** The built-in screen's refresh rate (see [BuiltInRefreshRate]). */
    val builtInRefreshRate = BuiltInRefreshRate(
        panelRates = {
            appContext.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
                ?.supportedModes
                ?.map { it.refreshRate }
                .orEmpty()
        },
        readPeak = { Settings.System.getString(appContext.contentResolver, BuiltInRefreshRate.PEAK) },
        shell = shell,
    )

    private fun builtInDisplay(): Display? =
        appContext.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    /** Android's own render resolution, full or 720p (see [RenderResolution]). */
    val renderResolution = RenderResolution(
        physicalSize = { builtInDisplay()?.mode?.let { it.physicalWidth to it.physicalHeight } },
        logicalSize = {
            builtInDisplay()?.let { display ->
                android.graphics.Point().also { @Suppress("DEPRECATION") display.getRealSize(it) }
                    .let { it.x to it.y }
            }
        },
        shell = shell,
    )

    val getConnectedDevices = GetConnectedDevicesUseCase(inputDeviceRepository)
    val startMirror = StartMirrorUseCase(processRepository, settingsRepository)
    val stopMirror = StopMirrorUseCase(processRepository, settingsRepository)
    val getMirrorStatus = GetMirrorStatusUseCase(processRepository, settingsRepository, dockStateRepository)
    val setHomeAsBackEnabled = SetHomeAsBackEnabledUseCase(settingsRepository)
    val setComboHoldKillAppEnabled = SetComboHoldKillAppEnabledUseCase(settingsRepository)
    val setVirtualMouseEnabled = SetVirtualMouseEnabledUseCase(settingsRepository)
    val setAutoMirrorEnabled = SetAutoMirrorEnabledUseCase(settingsRepository)
    val setMirrorWhenUndocked = SetMirrorWhenUndockedUseCase(settingsRepository)
    val setManualInternalController = SetManualInternalControllerUseCase(settingsRepository)
    val setManualExternalController = SetManualExternalControllerUseCase(settingsRepository)
    val resolveAutoMirrorDecision = ResolveAutoMirrorDecisionUseCase()

    companion object {
        @Volatile
        private var shared: InputMirrorGraph? = null

        /**
         * The one graph for this process.
         *
         * The supervisor service and the screen both need it, and building two was not free: each
         * carried its own [PServerExec] — hence its own cached binder and its own "does this device
         * answer" verdict, which costs a blocking transact to establish — and its own
         * [DefaultMappingSeeder], whose cache of pads the database does not know could then disagree
         * with the other's. One graph pays each of those once.
         *
         * [forceDockMode] is honoured from whoever builds it first. That is not a race in practice:
         * both callers pass the same build flag, and the screen already depends on agreeing with the
         * supervisor about whether a dock is present.
         */
        fun of(context: Context, forceDockMode: Boolean): InputMirrorGraph =
            shared ?: synchronized(this) {
                shared ?: InputMirrorGraph(context, forceDockMode).also { shared = it }
            }
    }
}

/**
 * True when this device ships the PServerBinder service the mirror needs (no root).
 *
 * Answering costs a real transact the first time, so it goes through the shared [PServerExec] and is
 * cached from then on. It also BLOCKS, which is why nothing on the main thread should ask: the
 * supervisor establishes this on its worker and the screen on an IO dispatcher.
 */
fun isPServerSupported(): Boolean = PServerExec.shared.isAvailable
