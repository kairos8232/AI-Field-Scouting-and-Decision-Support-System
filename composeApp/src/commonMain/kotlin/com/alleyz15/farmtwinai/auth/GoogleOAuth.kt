package com.alleyz15.farmtwinai.auth

data class GoogleOAuthConfig(
    val clientId: String,
    val redirectUri: String,
)

interface GoogleAuthProvider {
    suspend fun signIn(): String?
}

expect fun createGoogleAuthProvider(): GoogleAuthProvider

internal expect fun platformGoogleOAuthClientId(): String

internal expect fun platformGoogleOAuthRedirectUri(): String

internal fun resolvedGoogleOAuthConfig(): GoogleOAuthConfig? {
    val clientId = platformGoogleOAuthClientId().trim()
    val redirectUri = platformGoogleOAuthRedirectUri().trim()
    if (clientId.isBlank() || redirectUri.isBlank()) return null
    return GoogleOAuthConfig(clientId = clientId, redirectUri = redirectUri)
}