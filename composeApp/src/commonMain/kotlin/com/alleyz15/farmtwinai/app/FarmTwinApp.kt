package com.alleyz15.farmtwinai.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.alleyz15.farmtwinai.data.analysis.HttpFieldInsightsRepository
import com.alleyz15.farmtwinai.data.auth.HttpAuthRepository
import com.alleyz15.farmtwinai.data.mock.MockFarmTwinRepository
import com.alleyz15.farmtwinai.navigation.rememberAppNavigator
import com.alleyz15.farmtwinai.presentation.FarmTwinAppState
import com.alleyz15.farmtwinai.ui.screens.FarmTwinNavHost
import com.alleyz15.farmtwinai.ui.theme.FarmTwinTheme

@Composable
fun FarmTwinApp() {
    val repository = remember { MockFarmTwinRepository() }
    val fieldInsightsRepository = remember { HttpFieldInsightsRepository() }
    val authRepository = remember { HttpAuthRepository() }
    val appState = remember { FarmTwinAppState(repository, fieldInsightsRepository, authRepository) }
    val navigator = rememberAppNavigator()

    FarmTwinTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            FarmTwinNavHost(
                navigator = navigator,
                appState = appState,
            )
        }
    }
}
