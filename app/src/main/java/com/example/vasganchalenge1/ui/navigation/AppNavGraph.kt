package com.example.vasganchalenge1.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.ui.FactsScreen
import com.example.vasganchalenge1.ui.ChatViewModel
import com.example.vasganchalenge1.ui.ChatScreen
import com.example.vasganchalenge1.ui.branches.BranchesScreen
import com.example.vasganchalenge1.ui.branches.BranchesViewModel
import com.example.vasganchalenge1.ui.chats.ChatListScreen
import com.example.vasganchalenge1.ui.chats.ChatListViewModel
import com.example.vasganchalenge1.ui.profiles.ProfileListScreen
import com.example.vasganchalenge1.ui.profiles.ProfileListViewModel
import com.example.vasganchalenge1.ui.profiles.ProfileSettingsScreen
import com.example.vasganchalenge1.ui.profiles.ProfileSettingsViewModel
import com.example.vasganchalenge1.ui.mcp.McpServerScreen
import com.example.vasganchalenge1.ui.mcp.McpServerViewModel
import com.example.vasganchalenge1.ui.settings.SettingsScreen
import com.example.vasganchalenge1.ui.settings.SettingsViewModel
import com.example.vasganchalenge1.ui.tasks.TaskListScreen
import com.example.vasganchalenge1.ui.tasks.TaskListViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = Routes.Profiles) {

        composable(Routes.Profiles) {
            val vm = hiltViewModel<ProfileListViewModel>()
            val state = vm.state.collectAsState().value

            ProfileListScreen(
                state = state,
                onOpenProfile = { profileId ->
                    navController.navigate(Routes.tasks(profileId))
                },
                onCreateProfile = { title, longTermMode ->
                    vm.createProfile(title, longTermMode) { profileId ->
                        navController.navigate(Routes.tasks(profileId))
                    }
                }
            )
        }

        composable(
            route = "${Routes.Tasks}/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) {
            val vm = hiltViewModel<TaskListViewModel>()
            val state = vm.state.collectAsState().value

            TaskListScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenProfileSettings = {
                    navController.navigate(Routes.profileSettings(state.profileId))
                },
                onOpenTask = { taskId ->
                    navController.navigate(Routes.chatList(taskId))
                },
                onCreateTask = { title ->
                    vm.createTask(title) { taskId ->
                        navController.navigate(Routes.chatList(taskId))
                    }
                }
            )
        }

        composable(
            route = "${Routes.ProfileSettings}/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) {
            val vm = hiltViewModel<ProfileSettingsViewModel>()
            val state = vm.state.collectAsState().value

            ProfileSettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onSave = { vm.save { navController.popBackStack() } },
                onProfileDescriptionChange = vm::setProfileDescription,
                onCommunicationLanguageChange = vm::setCommunicationLanguage,
                onAddCustomField = vm::addCustomField,
                onCustomFieldKeyChange = vm::updateCustomFieldKey,
                onCustomFieldValueChange = vm::updateCustomFieldValue,
                onRemoveCustomField = vm::removeCustomField,
                onAddInvariant = vm::addInvariant,
                onInvariantChange = vm::updateInvariant,
                onRemoveInvariant = vm::removeInvariant
            )
        }

        composable(
            route = "${Routes.ChatList}/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) {
            val vm = hiltViewModel<ChatListViewModel>()
            val state = vm.state.collectAsState().value

            ChatListScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId ->
                    navController.navigate(Routes.chat(chatId))
                },
                onCreateChat = {
                    vm.createChat { chatId ->
                        navController.navigate(Routes.chat(chatId))
                    }
                },
                onDeleteChat = { chatId -> vm.deleteChat(chatId) },
            )
        }

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
                onOpenBranches = { navController.navigate(Routes.branches(state.chatId)) },
                onCreateBranch = { messageId ->
                    vm.createBranchFrom(messageId) { newChatId ->
                        navController.navigate(Routes.chat(newChatId))
                    }
                },
                onOpenSettings = { navController.navigate(Routes.settings(state.chatId)) },
                onOpenFacts = { navController.navigate(Routes.facts(state.chatId)) },
                onOpenMcp = { navController.navigate(Routes.mcpServer(state.chatId)) },
                onPauseTask = vm::pauseTask,
                onResumeTask = vm::resumeTask,
                onCancelTask = vm::cancelTask,
                onResetTask = vm::resetTask
            )
        }

        composable(
            route = "${Routes.McpServer}/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) {
            val vm = hiltViewModel<McpServerViewModel>()
            val state = vm.state.collectAsState().value

            McpServerScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onConnect = vm::connectAndLoadTools
            )
        }

        composable(
            route = "${Routes.Facts}/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) {
            val vm = hiltViewModel<ChatViewModel>()
            val state = vm.state.collectAsState().value

            FactsScreen(
                longTermMode = state.longTermMode,
                profileDescription = state.profileDescription,
                communicationLanguage = state.communicationLanguage,
                longTermFields = state.longTermFields,
                invariants = state.invariants,
                workingMemoryContext = state.workingMemoryContext,
                totalUsageTokens = state.metrics.firstOrNull()?.totalUsageToken ?: 0,
                userMessagesCount = state.messages.count { it.role == Role.USER },
                assistantMessagesCount = state.messages.count { it.role == Role.ASSISTANT },
                totalMessagesCount = state.messages.size,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.Branches}/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) {
            val vm = hiltViewModel<BranchesViewModel>()
            val state = vm.state.collectAsState().value

            BranchesScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenChat = { branchChatId ->
                    navController.navigate(Routes.chat(branchChatId))
                }
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
                contextModeOptions = vm.contextModeOptions,
                onModelChange = vm::setModel,
                onBack = { navController.popBackStack() },
                onSave = { vm.save { navController.popBackStack() } },
                onEnabledChange = vm::setEnabled,
                onContextModeChange = vm::setContextMode,
                onTemperatureChange = vm::setTemperature,
                onFormatChange = vm::setFormat,
                onLengthLimitChange = vm::setLengthLimit,
                onStopChange = vm::setStopSequence,
                onMaxTokensChange = vm::setMaxTokensText
            )
        }
    }
}
