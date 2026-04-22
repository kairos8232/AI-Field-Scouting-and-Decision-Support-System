package com.alleyz15.farmtwinai.data.auth

import java.io.File

private class FileAuthSessionStore : AuthSessionStore {
    private val sessionFile: File by lazy {
        val basePath = System.getProperty("java.io.tmpdir") ?: "."
        File(basePath, "farmtwin_auth_session.json")
    }

    override fun readSessionJson(): String? {
        return runCatching {
            if (sessionFile.exists()) sessionFile.readText() else null
        }.getOrNull()
    }

    override fun writeSessionJson(sessionJson: String) {
        runCatching {
            sessionFile.parentFile?.mkdirs()
            sessionFile.writeText(sessionJson)
        }
    }

    override fun clearSessionJson() {
        runCatching {
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
        }
    }
}

actual fun createAuthSessionStore(): AuthSessionStore = FileAuthSessionStore()
