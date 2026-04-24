package com.alleyz15.farmtwinai.presentation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import platform.posix.localtime_r
import platform.posix.time
import platform.posix.time_t
import platform.posix.tm

@OptIn(ExperimentalForeignApi::class)
actual fun wallClockEpochMillis(): Long = time(null) * 1000L

@OptIn(ExperimentalForeignApi::class)
actual fun localUtcOffsetMinutes(epochMillis: Long): Int = memScoped {
    val epochSeconds = (epochMillis / 1000L).convert<time_t>()
    val localTm = alloc<tm>()
    val result = localtime_r(cValuesOf(epochSeconds), localTm.ptr) ?: return@memScoped 0
    (result.pointed.tm_gmtoff / 60).toInt()
}
