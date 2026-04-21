package com.alleyz15.farmtwinai.data.farm

import java.io.File

private class FileTimelineCacheStore : TimelineCacheStore {
    private val cacheFile: File by lazy {
        val basePath = System.getProperty("java.io.tmpdir") ?: "."
        File(basePath, "farmtwin_timeline_cache.json")
    }

    override fun readCacheJson(): String? {
        return runCatching {
            if (cacheFile.exists()) cacheFile.readText() else null
        }.getOrNull()
    }

    override fun writeCacheJson(cacheJson: String) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(cacheJson)
        }
    }

    override fun clearCacheJson() {
        runCatching {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }
}

actual fun createTimelineCacheStore(): TimelineCacheStore = FileTimelineCacheStore()
