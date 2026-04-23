package com.alleyz15.farmtwinai.presentation

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
actual fun wallClockEpochMillis(): Long = time(null) * 1000L