package com.alleyz15.farmtwinai.data.farm

interface TimelineCacheStore {
    fun readCacheJson(): String?

    fun writeCacheJson(cacheJson: String)

    fun clearCacheJson()
}

expect fun createTimelineCacheStore(): TimelineCacheStore
