package com.alleyz15.farmtwinai.app

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.alleyz15.farmtwinai.data.mock.MockFarmTwinRepository
import com.alleyz15.farmtwinai.navigation.rememberAppNavigator
import com.alleyz15.farmtwinai.presentation.FarmTwinAppState
import com.alleyz15.farmtwinai.ui.screens.FarmTwinNavHost
import com.alleyz15.farmtwinai.ui.theme.FarmTwinTheme

@Composable
fun FarmTwinApp() {
    val repository = remember { MockFarmTwinRepository() }
    val appState = remember { FarmTwinAppState(repository) }
    val navigator = rememberAppNavigator()

    FarmTwinTheme {
        Surface {
            FarmTwinNavHost(
                navigator = navigator,
                appState = appState,
            )
        }
    }
}
