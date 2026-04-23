package com.alleyz15.farmtwinai.navigation

sealed interface AppDestination {
    data object Welcome : AppDestination
    data class Auth(val isLogin: Boolean) : AppDestination
    data object UserSituation : AppDestination
    data object SetupMethod : AppDestination
    data object FarmMapSetup : AppDestination
    data object FarmBoundaryDraw : AppDestination
    data object PolygonInsights : AppDestination
    data object LotSectionSetup : AppDestination
    data object LotRecommendation : AppDestination
    data object ManualSetup : AppDestination
    data object DocumentSetup : AppDestination
    data object QuickSetup : AppDestination
    data object Dashboard : AppDestination
    data class ZoneDetail(val zoneId: String) : AppDestination
    data object Timeline : AppDestination
    data object AiChat : AppDestination
    data object KnowledgeBase : AppDestination
    data object ActionConfirmation : AppDestination
    data object History : AppDestination
    data object Me : AppDestination
}
