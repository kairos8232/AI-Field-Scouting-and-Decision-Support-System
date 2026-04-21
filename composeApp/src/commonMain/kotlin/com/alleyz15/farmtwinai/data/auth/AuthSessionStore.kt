package com.alleyz15.farmtwinai.data.auth

interface AuthSessionStore {
    fun readSessionJson(): String?

    fun writeSessionJson(sessionJson: String)

    fun clearSessionJson()
}

expect fun createAuthSessionStore(): AuthSessionStore
