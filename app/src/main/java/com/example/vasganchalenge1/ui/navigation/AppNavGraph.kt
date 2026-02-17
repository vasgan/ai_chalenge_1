package com.example.vasganchalenge1.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.vasganchalenge1.ui.MainRoute
import com.example.vasganchalenge1.ui.MainScreen
import com.example.vasganchalenge1.ui.MainViewModel
import com.example.vasganchalenge1.ui.settings.SettingsScreen
import com.example.vasganchalenge1.ui.settings.SettingsViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = Routes.Main) {
        composable(Routes.Main) {
            val vm = hiltViewModel<MainViewModel>()
            MainRoute(
                vm = vm,
                onOpenSettings = { navController.navigate(Routes.Settings) }
            )
        }

        composable(Routes.Settings) {
            val vm = hiltViewModel<SettingsViewModel>()
            val state = vm.state.collectAsState().value

            SettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onSave = { vm.save { navController.popBackStack() } },
                onEnabledChange = vm::setEnabled,
                onFormatChange = vm::setFormat,
                onLengthLimitChange = vm::setLengthLimit,
                onStopChange = vm::setStopSequence,
                onMaxTokensChange = vm::setMaxTokensText
            )
        }
    }
}