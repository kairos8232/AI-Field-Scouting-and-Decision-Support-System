package com.alleyz15.farmtwinai.data.auth

import com.alleyz15.farmtwinai.auth.AuthUser
import com.alleyz15.farmtwinai.data.analysis.resolvedFieldInsightsBaseUrl
import com.alleyz15.farmtwinai.data.remote.platformHttpClientEngineFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class HttpAuthRepository(
    private val client: HttpClient = HttpClient(platformHttpClientEngineFactory()),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = resolvedFieldInsightsBaseUrl(),
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): AuthUser {
        return requestAuth(
            path = "auth/signin",
            body = buildJsonObject {
                put("email", email)
                put("password", password)
            }.toString(),
        )
    }

    override suspend fun signUp(email: String, password: String, displayName: String): AuthUser {
        return requestAuth(
            path = "auth/signup",
            body = buildJsonObject {
                put("email", email)
                put("password", password)
                put("displayName", displayName)
            }.toString(),
        )
    }

    private suspend fun requestAuth(path: String, body: String): AuthUser {
        val configuredBase = baseUrl.trimEnd('/')
        val response = client.post("$configuredBase/$path") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        if (!response.status.isSuccess()) {
            val errorRaw = response.body<String>()
            val message = runCatching {
                json.parseToJsonElement(errorRaw)
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.getOrNull() ?: "Auth request failed: ${response.status}"
            throw IllegalStateException(message)
        }

        val root = json.parseToJsonElement(response.body<String>()).jsonObject
        return AuthUser(
            userId = root["userId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            email = root["email"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            displayName = root["displayName"]?.jsonPrimitive?.contentOrNull,
            idToken = root["idToken"]?.jsonPrimitive?.contentOrNull,
        )
    }
}
