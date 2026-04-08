package com.alleyz15.farmtwinai.data.environment

import com.alleyz15.farmtwinai.domain.model.EnvironmentalContext
import com.alleyz15.farmtwinai.domain.model.FarmLocation
import com.alleyz15.farmtwinai.domain.model.WaterProductionRecord
import com.alleyz15.farmtwinai.domain.model.WeatherForecast
import com.alleyz15.farmtwinai.domain.model.WeatherWarning

interface EnvironmentalDataRepository {
    suspend fun fetchWeatherForecasts(location: FarmLocation): List<WeatherForecast>

    suspend fun fetchWeatherWarnings(): List<WeatherWarning>

    suspend fun fetchWaterProduction(state: String? = null): List<WaterProductionRecord>

    suspend fun fetchEnvironmentalContext(location: FarmLocation): EnvironmentalContext
}
