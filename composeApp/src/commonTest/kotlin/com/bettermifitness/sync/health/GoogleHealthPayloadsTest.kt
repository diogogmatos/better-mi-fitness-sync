package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.StepsRecord
import com.bettermifitness.sync.data.api.WeightMeasurement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleHealthPayloadsTest {

    private val json = Json

    @Test
    fun dataTypeFor_mapsSupportedTypes() {
        assertEquals("exercise", GoogleHealthPayloads.dataTypeFor("workouts"))
        assertEquals("sleep", GoogleHealthPayloads.dataTypeFor("sleep"))
        assertEquals("weight", GoogleHealthPayloads.dataTypeFor("weight"))
        assertEquals("heart-rate", GoogleHealthPayloads.dataTypeFor("heart_rate"))
        assertEquals("steps", GoogleHealthPayloads.dataTypeFor("steps"))
        assertNull(GoogleHealthPayloads.dataTypeFor("blood_pressure"))
        assertNull(GoogleHealthPayloads.dataTypeFor("resting_heart_rate"))
    }

    @Test
    fun weightBody_usesGrams() {
        val body = GoogleHealthPayloads.weightBody(WeightMeasurement(timestamp = 0, weightKg = 62.5))
        val weight = json.parseToJsonElement(body).jsonObject["weight"]!!.jsonObject
        assertEquals(62500.0, weight["weightGrams"]!!.jsonPrimitive.content.toDouble())
    }

    @Test
    fun bodyFatBody_returnsNullWithoutFat() {
        val body = GoogleHealthPayloads.bodyFatBody(WeightMeasurement(timestamp = 0, weightKg = 62.5))
        assertNull(body)
    }

    @Test
    fun stepsBody_usesStringCount() {
        val body = GoogleHealthPayloads.stepsBody(StepsRecord(date = "0", steps = 42))
        val steps = json.parseToJsonElement(body).jsonObject["steps"]!!.jsonObject
        assertEquals("42", steps["count"]!!.jsonPrimitive.content)
    }

    @Test
    fun timestamp_isIsoUtc() {
        val stamp = GoogleHealthPayloads.timestamp(0)
        assertTrue(stamp.startsWith("1970-01-01"))
        assertTrue(stamp.endsWith("Z"))
    }
}
