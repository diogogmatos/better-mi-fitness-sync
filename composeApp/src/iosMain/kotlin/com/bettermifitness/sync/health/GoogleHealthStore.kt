package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.ActiveCaloriesSample
import com.bettermifitness.sync.data.api.BloodPressureSample
import com.bettermifitness.sync.data.api.DistanceSample
import com.bettermifitness.sync.data.api.HeartRateSample
import com.bettermifitness.sync.data.api.HrvSample
import com.bettermifitness.sync.data.api.SleepSession
import com.bettermifitness.sync.data.api.SpO2Sample
import com.bettermifitness.sync.data.api.StepsRecord
import com.bettermifitness.sync.data.api.TemperatureSample
import com.bettermifitness.sync.data.api.Vo2MaxSample
import com.bettermifitness.sync.data.api.WeightMeasurement
import com.bettermifitness.sync.data.api.WorkoutSession
import com.bettermifitness.sync.i18n.L10n

/**
 * Google Health write destination. Implements [HealthStore] so the shared sync
 * pipeline can target it. Only workouts / sleep / weight / body-fat are writable
 * today; other metrics are attempted and surface [MetricUnsupportedException].
 */
class GoogleHealthStore(
    private val api: GoogleHealthApiClient,
) : HealthStore {

    override suspend fun writeHeartRate(samples: List<HeartRateSample>) {
        val clean = HealthDataNormalizer.normalizeHeartRate(samples)
        if (clean.isEmpty()) return
        postAll("heart-rate", clean.map { GoogleHealthPayloads.heartRateBody(it) })
    }

    override suspend fun writeRestingHeartRate(samples: List<HeartRateSample>) {
        if (samples.isEmpty()) return
        throw MetricUnsupportedException("resting_heart_rate")
    }

    override suspend fun writeSleep(sessions: List<SleepSession>) {
        val clean = HealthDataNormalizer.normalizeSleep(sessions)
        if (clean.isEmpty()) return
        postAll("sleep", clean.map { GoogleHealthPayloads.sleepBody(it) })
    }

    override suspend fun writeSteps(records: List<StepsRecord>) {
        val clean = HealthDataNormalizer.normalizeSteps(records)
        if (clean.isEmpty()) return
        postAll("steps", clean.map { GoogleHealthPayloads.stepsBody(it) })
    }

    override suspend fun writeDistance(samples: List<DistanceSample>) {
        val clean = HealthDataNormalizer.normalizeDistance(samples)
        if (clean.isEmpty()) return
        postAll("distance", clean.map { GoogleHealthPayloads.distanceBody(it) })
    }

    override suspend fun writeActiveCalories(samples: List<ActiveCaloriesSample>) {
        val clean = HealthDataNormalizer.normalizeActiveCalories(samples)
        if (clean.isEmpty()) return
        postAll("active-energy-burned", clean.map { GoogleHealthPayloads.activeCaloriesBody(it) })
    }

    override suspend fun writeSpO2(samples: List<SpO2Sample>) {
        val clean = HealthDataNormalizer.normalizeSpO2(samples)
        if (clean.isEmpty()) return
        postAll("oxygen-saturation", clean.map { GoogleHealthPayloads.spo2Body(it) })
    }

    override suspend fun writeWeight(measurements: List<WeightMeasurement>) {
        val clean = HealthDataNormalizer.normalizeWeight(measurements)
        if (clean.isEmpty()) return
        postAll("weight", clean.map { GoogleHealthPayloads.weightBody(it) })
        val fat = clean.mapNotNull { GoogleHealthPayloads.bodyFatBody(it) }
        if (fat.isNotEmpty()) postAll("body-fat", fat)
    }

    override suspend fun writeWorkouts(sessions: List<WorkoutSession>) {
        if (sessions.isEmpty()) return
        postAll("exercise", sessions.map { GoogleHealthPayloads.exerciseBody(it) })
    }

    override suspend fun writeBloodPressure(samples: List<BloodPressureSample>) {
        if (samples.isEmpty()) return
        throw MetricUnsupportedException("blood_pressure")
    }

    override suspend fun writeTemperature(samples: List<TemperatureSample>) {
        val clean = HealthDataNormalizer.normalizeTemperature(samples)
        if (clean.isEmpty()) return
        postAll(
            "core-body-temperature",
            clean.mapNotNull { GoogleHealthPayloads.temperatureBody(it).takeIf(String::isNotEmpty) },
        )
    }

    override suspend fun writeVo2Max(samples: List<Vo2MaxSample>) {
        val clean = HealthDataNormalizer.normalizeVo2Max(samples)
        if (clean.isEmpty()) return
        postAll("vo2-max", clean.map { GoogleHealthPayloads.vo2MaxBody(it) })
    }

    override suspend fun writeHrv(samples: List<HrvSample>) {
        val clean = HealthDataNormalizer.normalizeHrv(samples)
        if (clean.isEmpty()) return
        postAll("heart-rate-variability", clean.map { GoogleHealthPayloads.hrvBody(it) })
    }

    override suspend fun readLatestWeight(): WeightMeasurement? = null

    override suspend fun isAvailable(): Boolean = api.isConfigured()

    override suspend fun hasWritePermissions(): Boolean = api.hasSession()

    override suspend fun requestPermissions() {
        api.authorize()
    }

    override fun healthServiceName(): String = L10n.text(L10n.destinationGoogleHealth)

    override suspend fun availabilityHint(): String? = when {
        !api.isConfigured() -> L10n.text(L10n.googleHealthNotConfigured)
        !api.hasSession() -> L10n.text(L10n.googleHealthSignIn)
        else -> null
    }

    override fun openHealthService() {
        // Re-run the Google sign-in prompt (foreground only).
    }

    private suspend fun postAll(dataType: String, bodies: List<String>) {
        for (body in bodies) {
            api.createDataPoint(dataType, body)
        }
    }
}
