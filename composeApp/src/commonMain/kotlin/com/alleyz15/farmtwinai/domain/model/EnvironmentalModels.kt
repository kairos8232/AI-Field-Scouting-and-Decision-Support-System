package com.alleyz15.farmtwinai.domain.model

data class FarmLocation(
    val state: String,
    val district: String? = null,
    val town: String? = null,
)

data class WeatherForecast(
    val locationId: String,
    val locationName: String,
    val forecastDate: String,
    val morningForecast: String,
    val afternoonForecast: String,
    val nightForecast: String,
    val summaryForecast: String,
    val summaryWhen: String,
    val minTempCelsius: Int?,
    val maxTempCelsius: Int?,
)

data class WeatherWarning(
    val headline: String,
    val warningType: String?,
    val level: String?,
    val issuedAt: String?,
    val areas: List<String>,
    val rawDescription: String,
)

data class WaterProductionRecord(
    val date: String,
    val state: String,
    val millionLitresPerDay: Double,
)

data class EnvironmentalContext(
    val location: FarmLocation,
    val weatherForecasts: List<WeatherForecast>,
    val weatherWarnings: List<WeatherWarning>,
    val waterProductionRecords: List<WaterProductionRecord>,
)
