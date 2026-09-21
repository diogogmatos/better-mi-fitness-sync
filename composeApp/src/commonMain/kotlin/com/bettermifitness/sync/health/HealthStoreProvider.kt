package com.bettermifitness.sync.health

import kotlinx.coroutines.flow.StateFlow

/**
 * Resolves the currently-selected health destination and its [HealthStore].
 * Decouples sync policy/UI from any single platform store so the app can offer
 * more than one sync destination (e.g. Apple Health + Google Health on iOS).
 */
interface HealthStoreProvider {
    /** Destinations the user can choose from on this platform. */
    val supportedDestinations: List<SyncDestination>

    /** Currently-selected destination (falls back to the platform default). */
    val activeDestination: StateFlow<SyncDestination>

    /** [HealthStore] for the currently-selected destination. */
    val activeStore: StateFlow<HealthStore>
}
