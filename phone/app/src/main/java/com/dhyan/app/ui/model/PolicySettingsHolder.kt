package com.dhyan.app.ui.model

import com.dhyan.app.twin.decision.PolicyDefaults
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.decision.PolicySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe holder for the current [PolicySettings].
 * Allows atomic swap with zero-blocking reads on the decision/actuation hot path.
 */
object PolicySettingsHolder {
    private val currentRef = AtomicReference(PolicySettings())
    private val _settingsFlow = MutableStateFlow(currentRef.get())
    val settingsFlow: StateFlow<PolicySettings> = _settingsFlow.asStateFlow()

    fun get(): PolicySettings = currentRef.get()

    fun update(transform: (PolicySettings) -> PolicySettings) {
        val updated = transform(currentRef.get())
        currentRef.set(updated)
        _settingsFlow.value = updated
    }

    fun set(newSettings: PolicySettings) {
        currentRef.set(newSettings)
        _settingsFlow.value = newSettings
    }

    fun setMode(mode: PolicyMode) {
        update { current ->
            PolicySettings(
                mode = mode,
                killSwitch = current.killSwitch,
                kappaDPerHour = current.kappaDPerHour,
                explorationEnabled = current.explorationEnabled,
                epsilon = current.epsilon,
                strictSession = current.strictSession,
                allowListPackages = current.allowListPackages,
                seed = current.seed,
                digestIntervalMin = current.digestIntervalMin
            )
        }
    }

    fun setKillSwitch(active: Boolean) {
        update { current ->
            PolicySettings(
                mode = current.mode,
                killSwitch = active,
                kappaDPerHour = current.kappaDPerHour,
                explorationEnabled = current.explorationEnabled,
                epsilon = current.epsilon,
                strictSession = current.strictSession,
                allowListPackages = current.allowListPackages,
                seed = current.seed,
                digestIntervalMin = current.digestIntervalMin
            )
        }
    }
}
