package com.odininputmirror.domain.usecase

import com.odininputmirror.domain.repository.MirrorSettingsRepository

class SetHomeAsBackEnabledUseCase(
    private val mirrorSettingsRepository: MirrorSettingsRepository,
) {
    operator fun invoke(enabled: Boolean) = mirrorSettingsRepository.setHomeAsBackEnabled(enabled)
}

class SetComboHoldKillAppEnabledUseCase(
    private val mirrorSettingsRepository: MirrorSettingsRepository,
) {
    operator fun invoke(enabled: Boolean) = mirrorSettingsRepository.setComboHoldKillAppEnabled(enabled)
}

// Not a daemon option: it only changes whether the supervisor keeps a mirror running undocked, so
// it does not advance the config generation.
class SetMirrorWhenUndockedUseCase(
    private val mirrorSettingsRepository: MirrorSettingsRepository,
) {
    operator fun invoke(enabled: Boolean) = mirrorSettingsRepository.setMirrorWhenUndocked(enabled)
}

class SetVirtualMouseEnabledUseCase(
    private val mirrorSettingsRepository: MirrorSettingsRepository,
) {
    operator fun invoke(enabled: Boolean) = mirrorSettingsRepository.setVirtualMouseEnabled(enabled)
}
