package com.bettermifitness.sync.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.bettermifitness.sync.data.preferences.SyncPreferencesPort
import com.bettermifitness.sync.health.GoogleHealthApiClient
import com.bettermifitness.sync.health.GoogleHealthStore
import com.bettermifitness.sync.health.GoogleHealthTokenStore
import com.bettermifitness.sync.health.HealthStore
import com.bettermifitness.sync.health.HealthStoreProvider
import com.bettermifitness.sync.health.HealthWriter
import com.bettermifitness.sync.health.SyncDestination
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun platformModule(): Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                val directory = NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null,
                )!!
                "${directory.path}/mi_fitness_prefs.preferences_pb".toPath()
            },
        )
    }
    single { HealthWriter() }
    single { GoogleHealthTokenStore(get()) }
    single { GoogleHealthApiClient(HttpClient(Darwin), get()) }
    single { GoogleHealthStore(get()) }
    single<HealthStoreProvider> {
        IosHealthStoreProvider(
            syncPreferences = get(),
            apple = get<HealthWriter>(),
            google = get<GoogleHealthStore>(),
        )
    }
}

/** iOS exposes two destinations: Apple Health (default) and Google Health. */
private class IosHealthStoreProvider(
    private val syncPreferences: SyncPreferencesPort,
    apple: HealthStore,
    google: HealthStore,
) : HealthStoreProvider {
    private val stores = mapOf(
        SyncDestination.APPLE_HEALTH to apple,
        SyncDestination.GOOGLE_HEALTH to google,
    )

    override val supportedDestinations: List<SyncDestination> =
        listOf(SyncDestination.APPLE_HEALTH, SyncDestination.GOOGLE_HEALTH)

    private val destination = MutableStateFlow(SyncDestination.APPLE_HEALTH)
    private val store = MutableStateFlow(apple)

    override val activeDestination: StateFlow<SyncDestination> = destination
    override val activeStore: StateFlow<HealthStore> = store

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            syncPreferences.syncDestination.collect { key ->
                val resolved = SyncDestination.fromKey(key) ?: SyncDestination.APPLE_HEALTH
                val target = stores[resolved] ?: apple
                destination.value = resolved
                store.value = target
            }
        }
    }
}
