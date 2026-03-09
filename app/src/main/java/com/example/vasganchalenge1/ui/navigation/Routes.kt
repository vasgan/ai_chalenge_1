package com.example.vasganchalenge1.ui.navigation

object Routes {
    const val Profiles = "profiles"
    const val ProfileSettings = "profile_settings"
    const val Tasks = "tasks"
    const val ChatList = "chat_list"
    const val Chat = "chat"
    const val Settings = "settings"
    const val Facts = "facts"
    const val Branches = "branches"
    const val McpServer = "mcp_server"

    fun profileSettings(profileId: String) = "$ProfileSettings/$profileId"
    fun tasks(profileId: String) = "$Tasks/$profileId"
    fun chatList(taskId: String) = "$ChatList/$taskId"
    fun chat(chatId: String) = "$Chat/$chatId"
    fun settings(chatId: String) = "$Settings/$chatId"
    fun facts(chatId: String) = "$Facts/$chatId"
    fun branches(chatId: String) = "$Branches/$chatId"
    fun mcpServer(chatId: String) = "$McpServer/$chatId"
}
