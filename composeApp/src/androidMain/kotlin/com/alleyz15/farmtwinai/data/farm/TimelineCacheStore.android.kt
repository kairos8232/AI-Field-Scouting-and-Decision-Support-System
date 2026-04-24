package com.alleyz15.farmtwinai.data.farm

import com.alleyz15.farmtwinai.auth.AndroidGoogleAuthEnvironment
import java.io.File

private class FileTimelineCacheStore : TimelineCacheStore {
    private val cacheFile: File by lazy {
        val baseDir = runCatching { AndroidGoogleAuthEnvironment.appContext.filesDir }.getOrNull()
        val fallbackDir = System.getProperty("java.io.tmpdir")?.let { File(it) } ?: File(".")
        val storageDir = baseDir ?: fallbackDir
        File(storageDir, "farmtwin_timeline_cache.json")
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
