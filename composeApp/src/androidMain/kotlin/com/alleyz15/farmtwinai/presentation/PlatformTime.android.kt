package com.alleyz15.farmtwinai.presentation

actual fun wallClockEpochMillis(): Long = System.currentTimeMillis()

actual fun localUtcOffsetMinutes(epochMillis: Long): Int {
	val offsetMillis = java.util.TimeZone.getDefault().getOffset(epochMillis)
	return offsetMillis / 60_000
}