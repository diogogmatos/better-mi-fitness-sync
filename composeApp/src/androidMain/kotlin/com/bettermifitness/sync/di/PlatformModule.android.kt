package com.bettermifitness.sync.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.bettermifitness.sync.health.HealthStore
import com.bettermifitness.sync.health.HealthStoreProvider
import com.bettermifitness.sync.health.HealthWriter
import com.bettermifitness.sync.health.SyncDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

private lateinit var appContext: Context

/** Must be called before [initKoin] on Android. */
fun provideAndroidContext(context: Context) {
    appContext = context.applicationContext
}

/** Application context set by [provideAndroidContext]. */
fun androidAppContext(): Context = appContext

actual fun platformModule(): Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                appContext.filesDir
                    .resolve("mi_fitness_prefs.preferences_pb")
                    .absolutePath
                    .toPath()
            },
        )
    }
    single { HealthWriter(appContext) }
    single<HealthStoreProvider> {
        AndroidHealthStoreProvider(get())
    }
}

/** Android has a single destination (Health Connect). */
private class AndroidHealthStoreProvider(
    private val healthConnect: HealthStore,
) : HealthStoreProvider {
    private val destination = MutableStateFlow(SyncDestination.HEALTH_CONNECT)
    private val store = MutableStateFlow(healthConnect)

    override val supportedDestinations: List<SyncDestination> = listOf(SyncDestination.HEALTH_CONNECT)
    override val activeDestination: StateFlow<SyncDestination> = destination
    override val activeStore: StateFlow<HealthStore> = store
}
