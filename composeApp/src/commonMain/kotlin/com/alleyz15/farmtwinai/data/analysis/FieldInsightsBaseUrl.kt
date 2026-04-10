package com.alleyz15.farmtwinai.data.analysis

internal expect fun platformFieldInsightsBaseUrl(): String

internal fun resolvedFieldInsightsBaseUrl(): String {
    val defaultUrl = "http://localhost:8080/api"
    val raw = platformFieldInsightsBaseUrl()
        .trim()
        .trim('"')
        .replace("\\n", "")
        .replace("\\r", "")
        .trimEnd('/')
    if (raw.isBlank()) return defaultUrl

    val withScheme = when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        raw.startsWith("localhost") || raw.startsWith("127.0.0.1") -> "http://$raw"
        else -> raw
    }

    val normalizedLocal = when (withScheme) {
        "https://localhost", "http://localhost", "https://127.0.0.1", "http://127.0.0.1" -> "http://localhost:8080"
        else -> normalizeLocalhostVariant(withScheme)
    }

    return if (normalizedLocal.endsWith("/api")) normalizedLocal else "$normalizedLocal/api"
}

private fun normalizeLocalhostVariant(url: String): String {
    return when {
        url.startsWith("https://localhost/") -> "http://localhost:8080" + url.removePrefix("https://localhost")
        url.startsWith("http://localhost/") -> {
            if (url.startsWith("http://localhost:/")) "http://localhost:8080" + url.removePrefix("http://localhost") else url
        }
        url.startsWith("https://127.0.0.1/") -> "http://localhost:8080" + url.removePrefix("https://127.0.0.1")
        url.startsWith("http://127.0.0.1/") -> "http://localhost:8080" + url.removePrefix("http://127.0.0.1")
        else -> url
    }.let { normalized ->
        when {
            normalized.startsWith("http://localhost/") && !normalized.startsWith("http://localhost:8080/") ->
                "http://localhost:8080" + normalized.removePrefix("http://localhost")
            else -> normalized
        }
    }
}
