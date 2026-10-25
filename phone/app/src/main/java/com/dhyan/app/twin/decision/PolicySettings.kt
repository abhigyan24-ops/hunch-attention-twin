package com.dhyan.app.twin.decision

import java.util.Collections

/** Immutable snapshot. Runtime validation belongs in the fail-open pipeline, not this constructor. */
class PolicySettings(
    val mode: PolicyMode = PolicyMode.OFF,
    val killSwitch: Boolean = false,
    val kappaDPerHour: Double = PolicyDefaults.KAPPA_D_PER_HOUR,
    val explorationEnabled: Boolean = false,
    val epsilon: Double = PolicyDefaults.EPSILON,
    val strictSession: Boolean = false,
    allowListPackages: Set<String> = emptySet(),
    val seed: Long = 1L
) {
    val allowListPackages: Set<String> = Collections.unmodifiableSet(LinkedHashSet(allowListPackages))
}
