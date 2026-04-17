package com.alleyz15.farmtwinai.data.farm

import com.alleyz15.farmtwinai.data.analysis.resolvedFieldInsightsBaseUrl
import com.alleyz15.farmtwinai.data.remote.platformHttpClientEngineFactory
import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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

    override suspend fun upsertFarmConfig(draft: FarmConfigDraft) {
        val payload = buildJsonObject {
            put("userId", draft.userId)
            put("farmName", draft.farmName)
            put("address", draft.address)
            put("mapQuery", draft.mapQuery)
            put("totalAreaInput", draft.totalAreaInput)
            put("mode", draft.mode.name)
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
                        }
                    )
                }
            })
            put("timelinePhotoCache", buildJsonArray {
                draft.timelinePhotoCache.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("dayNumber", entry.dayNumber)
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
                            put("title", entry.title)
                            put("description", entry.description)
                            put("imageDataUrl", entry.imageDataUrl)
                            put("provider", entry.provider)
                            put("updatedAtEpochMs", entry.updatedAtEpochMs)
                        }
                    )
                }
            })
        }

        val response = client.post("${baseUrl.trimEnd('/')}/farm-config") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
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
        val response = client.get("${baseUrl.trimEnd('/')}/farm-config/latest") {
            parameter("userId", userId)
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
            )
        }

        val boundary = parsePoints(itemObj["boundaryPoints"]?.jsonArray.orEmpty())
        val fallbackBoundary = lots.firstOrNull()?.points.orEmpty()

        val timelinePhotoCache = itemObj["timelinePhotoCache"]?.jsonArray.orEmpty().mapNotNull { rawEntry ->
            val obj = rawEntry.jsonObject
            val dayNumber = obj["dayNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val base64 = obj["photoBase64"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (base64.isBlank()) return@mapNotNull null
            TimelinePhotoCacheEntry(
                dayNumber = dayNumber,
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
                title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                imageDataUrl = imageDataUrl,
                provider = obj["provider"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                updatedAtEpochMs = obj["updatedAtEpochMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }

        val mode = itemObj["mode"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { AppMode.valueOf(it) }.getOrNull() }
            ?: AppMode.PLANNING

        return FarmConfigRemote(
            farmName = itemObj["farmName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            address = itemObj["address"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            mapQuery = itemObj["mapQuery"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            totalAreaInput = itemObj["totalAreaInput"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            mode = mode,
            boundaryPoints = if (boundary.size >= 3) boundary else fallbackBoundary,
            lots = lots,
            timelinePhotoCache = timelinePhotoCache,
            timelineStageVisualCache = timelineStageVisualCache,
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
}
