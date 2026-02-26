package com.example.vasganchalenge1.ui.navigation

object Routes {
    const val ChatList = "chat_list"
    const val Chat = "chat"
    const val Settings = "settings"
    const val Summary = "summary"

    fun chat(chatId: String) = "$Chat/$chatId"
    fun settings(chatId: String) = "$Settings/$chatId"
    fun summary(chatId: String) = "$Summary/$chatId"
}
