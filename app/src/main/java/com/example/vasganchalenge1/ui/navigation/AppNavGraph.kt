package com.example.vasganchalenge1.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vasganchalenge1.ui.ChatViewModel
import com.example.vasganchalenge1.ui.ChatScreen
import com.example.vasganchalenge1.ui.chats.ChatListScreen
import com.example.vasganchalenge1.ui.chats.ChatListViewModel
import com.example.vasganchalenge1.ui.settings.SettingsScreen
import com.example.vasganchalenge1.ui.settings.SettingsViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = Routes.ChatList) {

        // 1) Список чатов
        composable(Routes.ChatList) {
            val vm = hiltViewModel<ChatListViewModel>()
            val state = vm.state.collectAsState().value

            ChatListScreen(
                state = state,
                onOpenChat = { chatId ->
                    navController.navigate(Routes.chat(chatId))
                },
                onCreateChat = { vm.createChat() },
                onDeleteChat = { chatId -> vm.deleteChat(chatId) },
              //  onOpenSettings = { ... } // настройки теперь привязаны к chatId
            )
        }

        // 2) Экран конкретного чата
        composable(
            route = "${Routes.Chat}/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) {
            val vm = hiltViewModel<ChatViewModel>()
            val state = vm.state.collectAsState().value

            ChatScreen( // это твой переработанный MainScreen (чат)
                state = state,
                onInputChange = vm::onInputChange,
                onSendClick = vm::onSendClick,
          //      onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.settings(state.chatId)) }
            )
        }

        // 3) Settings — оставляем как есть
        composable(
            route = "${Routes.Settings}/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) {
            val vm = hiltViewModel<SettingsViewModel>()
            val state = vm.state.collectAsState().value

            SettingsScreen(
                state = state,
                modelOptions = vm.modelOptions,
                onModelChange = vm::setModel,
                onBack = { navController.popBackStack() },
                onSave = { vm.save { navController.popBackStack() } },
                onEnabledChange = vm::setEnabled,
                onTemperatureChange = vm::setTemperature,
                onFormatChange = vm::setFormat,
                onLengthLimitChange = vm::setLengthLimit,
                onStopChange = vm::setStopSequence,
                onMaxTokensChange = vm::setMaxTokensText
            )
        }
    }
}
