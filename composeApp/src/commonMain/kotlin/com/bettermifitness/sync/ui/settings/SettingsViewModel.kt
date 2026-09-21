package com.bettermifitness.sync.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bettermifitness.sync.i18n.L10n
import com.bettermifitness.sync.AutoSyncPlatform
import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.preferences.TokenStore
import com.bettermifitness.sync.health.HealthReadiness
import com.bettermifitness.sync.health.HealthStore
import com.bettermifitness.sync.health.HealthStoreProvider
import com.bettermifitness.sync.health.SyncDestination
import com.bettermifitness.sync.sync.SyncOutcomeLabels
import com.bettermifitness.sync.util.RelativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    /**
     * Real enabled set from DataStore once [prefsReady] is true.
     * Do **not** default to all-on — that flashes wrong toggles before prefs load.
     */
    val enabledMetrics: Set<String> = emptySet(),
    /** False until the first DataStore emission for metrics / range / auto-sync. */
    val prefsReady: Boolean = false,
    val rangeDays: Int = 7,
    val autoSync: Boolean = false,
    val lastBackgroundSyncLabel: String = L10n.text(L10n.homeNever),
    val lastBackgroundStatusTitle: String = L10n.text(L10n.outcomeNotSynced),
    val lastBackgroundDetail: String = L10n.text(L10n.outcomeIdleDetail),
    val lastBackgroundIsError: Boolean = false,
    val lastSyncLabel: String = L10n.text(L10n.homeNever),
    val lastSyncStatusTitle: String = L10n.text(L10n.outcomeNotSynced),
    val lastSyncDetail: String = L10n.text(L10n.outcomeIdleDetail),
    val lastSyncIsError: Boolean = false,
    val lastSyncIsWarning: Boolean = false,
    val bgRefreshLabel: String = "",
    val canTestBgRefresh: Boolean = false,
    val bgTestStatus: String? = null,
    val bgTestRunning: Boolean = false,
    val showShortcutsHelp: Boolean = false,
    val showBackgroundRefreshDetails: Boolean = true,
    val healthServiceName: String = "",
    val healthStatusTitle: String = "",
    val healthStatusDetail: String = "",
    val healthNeedsAction: Boolean = false,
    /** Currently-selected sync destination (null until resolved). */
    val syncDestination: SyncDestination? = null,
    /** Destinations the user can pick from on this platform. */
    val supportedDestinations: List<SyncDestination> = emptyList(),
    val loggedOut: Boolean = false,
)

class SettingsViewModel(
    private val syncPreferences: SyncPreferences,
    private val healthProvider: HealthStoreProvider,
    private val tokenStore: TokenStore,
    private val session: MiSessionManager,
) : ViewModel() {

    private val healthStore: HealthStore get() = healthProvider.activeStore.value

    private val showShortcutsHelp = AutoSyncPlatform.supportsShortcutsHelp()

    private val _local = MutableStateFlow(
        LocalSettingsState(
            bgRefreshLabel = AutoSyncPlatform.backgroundRefreshStatusLabel(),
            canTestBgRefresh = AutoSyncPlatform.supportsOpportunisticRefreshTest(),
            loggedOut = false,
        ),
    )
    private val _health = MutableStateFlow(
        HealthReadiness(
            available = true,
            permissionsGranted = true,
            serviceName = healthStore.healthServiceName(),
            hint = null,
        ),
    )

    private val lastSyncBundle = combine(
        syncPreferences.lastSyncTime,
        syncPreferences.lastSyncStatus,
        syncPreferences.lastSyncMessage,
    ) { t, s, m -> Triple(t, s, m) }

    private val lastBgBundle = combine(
        syncPreferences.lastBackgroundSyncTime,
        syncPreferences.lastBackgroundSyncStatus,
        syncPreferences.lastBackgroundSyncMessage,
    ) { t, s, m -> Triple(t, s, m) }

    private val configBundle = combine(
        syncPreferences.enabledMetrics,
        syncPreferences.syncRangeDays,
        syncPreferences.autoSync,
    ) { e, r, a -> Triple(e, r, a) }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(configBundle, lastSyncBundle, lastBgBundle) { c, ls, bg -> Triple(c, ls, bg) },
        _local,
        _health,
        healthProvider.activeDestination,
    ) { bundle, local, health, destination ->
        val config = bundle.first
        val lastSync = bundle.second
        val lastBg = bundle.third
        SettingsUiState(
            enabledMetrics = config.first,
            prefsReady = true,
            rangeDays = config.second,
            autoSync = config.third,
            lastBackgroundSyncLabel = RelativeTime.format(lastBg.first),
            lastBackgroundStatusTitle = SyncOutcomeLabels.title(lastBg.second),
            lastBackgroundDetail = SyncOutcomeLabels.detail(lastBg.second, lastBg.third),
            lastBackgroundIsError = SyncOutcomeLabels.isError(lastBg.second),
            lastSyncLabel = RelativeTime.format(lastSync.first),
            lastSyncStatusTitle = SyncOutcomeLabels.title(lastSync.second),
            lastSyncDetail = SyncOutcomeLabels.detail(lastSync.second, lastSync.third),
            lastSyncIsError = SyncOutcomeLabels.isError(lastSync.second),
            lastSyncIsWarning = SyncOutcomeLabels.isWarning(lastSync.second),
            bgRefreshLabel = local.bgRefreshLabel,
            canTestBgRefresh = local.canTestBgRefresh,
            bgTestStatus = local.bgTestStatus,
            bgTestRunning = local.bgTestRunning,
            showShortcutsHelp = showShortcutsHelp,
            showBackgroundRefreshDetails = true,
            healthServiceName = health.serviceName,
            healthStatusTitle = health.statusTitle,
            healthStatusDetail = health.statusDetail,
            healthNeedsAction = !health.isReady,
            syncDestination = destination,
            supportedDestinations = healthProvider.supportedDestinations,
            loggedOut = local.loggedOut,
        )
    }.stateIn(
        scope = viewModelScope,
        // Eager so prefs load as soon as Settings opens (avoids all-on placeholder flash).
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(
            prefsReady = false,
            bgRefreshLabel = _local.value.bgRefreshLabel,
            canTestBgRefresh = _local.value.canTestBgRefresh,
            showShortcutsHelp = showShortcutsHelp,
            healthServiceName = healthStore.healthServiceName(),
            syncDestination = healthProvider.activeDestination.value,
            supportedDestinations = healthProvider.supportedDestinations,
        ),
    )

    init {
        refreshHealth()
        viewModelScope.launch {
            healthProvider.activeDestination.collect { refreshHealth() }
        }
    }

    fun refreshHealth() {
        viewModelScope.launch {
            try {
                _health.value = healthStore.readiness()
            } catch (_: Exception) {
                _health.value = HealthReadiness(
                    available = false,
                    permissionsGranted = false,
                    serviceName = healthStore.healthServiceName(),
                    hint = L10n.text(L10n.healthStatusCheckFailed),
                )
            }
        }
    }

    fun openHealthService() {
        viewModelScope.launch {
            try {
                healthStore.requestPermissions()
            } catch (_: Exception) {
                // Denied / failed — open Settings or Health Connect below.
            }
            if (!healthStore.hasWritePermissions()) {
                healthStore.openHealthService()
            }
            refreshHealth()
        }
    }

    fun setMetricEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch {
            syncPreferences.setMetricEnabled(key, enabled)
        }
    }

    fun setSyncDestination(destination: SyncDestination) {
        viewModelScope.launch {
            syncPreferences.setSyncDestination(destination.key)
        }
    }

    fun setSyncRangeDays(days: Int) {
        viewModelScope.launch {
            syncPreferences.setSyncRangeDays(days)
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            syncPreferences.setAutoSync(enabled)
            if (enabled) {
                AutoSyncPlatform.scheduleBackgroundRefresh()
            } else {
                AutoSyncPlatform.cancelBackgroundRefresh()
            }
        }
    }

    fun runBackgroundRefreshTest() {
        if (_local.value.bgTestRunning) return
        if (!uiState.value.autoSync) return

        _local.update {
            it.copy(bgTestRunning = true, bgTestStatus = L10n.text(L10n.settingsRunningRefresh))
        }
        AutoSyncPlatform.runOpportunisticRefreshTest { status ->
            viewModelScope.launch {
                _local.update {
                    it.copy(
                        bgTestRunning = false,
                        bgTestStatus = mapBgTestStatus(status),
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenStore.clear()
            session.clear()
            _local.update { it.copy(loggedOut = true) }
        }
    }

    fun consumeLoggedOut() {
        _local.update { it.copy(loggedOut = false) }
    }

    private fun mapBgTestStatus(status: String): String = when (status) {
        "success" -> L10n.text(L10n.settingsTestOk)
        "partial_success" -> L10n.text(L10n.settingsPartialOk)
        "skipped" -> L10n.text(L10n.settingsSkipped)
        "not_logged_in" -> L10n.text(L10n.settingsNotSignedIn)
        "cancelled" -> L10n.text(L10n.settingsCancelled)
        else -> L10n.textFmt(L10n.settingsFailedStatus, status)
    }

    private data class LocalSettingsState(
        val bgRefreshLabel: String = "",
        val canTestBgRefresh: Boolean = false,
        val bgTestStatus: String? = null,
        val bgTestRunning: Boolean = false,
        val loggedOut: Boolean = false,
    )
}
