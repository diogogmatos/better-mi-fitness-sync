package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.ActiveCaloriesSample
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
import com.bettermifitness.sync.data.time.resolveOffsetSecondsFromMiUnits
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * Pure JSON builders for Google Health API data points (no platform deps).
 * Builds the REST `DataPoint` body for each Mi metric so writes can be attempted
 * against `POST /users/me/dataTypes/{type}/dataPoints`.
 *
 * Only `exercise`, `sleep`, `weight`, and `body-fat` are currently writable;
 * the rest are produced anyway so they become writable automatically if Google
 * enables them. Int64 values are serialized as strings (API requirement).
 */
object GoogleHealthPayloads {

    const val STEP_HOUR_SECONDS = 3599L

    /** Kebab-case Google Health data type id for a metric key, or null if no mapping exists. */
    fun dataTypeFor(metricKey: String): String? = when (metricKey) {
        "workouts" -> "exercise"
        "sleep" -> "sleep"
        "weight" -> "weight"
        "heart_rate" -> "heart-rate"
        "hrv" -> "heart-rate-variability"
        "steps" -> "steps"
        "distance" -> "distance"
        "active_calories" -> "active-energy-burned"
        "spo2" -> "oxygen-saturation"
        "temperature" -> "core-body-temperature"
        "vo2_max" -> "vo2-max"
        // "resting_heart_rate" (daily type) and "blood_pressure" have no writable data type.
        else -> null
    }

    fun timestamp(epochSeconds: Long): String =
        Instant.fromEpochSeconds(epochSeconds).toString()

    fun offset(tzIn15Min: Int?): String =
        "${resolveOffsetSecondsFromMiUnits(tzIn15Min)}s"

    private fun sampleTime(epochSeconds: Long, tzIn15Min: Int?): JsonObject = buildJsonObject {
        put("physicalTime", timestamp(epochSeconds))
        put("utcOffset", offset(tzIn15Min))
    }

    private fun interval(
        start: Long,
        end: Long,
        startTz: Int?,
        endTz: Int?,
    ): JsonObject = buildJsonObject {
        put("startTime", timestamp(start))
        put("startUtcOffset", offset(startTz))
        put("endTime", timestamp(end))
        put("endUtcOffset", offset(endTz))
    }

    private fun dataPoint(field: String, payload: JsonObject): String =
        buildJsonObject { put(field, payload) }.toString()

    fun stepsBody(record: StepsRecord): String {
        val start = record.date.toLong()
        return dataPoint(
            "steps",
            buildJsonObject {
                put("interval", interval(start, start + STEP_HOUR_SECONDS, record.tzIn15Min, record.tzIn15Min))
                put("count", record.steps.toString())
            },
        )
    }

    fun heartRateBody(sample: HeartRateSample): String = dataPoint(
        "heartRate",
        buildJsonObject {
            put("sampleTime", sampleTime(sample.timestamp, sample.tzIn15Min))
            put("beatsPerMinute", sample.bpm.toString())
        },
    )

    fun hrvBody(sample: HrvSample): String = dataPoint(
        "heartRateVariability",
        buildJsonObject {
            put("sampleTime", sampleTime(sample.timestamp, sample.tzIn15Min))
            put("standardDeviationMilliseconds", sample.hrvMs)
        },
    )

    fun distanceBody(sample: DistanceSample): String = dataPoint(
        "distance",
        buildJsonObject {
            put("interval", interval(sample.startTime, sample.endTime, sample.tzIn15Min, sample.tzIn15Min))
            put("millimeters", (sample.meters * 1000.0).toLong().toString())
        },
    )

    fun activeCaloriesBody(sample: ActiveCaloriesSample): String = dataPoint(
        "activeEnergyBurned",
        buildJsonObject {
            put("interval", interval(sample.startTime, sample.endTime, sample.tzIn15Min, sample.tzIn15Min))
            put("kcal", sample.kilocalories)
        },
    )

    fun spo2Body(sample: SpO2Sample): String = dataPoint(
        "oxygenSaturation",
        buildJsonObject {
            put("sampleTime", sampleTime(sample.timestamp, sample.tzIn15Min))
            put("percentage", sample.percentage.toDouble())
        },
    )

    fun temperatureBody(sample: TemperatureSample): String {
        val celsius = sample.bodyCelsius ?: sample.skinCelsius ?: return ""
        return dataPoint(
            "coreBodyTemperature",
            buildJsonObject {
                put("sampleTime", sampleTime(sample.timestamp, sample.tzIn15Min))
                put("temperatureCelsius", celsius)
            },
        )
    }

    fun vo2MaxBody(sample: Vo2MaxSample): String = dataPoint(
        "vo2Max",
        buildJsonObject {
            put("sampleTime", sampleTime(sample.timestamp, sample.tzIn15Min))
            put("vo2Max", sample.mlPerKgMin)
        },
    )

    fun weightBody(measurement: WeightMeasurement): String = dataPoint(
        "weight",
        buildJsonObject {
            put("sampleTime", sampleTime(measurement.timestamp, measurement.tzIn15Min))
            put("weightGrams", measurement.weightKg * 1000.0)
        },
    )

    fun bodyFatBody(measurement: WeightMeasurement): String? {
        val fat = measurement.bodyFatPercent ?: return null
        return dataPoint(
            "bodyFat",
            buildJsonObject {
                put("sampleTime", sampleTime(measurement.timestamp, measurement.tzIn15Min))
                put("percentage", fat)
            },
        )
    }

    fun sleepBody(session: SleepSession): String {
        val stages = session.stages.mapNotNull { stage ->
            val type = when (HealthDataNormalizer.miSleepStageKind(stage.stage)) {
                HealthDataNormalizer.MiSleepStageKind.AWAKE -> "AWAKE"
                HealthDataNormalizer.MiSleepStageKind.DEEP -> "DEEP"
                HealthDataNormalizer.MiSleepStageKind.LIGHT -> "LIGHT"
                HealthDataNormalizer.MiSleepStageKind.REM -> "REM"
                HealthDataNormalizer.MiSleepStageKind.UNKNOWN -> return@mapNotNull null
            }
            buildJsonObject {
                put("startTime", timestamp(stage.startTime))
                put("startUtcOffset", offset(session.tzIn15Min))
                put("endTime", timestamp(stage.endTime))
                put("endUtcOffset", offset(session.tzIn15Min))
                put("type", type)
            }
        }
        return dataPoint(
            "sleep",
            buildJsonObject {
                put("interval", interval(session.startTime, session.endTime, session.tzIn15Min, session.tzIn15Min))
                put("type", "STAGES")
                put("stages", JsonArray(stages))
            },
        )
    }

    fun exerciseBody(session: WorkoutSession): String {
        val summary = buildJsonObject {
            session.distanceMeters?.let { put("distanceMillimeters", it * 1000.0) }
            session.caloriesKcal?.let { put("caloriesKcal", it) }
            session.totalSteps?.let { put("steps", it.toString()) }
            session.avgHeartRateBpm?.let { put("averageHeartRateBeatsPerMinute", it.toString()) }
        }
        val payload = buildJsonObject {
            put("interval", interval(session.startTime, session.endTime, session.tzIn15Min, session.tzIn15Min))
            exerciseType(session.activityType)?.let { put("exerciseType", it) }
            put("displayName", SportTypeMapper.displayTitle(session.activityType))
            put("activeDuration", "${(session.endTime - session.startTime).coerceAtLeast(1)}s")
            put("metricsSummary", summary)
            put("exerciseMetadata", buildJsonObject { put("hasGps", session.route.isNotEmpty()) })
        }
        return dataPoint("exercise", payload)
    }

    private fun exerciseType(activityType: String): String? {
        val key = SportTypeMapper.normalizeKey(activityType)
        return when {
            key.contains("hik") -> "HIKING"
            key.contains("run") || key.contains("jog") || key.contains("treadmill") -> "RUNNING"
            key.contains("walk") -> "WALKING"
            key.contains("cycl") || key.contains("bik") || key.contains("rid") -> "BIKING"
            key.contains("swim") -> "SWIMMING"
            key.contains("yoga") -> "YOGA"
            key.contains("pilates") -> "PILATES"
            else -> null
        }
    }
}
