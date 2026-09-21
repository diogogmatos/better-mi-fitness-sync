package com.bettermifitness.sync.di

import com.bettermifitness.sync.data.MiRegionDiscovery
import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.preferences.CredentialsPort
import com.bettermifitness.sync.data.preferences.CredentialsStore
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.preferences.SyncPreferencesPort
import com.bettermifitness.sync.data.preferences.SyncSessionPort
import com.bettermifitness.sync.data.preferences.TokenStore
import com.bettermifitness.sync.data.repository.HealthRepository
import com.bettermifitness.sync.data.repository.HealthSyncRunner
import com.bettermifitness.sync.health.HealthStoreProvider
import com.bettermifitness.sync.sync.SyncCoordinator
import com.bettermifitness.sync.ui.home.HomeViewModel
import com.bettermifitness.sync.ui.login.LoginViewModel
import com.bettermifitness.sync.ui.settings.SettingsViewModel
import com.bettermifitness.sync.ui.sync.SyncViewModel
import com.mifitness.miclient.auth.MiAuth
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Shared Koin graph for Android and iOS.
 * Platform-only factories (DataStore path, HealthWriter instance) live in [platformModule].
 */
fun commonAppModule(): Module = module {
    single { CredentialsStore(get()) }
    single<CredentialsPort> { get<CredentialsStore>() }
    single { SyncPreferences(get()) }
    single<SyncPreferencesPort> { get<SyncPreferences>() }
    single { TokenStore(get(), get(), get()) }

    single { MiAuth() }
    single { MiSessionManager(credentialsStore = get(), miAuth = get()) }
    single<SyncSessionPort> { get<MiSessionManager>() }
    single { MiRegionDiscovery() }

    // Platform module registers the HealthStoreProvider (HealthWriter + GoogleHealth).

    single { HealthRepository(get(), get<HealthStoreProvider>()) }
    single<HealthSyncRunner> { get<HealthRepository>() }
    single {
        SyncCoordinator(
            session = get<SyncSessionPort>(),
            credentials = get<CredentialsPort>(),
            syncPreferences = get<SyncPreferencesPort>(),
            healthProvider = get<HealthStoreProvider>(),
            repository = get<HealthSyncRunner>(),
        )
    }

    factory {
        LoginViewModel(
            miAuth = get(),
            sessionManager = get(),
            credentialsStore = get(),
            regionDiscovery = get(),
        )
    }
    factory {
        HomeViewModel(
            session = get(),
            tokenStore = get(),
            healthProvider = get(),
            syncCoordinator = get(),
        )
    }
    factory {
        SyncViewModel(
            repository = get(),
            healthProvider = get(),
            syncPreferences = get(),
            syncCoordinator = get(),
        )
    }
    factory {
        SettingsViewModel(
            syncPreferences = get(),
            healthProvider = get(),
            tokenStore = get(),
            session = get(),
        )
    }
}
