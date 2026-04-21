package com.alleyz15.farmtwinai.data.environment

import com.alleyz15.farmtwinai.data.remote.platformHttpClientEngineFactory
import com.alleyz15.farmtwinai.domain.model.EnvironmentalContext
import com.alleyz15.farmtwinai.domain.model.FarmLocation
import com.alleyz15.farmtwinai.domain.model.WaterProductionRecord
import com.alleyz15.farmtwinai.domain.model.WeatherForecast
import com.alleyz15.farmtwinai.domain.model.WeatherWarning
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DataGovMyEnvironmentalRepository(
    private val client: HttpClient = HttpClient(platformHttpClientEngineFactory()),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EnvironmentalDataRepository {

    override suspend fun fetchWeatherForecasts(location: FarmLocation): List<WeatherForecast> {
        val payload = getJson(
            url = "${EnvironmentApiConfig.weatherBaseUrl}/forecast",
            query = buildMap {
                location.state.takeIf { it.isNotBlank() }?.let { put("contains", it) }
            },
        )

        val rows = payload["data"]?.jsonArray.orEmpty()
        return rows
            .mapNotNull { it as? JsonObject }
            .mapNotNull { parseWeatherForecast(it, location) }
    }

    override suspend fun fetchWeatherWarnings(): List<WeatherWarning> {
        val payload = getJson(url = "${EnvironmentApiConfig.weatherBaseUrl}/warning")
        val rows = payload["data"]?.jsonArray.orEmpty()
        return rows
            .mapNotNull { it as? JsonObject }
            .map { parseWeatherWarning(it) }
    }

    override suspend fun fetchWaterProduction(state: String?): List<WaterProductionRecord> {
        val csv = client.get(EnvironmentApiConfig.waterProductionCsvUrl).body<String>()
        val records = csv
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull(::parseWaterProductionRow)
            .toList()

        return if (state.isNullOrBlank()) {
            records
        } else {
            records.filter { it.state.equals(state, ignoreCase = true) }
        }
    }

    override suspend fun fetchEnvironmentalContext(location: FarmLocation): EnvironmentalContext = coroutineScope {
        val forecasts = async { fetchWeatherForecasts(location) }
        val warnings = async { fetchWeatherWarnings() }
        val water = async { fetchWaterProduction(location.state) }

        EnvironmentalContext(
            location = location,
            weatherForecasts = forecasts.await(),
            weatherWarnings = warnings.await(),
            waterProductionRecords = water.await(),
        )
    }

    private suspend fun getJson(
        url: String,
        query: Map<String, String> = emptyMap(),
    ): JsonObject {
        val response = client.get(url) {
            query.forEach { (key, value) -> parameter(key, value) }
        }
        check(response.status.isSuccess()) {
            "Request failed for $url with status ${response.status}"
        }
        return json.parseToJsonElement(response.body<String>()).jsonObject
    }

    private fun parseWeatherForecast(
        item: JsonObject,
        location: FarmLocation,
    ): WeatherForecast? {
        val summary = item.string("summary_forecast") ?: item.string("summary") ?: return null
        val district = item.string("district") ?: item.string("area")
        val town = item.string("location_name") ?: item.string("town")
        val locationName = listOfNotNull(town, district, item.string("state")).joinToString(", ").ifBlank {
            location.state
        }

        return WeatherForecast(
            locationId = item.string("location_id") ?: locationName.lowercase(),
            locationName = locationName,
            forecastDate = item.string("date") ?: item.string("forecast_date").orEmpty(),
            morningForecast = item.string("morning_forecast") ?: summary,
            afternoonForecast = item.string("afternoon_forecast") ?: summary,
            nightForecast = item.string("night_forecast") ?: summary,
            summaryForecast = summary,
            summaryWhen = item.string("summary_when") ?: "day",
            minTempCelsius = item.int("min_temp"),
            maxTempCelsius = item.int("max_temp"),
        ).takeIf {
            location.district.isNullOrBlank() || locationName.contains(location.district, ignoreCase = true)
        }
    }

    private fun parseWeatherWarning(item: JsonObject): WeatherWarning {
        val areas = when (val areaNode = item["area"]) {
            is JsonArray -> areaNode.mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonPrimitive -> areaNode.contentOrNull?.split(",")?.map { it.trim() }.orEmpty()
            else -> emptyList()
        }

        return WeatherWarning(
            headline = item.string("title") ?: item.string("headline") ?: "Weather warning",
            warningType = item.string("type"),
            level = item.string("level") ?: item.string("warning_level"),
            issuedAt = item.string("issued_at") ?: item.string("timestamp"),
            areas = areas,
            rawDescription = item.string("text") ?: item.string("description").orEmpty(),
        )
    }

    private fun parseWaterProductionRow(line: String): WaterProductionRecord? {
        val columns = parseCsvLine(line)
        if (columns.size < 3) return null

        return WaterProductionRecord(
            date = columns[0],
            state = columns[1],
            millionLitresPerDay = columns[2].toDoubleOrNull() ?: return null,
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false

        line.forEach { char ->
            when {
                char == '"' -> insideQuotes = !insideQuotes
                char == ',' && !insideQuotes -> {
                    values += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        values += current.toString().trim()
        return values.map { it.removeSurrounding("\"") }
    }

    private fun JsonObject.string(key: String): String? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.contentOrNull
    }

    private fun JsonObject.int(key: String): Int? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.intOrNull
    }
}

object EnvironmentApiConfig {
    const val weatherBaseUrl = "https://api.data.gov.my/weather"
    const val waterProductionCsvUrl =
        "https://storage.data.gov.my/water/water_production.csv"
}
