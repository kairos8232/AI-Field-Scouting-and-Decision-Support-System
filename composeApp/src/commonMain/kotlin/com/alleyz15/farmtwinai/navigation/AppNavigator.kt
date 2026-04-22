package com.alleyz15.farmtwinai.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

@Stable
class AppNavigator {
    private val backStack = mutableStateListOf<AppDestination>(AppDestination.Welcome)

    val currentDestination: State<AppDestination> =
        derivedStateOf { backStack.last() }

    fun navigate(destination: AppDestination) {
        backStack += destination
    }

    fun replace(destination: AppDestination) {
        backStack[backStack.lastIndex] = destination
    }

    fun resetTo(destination: AppDestination) {
        backStack.clear()
        backStack += destination
    }

    fun pop(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            true
        } else {
            false
        }
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator = remember { AppNavigator() }
