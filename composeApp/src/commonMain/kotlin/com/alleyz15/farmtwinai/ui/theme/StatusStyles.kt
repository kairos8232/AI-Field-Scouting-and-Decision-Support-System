package com.alleyz15.farmtwinai.ui.theme

import androidx.compose.ui.graphics.Color
import com.alleyz15.farmtwinai.domain.model.HealthStatus
import com.alleyz15.farmtwinai.domain.model.TimelineStatus

data class StatusStyle(
    val label: String,
    val container: Color,
    val content: Color,
)

fun HealthStatus.style(): StatusStyle {
    return when (this) {
        HealthStatus.HEALTHY -> StatusStyle("Healthy", Mint200, Forest700)
        HealthStatus.MONITOR -> StatusStyle("Monitor", Color(0xFFFFF0C9), WarningAmber)
        HealthStatus.ISSUE -> StatusStyle("Issue", Color(0xFFF7D6D6), AlertRed)
        HealthStatus.WATER_ISSUE -> StatusStyle("Water Issue", Color(0xFFD9F0F8), WaterBlue)
        HealthStatus.UPDATED -> StatusStyle("Updated", Color(0xFFD7F0F3), UpdateCyan)
    }
}

fun TimelineStatus.style(): StatusStyle {
    return when (this) {
        TimelineStatus.NORMAL -> StatusStyle("Normal", Mint200, Forest700)
        TimelineStatus.WARNING -> StatusStyle("Warning", Color(0xFFFFF0C9), WarningAmber)
        TimelineStatus.ACTION_TAKEN -> StatusStyle("Action Taken", Color(0xFFD9F0F8), WaterBlue)
        TimelineStatus.UPDATED -> StatusStyle("Updated", Color(0xFFD7F0F3), UpdateCyan)
    }
}
