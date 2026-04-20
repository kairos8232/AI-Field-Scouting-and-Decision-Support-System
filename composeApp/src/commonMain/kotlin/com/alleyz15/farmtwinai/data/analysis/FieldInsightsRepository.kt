package com.alleyz15.farmtwinai.data.analysis

import com.alleyz15.farmtwinai.domain.model.AiChatContext
import com.alleyz15.farmtwinai.domain.model.AiChatReply
import com.alleyz15.farmtwinai.domain.model.ChatMessage
import com.alleyz15.farmtwinai.domain.model.CurrentWeatherNow
import com.alleyz15.farmtwinai.domain.model.FieldInsightReport
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.KnowledgeBaseReply
import com.alleyz15.farmtwinai.domain.model.TimelinePhotoAssessment
import com.alleyz15.farmtwinai.domain.model.TimelineStageVisual

interface FieldInsightsRepository {
    suspend fun analyzePolygon(
        points: List<FarmPoint>,
        targetCrops: List<String> = emptyList(),
        totalFarmAreaHectares: Double? = null,
        lotAreaHectares: Double? = null,
    ): FieldInsightReport

    suspend fun generateTimelineStageVisual(
        dayNumber: Int,
        expectedStage: String,
        cropName: String,
    ): TimelineStageVisual

    suspend fun assessTimelinePhoto(
        dayNumber: Int,
        expectedStage: String,
        cropName: String,
        photoBase64: String,
        photoMimeType: String,
        userMarkedSimilar: Boolean? = null,
    ): TimelinePhotoAssessment

    suspend fun consultAiChat(
        message: String,
        history: List<ChatMessage>,
        userId: String? = null,
        context: AiChatContext? = null,
    ): AiChatReply

    suspend fun getCurrentWeatherNow(
        location: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): CurrentWeatherNow

    suspend fun queryKnowledgeBase(
        query: String,
        pageSize: Int = 5,
    ): KnowledgeBaseReply

    suspend fun getHistory(): List<com.alleyz15.farmtwinai.domain.model.FieldInsightHistoryRecord>
}
