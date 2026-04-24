package com.alleyz15.farmtwinai.data.farm

import com.alleyz15.farmtwinai.data.analysis.resolvedFieldInsightsBaseUrl
import com.alleyz15.farmtwinai.data.remote.platformHttpClientEngineFactory
import com.alleyz15.farmtwinai.domain.model.ActionState
import com.alleyz15.farmtwinai.domain.model.ActionType
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.ForecastConfidenceTier
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import com.alleyz15.farmtwinai.domain.model.RecoveryTrend
import com.alleyz15.farmtwinai.domain.model.TimelineStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class HttpFarmConfigRepository(
    private val client: HttpClient = HttpClient(platformHttpClientEngineFactory()),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = resolvedFieldInsightsBaseUrl(),
) : FarmConfigRepository {

    companion object {
        private const val FARM_CONFIG_REQUEST_TIMEOUT_MS = 120_000L
        private const val FARM_CONFIG_SOCKET_TIMEOUT_MS = 120_000L
        private const val FARM_CONFIG_MAX_ATTEMPTS = 3
        private const val FARM_CONFIG_RETRY_DELAY_MS = 1_000L
    }

    override suspend fun upsertFarmConfig(draft: FarmConfigDraft) {
        val payload = buildJsonObject {
            put("userId", draft.userId)
            put("activeFarmId", draft.activeFarmId)
            put("farms", buildJsonArray {
                draft.farms.forEach { farm ->
                    add(
                        buildJsonObject {
                            put("id", farm.id)
                            put("farmName", farm.farmName)
                            put("address", farm.address)
                            put("mapQuery", farm.mapQuery)
                            put("totalAreaInput", farm.totalAreaInput)
                            put("mode", farm.mode.name)
                            put("plantingDate", farm.plantingDate)
                            put("createdAtEpochMs", farm.createdAtEpochMs)
                            put("boundaryPoints", buildPointsArray(farm.boundaryPoints))
                            put("lots", buildJsonArray {
                                farm.lots.forEach { lot ->
                                    add(
                                        buildJsonObject {
                                            put("id", lot.id)
                                            put("name", lot.name)
                                            put("points", buildPointsArray(lot.points))
                                            put("cropPlan", lot.cropPlan)
                                            put("soilType", lot.soilType)
                                            put("waterAvailability", lot.waterAvailability)
                                            put("plantingDate", lot.plantingDate.orEmpty())
                                        }
                                    )
                                }
                            })
                        }
                    )
                }
            })
            put("farmName", draft.farmName)
            put("address", draft.address)
            put("mapQuery", draft.mapQuery)
            put("totalAreaInput", draft.totalAreaInput)
            put("mode", draft.mode.name)
            put("plantingDate", draft.plantingDate)
            put("boundaryPoints", buildPointsArray(draft.boundaryPoints))
            put("lots", buildJsonArray {
                draft.lots.forEach { lot ->
                    add(
                        buildJsonObject {
                            put("id", lot.id)
                            put("name", lot.name)
                            put("points", buildPointsArray(lot.points))
                            put("cropPlan", lot.cropPlan)
                            put("soilType", lot.soilType)
                            put("waterAvailability", lot.waterAvailability)
                            put("plantingDate", lot.plantingDate.orEmpty())
                        }
                    )
                }
            })
            put("timelinePhotoCache", buildJsonArray {
                draft.timelinePhotoCache.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("dayNumber", entry.dayNumber)
                            put("farmId", entry.farmId)
                            put("photoBase64", entry.photoBase64)
                            put("photoMimeType", entry.photoMimeType)
                            put("updatedAtEpochMs", entry.updatedAtEpochMs)
                        }
                    )
                }
            })
            put("timelineStageVisualCache", buildJsonArray {
                draft.timelineStageVisualCache.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("dayNumber", entry.dayNumber)
                            put("expectedStage", entry.expectedStage)
                            put("cropName", entry.cropName)
                            put("farmId", entry.farmId)
                            put("title", entry.title)
                            put("description", entry.description)
                            put("imageDataUrl", entry.imageDataUrl)
                            put("provider", entry.provider)
                            put("updatedAtEpochMs", entry.updatedAtEpochMs)
                        }
                    )
                }
            })
            put("timelineAssessmentCache", buildJsonArray {
                draft.timelineAssessmentCache.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("dayNumber", entry.dayNumber)
                            put("expectedStage", entry.expectedStage)
                            put("cropName", entry.cropName)
                            put("farmId", entry.farmId)
                            put("similarityScore", entry.similarityScore)
                            put("isSimilar", entry.isSimilar)
                            put("observedStage", entry.observedStage)
                            put("recommendation", entry.recommendation)
                            put("rationale", entry.rationale)
                            put("provider", entry.provider)
                            put("updatedAtEpochMs", entry.updatedAtEpochMs)
                        }
                    )
                }
            })
            put("timelineActionDecisionCache", buildJsonArray {
                draft.timelineActionDecisionCache.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("dayNumber", entry.dayNumber)
                            put("farmId", entry.farmId)
                            put("actionType", entry.actionType.name)
                            put("state", entry.state.name)
                            put("updatedAtEpochMs", entry.updatedAtEpochMs)
                            put("nextBestAction", entry.nextBestAction)
                            put("followUpQuestion", entry.followUpQuestion)
                            put("confidence", entry.confidence)
                            put("riskLevel", entry.riskLevel)
                            put("provider", entry.provider)
                        }
                    )
                }
            })
            put("timelineInsightCache", buildJsonArray {
                draft.timelineInsightCache.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("dayNumber", entry.dayNumber)
                            put("farmId", entry.farmId)
                            put("recommendedActionText", entry.recommendedActionText)
                            entry.timelineStatus?.let { put("timelineStatus", it.name) }
                            put("sourceDayNumber", entry.sourceDayNumber)
                            put("trend", entry.trend.name)
                            put("etaDaysMin", entry.etaDaysMin)
                            put("etaDaysMax", entry.etaDaysMax)
                            put("confidencePercent", entry.confidencePercent)
                            put("confidenceTier", entry.confidenceTier.name)
                            put("isUrgent", entry.isUrgent)
                            put("updatedAtEpochMs", entry.updatedAtEpochMs)
                        }
                    )
                }
            })
        }

        val response = withTransientNetworkRetry {
            client.post("${baseUrl.trimEnd('/')}/farm-config") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
                timeout {
                    requestTimeoutMillis = FARM_CONFIG_REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = FARM_CONFIG_SOCKET_TIMEOUT_MS
                }
            }
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                describeHttpError(
                    response = response,
                    endpoint = "${baseUrl.trimEnd('/')}/farm-config",
                    operation = "save farm config",
                )
            )
        }
    }

    override suspend fun fetchLatestFarmConfig(userId: String): FarmConfigRemote? {
        val response = withTransientNetworkRetry {
            client.get("${baseUrl.trimEnd('/')}/farm-config/latest") {
                parameter("userId", userId)
                timeout {
                    requestTimeoutMillis = FARM_CONFIG_REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = FARM_CONFIG_SOCKET_TIMEOUT_MS
                }
            }
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                describeHttpError(
                    response = response,
                    endpoint = "${baseUrl.trimEnd('/')}/farm-config/latest",
                    operation = "load farm config",
                )
            )
        }

        val root = json.parseToJsonElement(response.body<String>()).jsonObject
        val item = root["item"] ?: return null
        if (item is kotlinx.serialization.json.JsonNull) return null
        val itemObj = item.jsonObject

        val farms = itemObj["farms"]?.jsonArray.orEmpty().mapIndexedNotNull { index, rawFarm ->
            val farmObj = rawFarm.jsonObject
            val farmLots = farmObj["lots"]?.jsonArray.orEmpty().mapIndexedNotNull { lotIndex, rawLot ->
                val lotObj = rawLot.jsonObject
                val points = parsePoints(lotObj["points"]?.jsonArray.orEmpty())
                if (points.size < 3) return@mapIndexedNotNull null

                LotSectionDraft(
                    id = lotObj["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "lot-${lotIndex + 1}" },
                    name = lotObj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "Lot ${lotIndex + 1}" },
                    points = points,
                    cropPlan = lotObj["cropPlan"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    soilType = lotObj["soilType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    waterAvailability = lotObj["waterAvailability"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    plantingDate = lotObj["plantingDate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }

            val boundary = parsePoints(farmObj["boundaryPoints"]?.jsonArray.orEmpty())
            val fallbackBoundary = farmLots.firstOrNull()?.points.orEmpty()
            if (farmLots.isEmpty()) return@mapIndexedNotNull null

            val mode = farmObj["mode"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { AppMode.valueOf(it) }.getOrNull() }
                ?: AppMode.PLANNING

            FarmConfigFarmEntry(
                id = farmObj["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "farm-${index + 1}" },
                farmName = farmObj["farmName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                address = farmObj["address"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                mapQuery = farmObj["mapQuery"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                totalAreaInput = farmObj["totalAreaInput"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                mode = mode,
                plantingDate = farmObj["plantingDate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                createdAtEpochMs = farmObj["createdAtEpochMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                boundaryPoints = if (boundary.size >= 3) boundary else fallbackBoundary,
                lots = farmLots,
            )
        }

        val lots = itemObj["lots"]?.jsonArray.orEmpty().mapIndexedNotNull { index, rawLot ->
            val lotObj = rawLot.jsonObject
            val points = parsePoints(lotObj["points"]?.jsonArray.orEmpty())
            if (points.size < 3) return@mapIndexedNotNull null

            LotSectionDraft(
                id = lotObj["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "lot-${index + 1}" },
                name = lotObj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "Lot ${index + 1}" },
                points = points,
                cropPlan = lotObj["cropPlan"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                soilType = lotObj["soilType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                waterAvailability = lotObj["waterAvailability"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                plantingDate = lotObj["plantingDate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }

        val boundary = parsePoints(itemObj["boundaryPoints"]?.jsonArray.orEmpty())
        val fallbackBoundary = lots.firstOrNull()?.points.orEmpty()

        val legacyMode = itemObj["mode"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { AppMode.valueOf(it) }.getOrNull() }
            ?: AppMode.PLANNING

        val legacyFarm = if (lots.isNotEmpty()) {
            FarmConfigFarmEntry(
                id = itemObj["id"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "farm-legacy" },
                farmName = itemObj["farmName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                address = itemObj["address"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                mapQuery = itemObj["mapQuery"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                totalAreaInput = itemObj["totalAreaInput"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                mode = legacyMode,
                plantingDate = itemObj["plantingDate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                createdAtEpochMs = itemObj["createdAtEpochMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                boundaryPoints = if (boundary.size >= 3) boundary else fallbackBoundary,
                lots = lots,
            )
        } else {
            null
        }

        val resolvedFarms = if (farms.isNotEmpty()) farms else listOfNotNull(legacyFarm)
        val activeFarmId = itemObj["activeFarmId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { resolvedFarms.firstOrNull()?.id.orEmpty() }
        val activeFarm = resolvedFarms.firstOrNull { it.id == activeFarmId } ?: resolvedFarms.firstOrNull()

        val timelinePhotoCache = itemObj["timelinePhotoCache"]?.jsonArray.orEmpty().mapNotNull { rawEntry ->
            val obj = rawEntry.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val base64 = obj["photoBase64"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (base64.isBlank()) return@mapNotNull null
            TimelinePhotoCacheEntry(
                dayNumber = dayNumber,
                farmId = obj["farmId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                photoBase64 = base64,
                photoMimeType = obj["photoMimeType"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "image/jpeg" },
                updatedAtEpochMs = obj["updatedAtEpochMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }

        val timelineStageVisualCache = itemObj["timelineStageVisualCache"]?.jsonArray.orEmpty().mapNotNull { rawEntry ->
            val obj = rawEntry.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val imageDataUrl = obj["imageDataUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (imageDataUrl.isBlank()) return@mapNotNull null
            TimelineStageVisualCacheEntry(
                dayNumber = dayNumber,
                expectedStage = obj["expectedStage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                cropName = obj["cropName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                farmId = obj["farmId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                imageDataUrl = imageDataUrl,
                provider = obj["provider"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                updatedAtEpochMs = obj["updatedAtEpochMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }

        val timelineAssessmentCache = itemObj["timelineAssessmentCache"]?.jsonArray.orEmpty().mapNotNull { rawEntry ->
            val obj = rawEntry.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            TimelinePhotoAssessmentCacheEntry(
                dayNumber = dayNumber,
                expectedStage = obj["expectedStage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                cropName = obj["cropName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                farmId = obj["farmId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                similarityScore = obj["similarityScore"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                isSimilar = obj["isSimilar"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                observedStage = obj["observedStage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                recommendation = obj["recommendation"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                rationale = obj["rationale"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                provider = obj["provider"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                updatedAtEpochMs = obj["updatedAtEpochMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }

        val timelineActionDecisionCache = itemObj["timelineActionDecisionCache"]?.jsonArray.orEmpty().mapNotNull { rawEntry ->
            val obj = rawEntry.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val actionType = obj["actionType"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { ActionType.valueOf(it) }.getOrNull() }
                ?: return@mapNotNull null
            val state = obj["state"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { ActionState.valueOf(it) }.getOrNull() }
                ?: return@mapNotNull null
            TimelineActionDecisionCacheEntry(
                dayNumber = dayNumber,
                farmId = obj["farmId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                actionType = actionType,
                state = state,
                updatedAtEpochMs = obj["updatedAtEpochMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                nextBestAction = obj["nextBestAction"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                followUpQuestion = obj["followUpQuestion"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                confidence = obj["confidence"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0,
                riskLevel = obj["riskLevel"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                provider = obj["provider"]?.jsonPrimitive?.contentOrNull ?: "agent-action-tracker-v1",
            )
        }

        val timelineInsightCache = itemObj["timelineInsightCache"]?.jsonArray.orEmpty().mapNotNull { rawEntry ->
            val obj = rawEntry.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val trend = obj["trend"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { RecoveryTrend.valueOf(it) }.getOrNull() }
                ?: RecoveryTrend.UNKNOWN
            val confidenceTier = obj["confidenceTier"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { ForecastConfidenceTier.valueOf(it) }.getOrNull() }
                ?: ForecastConfidenceTier.LOW
            val timelineStatus = obj["timelineStatus"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { TimelineStatus.valueOf(it) }.getOrNull() }
            TimelineInsightCacheEntry(
                dayNumber = dayNumber,
                farmId = obj["farmId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                recommendedActionText = obj["recommendedActionText"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                timelineStatus = timelineStatus,
                sourceDayNumber = obj["sourceDayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: dayNumber,
                trend = trend,
                etaDaysMin = obj["etaDaysMin"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
                etaDaysMax = obj["etaDaysMax"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
                confidencePercent = obj["confidencePercent"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                confidenceTier = confidenceTier,
                isUrgent = obj["isUrgent"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                updatedAtEpochMs = obj["updatedAtEpochMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }

        return FarmConfigRemote(
            activeFarmId = activeFarm?.id.orEmpty(),
            farms = resolvedFarms,
            farmName = activeFarm?.farmName.orEmpty(),
            address = activeFarm?.address.orEmpty(),
            mapQuery = activeFarm?.mapQuery.orEmpty(),
            totalAreaInput = activeFarm?.totalAreaInput.orEmpty(),
            mode = activeFarm?.mode ?: AppMode.PLANNING,
            plantingDate = activeFarm?.plantingDate.orEmpty(),
            boundaryPoints = activeFarm?.boundaryPoints.orEmpty(),
            lots = activeFarm?.lots.orEmpty(),
            timelinePhotoCache = timelinePhotoCache,
            timelineStageVisualCache = timelineStageVisualCache,
            timelineAssessmentCache = timelineAssessmentCache,
            timelineActionDecisionCache = timelineActionDecisionCache,
            timelineInsightCache = timelineInsightCache,
        )
    }

    private fun buildPointsArray(points: List<FarmPoint>) = buildJsonArray {
        points.forEach { point ->
            add(
                buildJsonObject {
                    put("x", JsonPrimitive(point.x))
                    put("y", JsonPrimitive(point.y))
                }
            )
        }
    }

    private fun parsePoints(items: List<kotlinx.serialization.json.JsonElement>): List<FarmPoint> {
        return items.mapNotNull { raw ->
            val pointObj = raw.jsonObject
            val x = pointObj["x"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
            val y = pointObj["y"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
            if (x == null || y == null) {
                null
            } else {
                FarmPoint(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
            }
        }
    }

    private suspend fun describeHttpError(
        response: HttpResponse,
        endpoint: String,
        operation: String,
    ): String {
        val code = response.status.value
        val bodyText = runCatching { response.body<String>() }.getOrDefault("")
        val parsedMessage = runCatching {
            val obj = json.parseToJsonElement(bodyText).jsonObject
            listOfNotNull(
                obj["error"]?.jsonPrimitive?.contentOrNull,
                obj["detail"]?.jsonPrimitive?.contentOrNull,
            ).joinToString(" - ")
        }.getOrNull().orEmpty()

        if (code == 404) {
            return "Unable to $operation: API endpoint not found (404) at $endpoint. Backend likely not updated/redeployed with /api/farm-config routes."
        }

        if (parsedMessage.isNotBlank()) {
            return "Unable to $operation: HTTP $code at $endpoint. $parsedMessage"
        }

        return "Unable to $operation: HTTP $code at $endpoint."
    }

    private suspend fun <T> withTransientNetworkRetry(block: suspend () -> T): T {
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < FARM_CONFIG_MAX_ATTEMPTS) {
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                attempt += 1

                if (!isTransientNetworkError(error) || attempt >= FARM_CONFIG_MAX_ATTEMPTS) {
                    throw error
                }

                delay(FARM_CONFIG_RETRY_DELAY_MS * attempt)
            }
        }

        throw lastError ?: IllegalStateException("Farm config request failed after retries.")
    }

    private fun isTransientNetworkError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("socket timeout") ||
            message.contains("timed out") ||
            message.contains("network connection was lost") ||
            message.contains("nsurlerrordomain code=-1005") ||
            message.contains("connection reset") ||
            message.contains("connection abort") ||
            message.contains("temporary failure")
    }
}
