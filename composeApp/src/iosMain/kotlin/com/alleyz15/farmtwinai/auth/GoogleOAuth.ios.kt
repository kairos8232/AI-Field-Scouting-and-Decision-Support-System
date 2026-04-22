package com.alleyz15.farmtwinai.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSBundle
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val GOOGLE_AUTH_SCOPE = "openid email profile"
private const val GOOGLE_APP_REDIRECT_SCHEME = "farmtwinai"

private fun decodeQueryComponent(value: String): String {
    val input = value.replace('+', ' ')
    val out = StringBuilder(input.length)
    var index = 0
    while (index < input.length) {
        val ch = input[index]
        if (ch == '%' && index + 2 < input.length) {
            val hex = input.substring(index + 1, index + 3)
            val decoded = hex.toIntOrNull(16)
            if (decoded != null) {
                out.append(decoded.toChar())
                index += 3
                continue
            }
        }
        out.append(ch)
        index += 1
    }
    return out.toString()
}

internal actual fun platformGoogleOAuthClientId(): String {
    return NSBundle.mainBundle.objectForInfoDictionaryKey("GoogleOAuthClientId")?.toString().orEmpty()
}

internal actual fun platformGoogleOAuthRedirectUri(): String {
    return NSBundle.mainBundle.objectForInfoDictionaryKey("GoogleOAuthRedirectUri")?.toString().orEmpty()
}

private class IOSAuthPresentationContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        return UIApplication.sharedApplication.keyWindow ?: UIWindow()
    }
}

private class IosGoogleAuthProvider : GoogleAuthProvider {
    override suspend fun signIn(): String? {
        val config = resolvedGoogleOAuthConfig() ?: throw IllegalStateException(
            "Google Sign-In is not configured. Set GoogleOAuthClientId and GoogleOAuthRedirectUri in Info.plist."
        )

        return suspendCancellableCoroutine { continuation ->
            var settled = false

            fun fail(message: String) {
                if (!settled && continuation.isActive) {
                    settled = true
                    continuation.resumeWithException(IllegalStateException(message))
                }
            }

            fun succeed(code: String) {
                if (!settled && continuation.isActive) {
                    settled = true
                    continuation.resume(code)
                }
            }

            val components = NSURLComponents()
            components.scheme = "https"
            components.host = "accounts.google.com"
            components.path = "/o/oauth2/v2/auth"

            components.queryItems = listOf(
                NSURLQueryItem(name = "client_id", value = config.clientId),
                NSURLQueryItem(name = "redirect_uri", value = config.redirectUri),
                NSURLQueryItem(name = "response_type", value = "code"),
                NSURLQueryItem(name = "scope", value = GOOGLE_AUTH_SCOPE),
                NSURLQueryItem(name = "access_type", value = "offline"),
                NSURLQueryItem(name = "prompt", value = "select_account"),
                NSURLQueryItem(name = "include_granted_scopes", value = "true"),
            )

            val authUrl = components.URL ?: run {
                fail("Unable to create Google auth URL.")
                return@suspendCancellableCoroutine
            }

            val session = ASWebAuthenticationSession(
                uRL = authUrl,
                callbackURLScheme = GOOGLE_APP_REDIRECT_SCHEME,
                completionHandler = { callbackUrl: NSURL?, error: NSError? ->
                    when {
                        callbackUrl != null -> {
                            val callbackQuery = callbackUrl.absoluteString
                                ?.substringAfter("?", missingDelimiterValue = "")
                                .orEmpty()
                            val callbackParams = callbackQuery
                                .split('&')
                                .mapNotNull { chunk ->
                                    val separator = chunk.indexOf('=')
                                    if (separator <= 0) return@mapNotNull null
                                    val key = decodeQueryComponent(chunk.substring(0, separator))
                                    val value = decodeQueryComponent(chunk.substring(separator + 1))
                                    key to value
                                }
                                .toMap()
                            val code = callbackParams["code"]?.trim()
                            val errorDescription = callbackParams["error_description"]?.trim().orEmpty().ifBlank {
                                callbackParams["error"]?.trim().orEmpty()
                            }

                            when {
                                !errorDescription.isBlank() -> fail(errorDescription)
                                !code.isNullOrBlank() -> succeed(code)
                                else -> fail("Google Sign-In did not return an authorization code.")
                            }
                        }
                        error != null -> fail(error.toString())
                        else -> fail("Google Sign-In was cancelled.")
                    }
                }
            )
            val presentationContextProvider = IOSAuthPresentationContextProvider()
            session.presentationContextProvider = presentationContextProvider

            continuation.invokeOnCancellation {
                if (!settled) {
                    settled = true
                    session.cancel()
                }
            }

            val started = session.start()
            if (!started) {
                fail(
                    "Unable to start Google Sign-In session. Close any in-progress auth sheet and try again."
                )
            }
        }
    }
}

actual fun createGoogleAuthProvider(): GoogleAuthProvider = IosGoogleAuthProvider()
