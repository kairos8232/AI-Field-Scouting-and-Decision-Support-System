package com.alleyz15.farmtwinai.data.farm

import platform.Foundation.NSUserDefaults

private class UserDefaultsTimelineCacheStore : TimelineCacheStore {
    private val key = "farmtwin.timeline.cache.json"

    override fun readCacheJson(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey(key)
    }

    override fun writeCacheJson(cacheJson: String) {
        NSUserDefaults.standardUserDefaults.setObject(cacheJson, forKey = key)
    }

    override fun clearCacheJson() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
    }
}

actual fun createTimelineCacheStore(): TimelineCacheStore = UserDefaultsTimelineCacheStore()
