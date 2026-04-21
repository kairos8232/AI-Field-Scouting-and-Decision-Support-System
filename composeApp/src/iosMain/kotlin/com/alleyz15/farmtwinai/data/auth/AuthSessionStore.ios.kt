package com.alleyz15.farmtwinai.data.auth

import platform.Foundation.NSUserDefaults

private class UserDefaultsAuthSessionStore : AuthSessionStore {
    private val key = "farmtwin.auth.session.json"

    override fun readSessionJson(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey(key)
    }

    override fun writeSessionJson(sessionJson: String) {
        NSUserDefaults.standardUserDefaults.setObject(sessionJson, forKey = key)
    }

    override fun clearSessionJson() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
    }
}

actual fun createAuthSessionStore(): AuthSessionStore = UserDefaultsAuthSessionStore()
