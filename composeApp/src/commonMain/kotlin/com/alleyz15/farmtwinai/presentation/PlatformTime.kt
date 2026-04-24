package com.alleyz15.farmtwinai.presentation

expect fun wallClockEpochMillis(): Long

expect fun localUtcOffsetMinutes(epochMillis: Long): Int