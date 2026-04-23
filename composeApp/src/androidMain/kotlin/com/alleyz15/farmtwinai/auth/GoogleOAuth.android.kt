package com.alleyz15.farmtwinai.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.alleyz15.farmtwinai.BuildConfig
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

private const val GOOGLE_AUTH_SCOPE = "openid email profile"
private const val GOOGLE_AUTH_TIMEOUT_MS = 120_000L
private const val GOOGLE_APP_REDIRECT_SCHEME = "farmtwinai"

internal actual fun platformGoogleOAuthClientId(): String = BuildConfig.GOOGLE_OAUTH_CLIENT_ID

internal actual fun platformGoogleOAuthRedirectUri(): String = BuildConfig.GOOGLE_OAUTH_REDIRECT_URI

object AndroidGoogleOAuthBridge {
    private val pendingAuthorizationCode = AtomicReference<CompletableDeferred<String?>?>(null)

    fun registerIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (!data.scheme.equals(GOOGLE_APP_REDIRECT_SCHEME, ignoreCase = true)) {
            return
        }

        val code = data.getQueryParameter("code")?.trim()
        val error = data.getQueryParameter("error_description")?.trim().orEmpty().ifBlank {
            data.getQueryParameter("error")?.trim().orEmpty()
        }

        val pending = pendingAuthorizationCode.getAndSet(null)
        when {
            pending == null -> Unit
            !error.isBlank() -> pending.completeExceptionally(IllegalStateException(error))
            !code.isNullOrBlank() -> pending.complete(code)
            else -> pending.completeExceptionally(IllegalStateException("Google Sign-In did not return an authorization code."))
        }
    }

    suspend fun requestAuthorizationCode(context: Context): String? {
        val config = resolvedGoogleOAuthConfig() ?: throw IllegalStateException(
            "Google Sign-In is not configured. Set GOOGLE_OAUTH_CLIENT_ID and GOOGLE_OAUTH_REDIRECT_URI."
        )

        val authUrl = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", GOOGLE_AUTH_SCOPE)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "select_account")
            .appendQueryParameter("include_granted_scopes", "true")
            .build()

        val deferred = CompletableDeferred<String?>().also { pendingAuthorizationCode.set(it) }
        val intent = Intent(Intent.ACTION_VIEW, authUrl).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
        return withTimeoutOrNull(GOOGLE_AUTH_TIMEOUT_MS) {
            deferred.await()
        } ?: throw IllegalStateException("Google Sign-In timed out. Please try again.")
    }
}

private class AndroidGoogleAuthProvider : GoogleAuthProvider {
    override suspend fun signIn(): String? {
        val appContext = AndroidGoogleAuthEnvironment.appContext
        return AndroidGoogleOAuthBridge.requestAuthorizationCode(appContext)
    }
}

actual fun createGoogleAuthProvider(): GoogleAuthProvider {
    return AndroidGoogleAuthProvider()
}

object AndroidGoogleAuthEnvironment {
    lateinit var appContext: Context
        private set

    fun install(context: Context) {
        appContext = context.applicationContext
    }
}
