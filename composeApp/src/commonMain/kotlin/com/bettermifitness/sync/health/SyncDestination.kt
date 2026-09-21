package com.bettermifitness.sync.health

/**
 * A health platform the app can sync Mi Fitness data into.
 * iOS exposes [APPLE_HEALTH] and [GOOGLE_HEALTH]; Android exposes [HEALTH_CONNECT].
 */
enum class SyncDestination(val key: String) {
    HEALTH_CONNECT("health_connect"),
    APPLE_HEALTH("apple_health"),
    GOOGLE_HEALTH("google_health"),
    ;

    companion object {
        fun fromKey(key: String?): SyncDestination? =
            entries.firstOrNull { it.key == key }
    }
}
