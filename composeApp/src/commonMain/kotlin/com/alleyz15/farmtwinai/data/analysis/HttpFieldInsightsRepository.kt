package com.alleyz15.farmtwinai.data.analysis

import com.alleyz15.farmtwinai.domain.model.AiChatContext
import com.alleyz15.farmtwinai.domain.model.AiChatReply
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.ActionTrackerFollowUp
import com.alleyz15.farmtwinai.domain.model.ChatMessage
import com.alleyz15.farmtwinai.domain.model.CurrentWeatherNow
import com.alleyz15.farmtwinai.data.remote.platformHttpClientEngineFactory
import com.alleyz15.farmtwinai.domain.model.CropRecommendation
import com.alleyz15.farmtwinai.domain.model.EarthEngineSummary
import com.alleyz15.farmtwinai.domain.model.FieldInsightReport
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.KnowledgeBaseReply
import com.alleyz15.farmtwinai.domain.model.KnowledgeBaseResult
import com.alleyz15.farmtwinai.domain.model.MessageSender
import com.alleyz15.farmtwinai.domain.model.FieldInsightHistoryCategory
import com.alleyz15.farmtwinai.domain.model.TimelinePhotoAssessment
import com.alleyz15.farmtwinai.domain.model.TimelineStageVisual
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

class HttpFieldInsightsRepository(
    private val client: HttpClient = HttpClient(platformHttpClientEngineFactory()),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = resolvedFieldInsightsBaseUrl(),
) : FieldInsightsRepository {

    override suspend fun analyzePolygon(
        points: List<FarmPoint>,
        targetCrops: List<String>,
        totalFarmAreaHectares: Double?,
        lotAreaHectares: Double?,
    ): FieldInsightReport {
        require(points.size >= 3) { "Polygon must contain at least 3 points." }
        val cleanedTargetCrops = targetCrops.map { it.trim() }.filter { it.isNotEmpty() }

        val centroid = centroid(points)
        val payload = buildJsonObject {
            put("polygon", buildJsonArray {
                points.forEach { point ->
                    add(
                        buildJsonObject {
                            put("x", point.x)
                            put("y", point.y)
                        }
                    )
                }
            })
            put(
                "centroid",
                buildJsonObject {
                    put("x", centroid.x)
                    put("y", centroid.y)
                }
            )
            put("targetCrops", buildJsonArray {
                cleanedTargetCrops.forEach { crop ->
                    add(JsonPrimitive(crop))
                }
            })
            if (totalFarmAreaHectares != null) {
                put("totalFarmAreaHectares", totalFarmAreaHectares)
            }
            if (lotAreaHectares != null) {
                put("lotAreaHectares", lotAreaHectares)
            }
        }

        val configuredBase = baseUrl.trimEnd('/')
        return runCatching {
            requestInsightsViaOrchestrator(configuredBase, payload.toString())
        }.getOrElse { cause ->
            if (cause is IllegalStateException) {
                throw cause
            }
            throw IllegalStateException(
                "Backend unreachable (baseUrl from env: $configuredBase)",
                cause,
            )
        }
    }

    override suspend fun generateTimelineStageVisual(
        dayNumber: Int,
        expectedStage: String,
        cropName: String,
    ): TimelineStageVisual {
        val payload = buildJsonObject {
            put("dayNumber", dayNumber)
            put("expectedStage", expectedStage)
            put("cropName", cropName)
        }
        val configuredBase = baseUrl.trimEnd('/')
        val response = client.post("$configuredBase/timeline/stage-visual") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        if (!response.status.isSuccess()) {
            val message = extractServerErrorMessage(response.body<String>())
            throw IllegalStateException(message)
        }
        return parseTimelineStageVisual(response.body<String>())
    }

    override suspend fun assessTimelinePhoto(
        dayNumber: Int,
        expectedStage: String,
        cropName: String,
        photoBase64: String,
        photoMimeType: String,
        userMarkedSimilar: Boolean?,
        userId: String?,
    ): TimelinePhotoAssessment {
        val cleanBase64 = photoBase64.substringAfter("base64,").trim()
        val payload = buildJsonObject {
            put("dayNumber", dayNumber)
            put("expectedStage", expectedStage)
            put("cropName", cropName)
            put("photoBase64", cleanBase64)
            put("photoMimeType", photoMimeType)
            if (userMarkedSimilar != null) {
                put("userMarkedSimilar", userMarkedSimilar)
            }
            if (!userId.isNullOrBlank()) {
                put("userId", userId)
            }
        }
        val configuredBase = baseUrl.trimEnd('/')
        val primaryResponse = client.post("$configuredBase/agents/daily-decision-loop") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        if (primaryResponse.status.isSuccess()) {
            return parseTimelinePhotoAssessmentFromScoutingLoop(primaryResponse.body<String>())
        }

        val secondaryResponse = client.post("$configuredBase/agents/scouting-loop") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        if (secondaryResponse.status.isSuccess()) {
            return parseTimelinePhotoAssessmentFromScoutingLoop(secondaryResponse.body<String>())
        }

        val fallbackResponse = client.post("$configuredBase/timeline/photo-compare") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        if (!fallbackResponse.status.isSuccess()) {
            val message = extractServerErrorMessage(fallbackResponse.body<String>())
            throw IllegalStateException(message)
        }
        return parseTimelinePhotoAssessment(fallbackResponse.body<String>())
    }

    override suspend fun consultAiChat(
        message: String,
        history: List<ChatMessage>,
        userId: String?,
        context: AiChatContext?,
    ): AiChatReply {
        val cleanMessage = message.trim()
        require(cleanMessage.isNotEmpty()) { "Message cannot be empty." }

        val payload = buildJsonObject {
            put("message", cleanMessage)

            if (!userId.isNullOrBlank()) {
                put("userId", userId)
            }

            put("history", buildJsonArray {
                history.takeLast(12).forEach { chatMessage ->
                    add(
                        buildJsonObject {
                            put(
                                "role",
                                if (chatMessage.sender == MessageSender.USER) "user" else "assistant",
                            )
                            put("content", chatMessage.content)
                        }
                    )
                }
            })

            if (context != null) {
                put(
                    "context",
                    buildJsonObject {
                        context.farmName?.takeIf { it.isNotBlank() }?.let { put("farmName", it) }
                        context.cropName?.takeIf { it.isNotBlank() }?.let { put("cropName", it) }
                        context.mode?.takeIf { it.isNotBlank() }?.let { put("mode", it) }
                        context.latestRecommendation?.takeIf { it.isNotBlank() }?.let {
                            put("latestRecommendation", it)
                        }
                    },
                )
            }
        }

        val configuredBase = baseUrl.trimEnd('/')
        val response = client.post("$configuredBase/chat") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        if (!response.status.isSuccess()) {
            val messageFromServer = extractServerErrorMessage(response.body<String>())
            throw IllegalStateException(messageFromServer)
        }

        val root = json.parseToJsonElement(response.body<String>()).jsonObject
        val replyText = root["reply"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (replyText.isBlank()) {
            throw IllegalStateException("AI response was empty.")
        }

        return AiChatReply(
            reply = replyText,
            provider = root["provider"]?.jsonPrimitive?.contentOrNull ?: "gemini",
        )
    }

    override suspend fun getCurrentWeatherNow(
        location: String,
        latitude: Double?,
        longitude: Double?,
    ): CurrentWeatherNow {
        val cleanLocation = location.trim()
        val safeLatitude = latitude?.takeIf { it.isFinite() }
        val safeLongitude = longitude?.takeIf { it.isFinite() }
        require(cleanLocation.isNotEmpty() || (safeLatitude != null && safeLongitude != null)) {
            "Location or coordinates are required."
        }

        val payload = buildJsonObject {
            if (cleanLocation.isNotEmpty()) {
                put("location", cleanLocation)
            }
            if (safeLatitude != null && safeLongitude != null) {
                put("latitude", safeLatitude)
                put("longitude", safeLongitude)
            }
        }

        val configuredBase = baseUrl.trimEnd('/')
        val response = client.post("$configuredBase/weather-now") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        if (!response.status.isSuccess()) {
            val message = extractServerErrorMessage(response.body<String>())
            throw IllegalStateException(message)
        }

        val root = json.parseToJsonElement(response.body<String>()).jsonObject
        val fallbackLocation = cleanLocation.ifBlank { "Farm centroid" }
        return CurrentWeatherNow(
            location = root["location"]?.jsonPrimitive?.contentOrNull ?: fallbackLocation,
            resolvedAddress = root["resolvedAddress"]?.jsonPrimitive?.contentOrNull ?: fallbackLocation,
            temperatureC = root["temperatureC"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            condition = root["condition"]?.jsonPrimitive?.contentOrNull ?: "Clear",
            icon = root["icon"]?.jsonPrimitive?.contentOrNull ?: "sun",
            provider = root["provider"]?.jsonPrimitive?.contentOrNull ?: "weather-fallback",
        )
    }

    override suspend fun queryKnowledgeBase(query: String, userId: String?, pageSize: Int): KnowledgeBaseReply {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotEmpty()) { "Query cannot be empty." }

        val payload = buildJsonObject {
            put("query", cleanQuery)
            put("pageSize", pageSize.coerceIn(1, 10))
            if (!userId.isNullOrBlank()) {
                put("userId", userId)
            }
        }

        val configuredBase = baseUrl.trimEnd('/')
        val response = client.post("$configuredBase/knowledge/query") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        if (!response.status.isSuccess()) {
            val message = extractServerErrorMessage(response.body<String>())
            throw IllegalStateException(message)
        }

        val root = json.parseToJsonElement(response.body<String>()).jsonObject
        val results = root["results"]?.jsonArray.orEmpty().map { item ->
            val obj = item.jsonObject
            val rawUri = obj["uri"]?.jsonPrimitive?.contentOrNull
                ?: obj["url"]?.jsonPrimitive?.contentOrNull
                ?: obj["link"]?.jsonPrimitive?.contentOrNull
            KnowledgeBaseResult(
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Knowledge Result",
                snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull ?: "",
                uri = normalizeKnowledgeUri(rawUri),
                sourceId = obj["sourceId"]?.jsonPrimitive?.contentOrNull ?: "knowledge-doc",
                score = obj["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }

        return KnowledgeBaseReply(
            query = root["query"]?.jsonPrimitive?.contentOrNull ?: cleanQuery,
            results = results,
            totalResults = root["totalResults"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: results.size,
            provider = root["provider"]?.jsonPrimitive?.contentOrNull ?: "vertex-ai-search",
        )
    }

    private suspend fun requestInsights(base: String, body: String): FieldInsightReport {
        val response = client.post("$base/field-insights") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            val message = extractServerErrorMessage(response.body<String>())
            throw IllegalStateException(message)
        }
        return parseReport(response.body<String>())
    }

    private suspend fun requestInsightsViaOrchestrator(base: String, body: String): FieldInsightReport {
        val response = client.post("$base/agents/field-insights-orchestrator") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.isSuccess()) {
            return parseReport(response.body<String>())
        }
        // Backward compatibility for environments not yet deployed with agent endpoints.
        return requestInsights(base, body)
    }

    private fun extractServerErrorMessage(raw: String): String {
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            root["detail"]?.jsonPrimitive?.contentOrNull
                ?: root["error"]?.jsonPrimitive?.contentOrNull
                ?: "wrong connection to server"
        }.getOrDefault("wrong connection to server")
    }

    private fun parseReport(raw: String): FieldInsightReport {
        val root = json.parseToJsonElement(raw).jsonObject
        val summary = root["summary"]?.jsonObject ?: error("Missing summary in backend response")
        val recommendations = root["recommendations"]?.jsonArray.orEmpty()

        return FieldInsightReport(
            summary = EarthEngineSummary(
                centroidLat = summary["centroidLat"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                centroidLng = summary["centroidLng"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                ndviMean = summary["ndviMean"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                soilMoistureMean = summary["soilMoistureMean"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                rainfallMm7d = summary["rainfallMm7d"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                averageTempC = summary["averageTempC"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                notes = summary["notes"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                source = summary["source"]?.jsonPrimitive?.contentOrNull
                    ?: if (summary["notes"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("real earth engine", ignoreCase = true)) "earth-engine-live" else "unknown",
                sourceVerified = summary["sourceVerified"]?.jsonPrimitive?.booleanOrNull
                    ?: summary["notes"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("real earth engine", ignoreCase = true),
            ),
            recommendations = recommendations.map { item ->
                val obj = item.jsonObject
                CropRecommendation(
                    cropName = obj["cropName"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
                    suitability = obj["suitability"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
                    rationale = obj["rationale"]?.jsonPrimitive?.contentOrNull ?: "No rationale returned",
                )
            },
            provider = root["provider"]?.jsonPrimitive?.contentOrNull ?: "gemini",
        )
    }

    private fun parseTimelineStageVisual(raw: String): TimelineStageVisual {
        val root = json.parseToJsonElement(raw).jsonObject
        return TimelineStageVisual(
            dayNumber = root["dayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
            expectedStage = root["expectedStage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            cropName = root["cropName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            farmId = root["farmId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            title = root["title"]?.jsonPrimitive?.contentOrNull ?: "Expected plant visual",
            description = root["description"]?.jsonPrimitive?.contentOrNull ?: "Daily expected morphology generated by AI.",
            imageDataUrl = root["imageDataUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            prompt = root["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            provider = root["provider"]?.jsonPrimitive?.contentOrNull ?: "gemini-mock",
        )
    }

    private fun parseTimelinePhotoAssessment(raw: String): TimelinePhotoAssessment {
        val root = json.parseToJsonElement(raw).jsonObject
        return TimelinePhotoAssessment(
            dayNumber = root["dayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
            expectedStage = root["expectedStage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            cropName = root["cropName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            similarityScore = root["similarityScore"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            isSimilar = root["isSimilar"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            observedStage = root["observedStage"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
            recommendation = root["recommendation"]?.jsonPrimitive?.contentOrNull ?: "Retake photo with clearer lighting.",
            rationale = root["rationale"]?.jsonPrimitive?.contentOrNull ?: "Insufficient detail for robust stage matching.",
            provider = root["provider"]?.jsonPrimitive?.contentOrNull ?: "gemini-mock",
        )
    }

    private fun parseTimelinePhotoAssessmentFromScoutingLoop(raw: String): TimelinePhotoAssessment {
        val root = json.parseToJsonElement(raw).jsonObject
        val scoutingRoot = root["scouting"]?.jsonObject ?: root
        val assessment = scoutingRoot["assessment"]?.jsonObject ?: scoutingRoot
        val recommendation = scoutingRoot["recommendation"]?.jsonObject
        return TimelinePhotoAssessment(
            dayNumber = assessment["dayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
            expectedStage = assessment["expectedStage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            cropName = assessment["cropName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            similarityScore = assessment["similarityScore"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            isSimilar = assessment["isSimilar"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            observedStage = assessment["observedStage"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
            recommendation = recommendation?.get("primaryAction")?.jsonPrimitive?.contentOrNull
                ?: assessment["recommendation"]?.jsonPrimitive?.contentOrNull
                ?: "Retake photo with clearer lighting.",
            rationale = recommendation?.get("issueSummary")?.jsonPrimitive?.contentOrNull
                ?: assessment["rationale"]?.jsonPrimitive?.contentOrNull
                ?: "Insufficient detail for robust stage matching.",
            provider = scoutingRoot["provider"]?.jsonPrimitive?.contentOrNull
                ?: root["provider"]?.jsonPrimitive?.contentOrNull
                ?: assessment["provider"]?.jsonPrimitive?.contentOrNull
                ?: "agent-scouting-loop-v1",
        )
    }

    private fun normalizeKnowledgeUri(raw: String?): String? {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return null
        val lower = clean.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> clean
            lower.startsWith("www.") -> "https://$clean"
            lower.startsWith("mailto:") || lower.startsWith("tel:") -> clean
            clean.contains('.') && !clean.contains(' ') -> "https://$clean"
            else -> null
        }
    }

    override suspend fun getHistory(userId: String?): List<com.alleyz15.farmtwinai.domain.model.FieldInsightHistoryRecord> {
        val userParam = userId?.trim().orEmpty()
        val url = if (userParam.isNotBlank()) {
            "$baseUrl/field-insights/history?limit=40&userId=$userParam"
        } else {
            "$baseUrl/field-insights/history?limit=40"
        }
        return try {
            val response = client.get(url)
            val bodyText = response.body<String>()
            val root = json.parseToJsonElement(bodyText).jsonObject
            val itemsArray = root["items"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            
            itemsArray.mapNotNull { item ->
                try {
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val summaryNotes = obj["summaryNotes"]?.jsonPrimitive?.contentOrNull
                        ?: obj["summary"]?.jsonPrimitive?.contentOrNull
                        ?: "No notes"
                    val firstRec = obj["recommendedCrops"]?.jsonPrimitive?.contentOrNull
                        ?: obj["recommendation"]?.jsonPrimitive?.contentOrNull
                        ?: "No rec"
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "History record"
                    val categoryRaw = obj["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val category = when (categoryRaw.trim().lowercase()) {
                        "scan" -> FieldInsightHistoryCategory.SCAN
                        "action_log" -> FieldInsightHistoryCategory.ACTION_LOG
                        "kb_search" -> FieldInsightHistoryCategory.KB_SEARCH
                        "timeline_comparison" -> FieldInsightHistoryCategory.TIMELINE_COMPARISON
                        "conversation" -> FieldInsightHistoryCategory.CONVERSATION
                        else -> FieldInsightHistoryCategory.UNKNOWN
                    }
                    
                    val createdObj = obj["createdAt"]?.jsonObject
                    val seconds = obj["dateString"]?.jsonPrimitive?.contentOrNull
                        ?.substringAfterLast(" ")
                        ?.toLongOrNull()
                        ?: createdObj?.get("_seconds")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        ?: 0L
                    val chatCount = obj["chatMessagesCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val hasChat = obj["hasConversation"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                        ?: (category == FieldInsightHistoryCategory.CONVERSATION)

                    com.alleyz15.farmtwinai.domain.model.FieldInsightHistoryRecord(
                        id = id,
                        category = category,
                        title = title,
                        summaryNotes = summaryNotes,
                        recommendedCrops = firstRec,
                        dateString = "Stored TS: $seconds",
                        hasConversation = hasChat,
                        chatMessagesCount = chatCount
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun logTimelineAction(
        userId: String,
        dayNumber: Int,
        actionType: ActionType,
        actionState: ActionState,
        summary: String,
        cropName: String,
    ) {
        val payload = buildJsonObject {
            put("userId", userId)
            put("dayNumber", dayNumber)
            put("actionType", actionType.name)
            put("actionState", actionState.name)
            put("summary", summary)
            put("cropName", cropName)
        }

        val configuredBase = baseUrl.trimEnd('/')
        val response = client.post("$configuredBase/timeline/action-log") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        if (!response.status.isSuccess()) {
            val message = extractServerErrorMessage(response.body<String>())
            throw IllegalStateException(message)
        }
    }

    override suspend fun trackActionFollowUp(
        userId: String,
        dayNumber: Int,
        cropName: String,
        issueType: String,
        actionTaken: String,
        note: String,
    ): ActionTrackerFollowUp {
        val payload = buildJsonObject {
            put("userId", userId)
            put("dayNumber", dayNumber)
            put("cropName", cropName)
            put("issueType", issueType)
            put("actionTaken", actionTaken)
            if (note.isNotBlank()) {
                put("note", note)
            }
        }

        val configuredBase = baseUrl.trimEnd('/')
        val response = client.post("$configuredBase/agents/action-tracker") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        if (!response.status.isSuccess()) {
            val message = extractServerErrorMessage(response.body<String>())
            throw IllegalStateException(message)
        }

        val root = json.parseToJsonElement(response.body<String>()).jsonObject
        val followUp = root["followUp"]?.jsonObject ?: root
        return ActionTrackerFollowUp(
            nextBestAction = followUp["nextBestAction"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            followUpQuestion = followUp["followUpQuestion"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            confidence = followUp["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            riskLevel = followUp["riskLevel"]?.jsonPrimitive?.contentOrNull ?: "unknown",
            provider = followUp["provider"]?.jsonPrimitive?.contentOrNull
                ?: root["provider"]?.jsonPrimitive?.contentOrNull
                ?: "agent-action-tracker-v1",
        )
    }

    private fun centroid(points: List<FarmPoint>): FarmPoint {
        val x = points.sumOf { it.x.toDouble() } / points.size
        val y = points.sumOf { it.y.toDouble() } / points.size
        return FarmPoint(x.toFloat(), y.toFloat())
    }
}
