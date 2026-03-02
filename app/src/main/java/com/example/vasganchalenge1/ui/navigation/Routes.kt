package com.example.vasganchalenge1.ui.navigation

object Routes {
    const val ChatList = "chat_list"
    const val Chat = "chat"
    const val Settings = "settings"
    const val Facts = "facts"
    const val Branches = "branches"

    fun chat(chatId: String) = "$Chat/$chatId"
    fun settings(chatId: String) = "$Settings/$chatId"
    fun facts(chatId: String) = "$Facts/$chatId"
    fun branches(chatId: String) = "$Branches/$chatId"
}
