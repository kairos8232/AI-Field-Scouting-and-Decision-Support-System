package com.alleyz15.farmtwinai.data.analysis

import com.alleyz15.farmtwinai.domain.model.FieldInsightReport
import com.alleyz15.farmtwinai.domain.model.FarmPoint

interface FieldInsightsRepository {
    suspend fun analyzePolygon(
        points: List<FarmPoint>,
        targetCrops: List<String> = emptyList(),
        totalFarmAreaHectares: Double? = null,
        lotAreaHectares: Double? = null,
    ): FieldInsightReport
}
