package com.alleyz15.farmtwinai.data.analysis

internal expect fun platformFieldInsightsBaseUrl(): String

internal fun resolvedFieldInsightsBaseUrl(): String {
    val raw = platformFieldInsightsBaseUrl()
        .trim()
        .trim('"')
        .trim('\'')
        .replace("\\n", "")
        .replace("\\r", "")
        .trimEnd('/')
    if (raw.isBlank()) {
        throw IllegalStateException("FIELD_INSIGHTS_BASE_URL is not configured. Set it in environment/secrets.")
    }

    val migrated = migrateLegacyRunHost(raw)

    val withScheme = when {
        migrated.startsWith("http://") || migrated.startsWith("https://") -> migrated
        migrated.startsWith("localhost") || migrated.startsWith("127.0.0.1") -> "http://$migrated"
        else -> migrated
    }

    val normalizedLocal = when (withScheme) {
        "https://localhost", "http://localhost", "https://127.0.0.1", "http://127.0.0.1" -> "http://localhost:8080"
        else -> normalizeLocalhostVariant(withScheme)
    }

    return if (normalizedLocal.endsWith("/api")) normalizedLocal else "$normalizedLocal/api"
}

private fun migrateLegacyRunHost(url: String): String {
    val legacyBase = "https://farmtwin-field-insights-578643838222.asia-southeast1.run.app"
    val canonicalBase = "https://farmtwin-field-insights-w35mh5k5qa-as.a.run.app"
    return when {
        url == legacyBase -> canonicalBase
        url.startsWith("$legacyBase/") -> canonicalBase + url.removePrefix(legacyBase)
        else -> url
    }
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
