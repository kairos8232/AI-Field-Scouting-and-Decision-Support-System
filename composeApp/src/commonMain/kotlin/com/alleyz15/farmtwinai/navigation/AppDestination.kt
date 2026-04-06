package com.alleyz15.farmtwinai.navigation

sealed interface AppDestination {
    data object Welcome : AppDestination
    data class Auth(val isLogin: Boolean) : AppDestination
    data object UserSituation : AppDestination
    data object SetupMethod : AppDestination
    data object FarmMapSetup : AppDestination
    data object LotSectionSetup : AppDestination
    data object ManualSetup : AppDestination
    data object DocumentSetup : AppDestination
    data object QuickSetup : AppDestination
    data object Dashboard : AppDestination
    data object DigitalTwinMap : AppDestination
    data class ZoneDetail(val zoneId: String) : AppDestination
    data object Timeline : AppDestination
    data object AiChat : AppDestination
    data object ActionConfirmation : AppDestination
    data object History : AppDestination
}
