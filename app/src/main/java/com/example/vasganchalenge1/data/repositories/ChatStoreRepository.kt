package com.example.vasganchalenge1.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vasganchalenge1.data.Chat
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "chat_prefs")
@Singleton
class ChatStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    private val key = stringPreferencesKey("chats")

    private val listType = Types.newParameterizedType(List::class.java, Chat::class.java)
    private val adapter = moshi.adapter<List<Chat>>(listType)

    val chatsFlow: Flow<List<Chat>> = context.dataStore.data.map { prefs ->
        val json = prefs[key]
        if (json.isNullOrBlank()) emptyList() else adapter.fromJson(json).orEmpty()
    }

    suspend fun saveAll(chats: List<Chat>) {
        val json = adapter.toJson(chats)
        context.dataStore.edit { it[key] = json }
    }

    suspend fun createChat(title: String): Chat {
        val newChat = Chat(title = title)
        val chats = chatsFlow.first()
        saveAll(listOf(newChat) + chats)
        return newChat
    }

    suspend fun deleteChat(chatId: String) {
        val chats = chatsFlow.first()
        saveAll(chats.filterNot { it.id == chatId })
    }

    suspend fun updateChat(updated: Chat) {
        val chats = chatsFlow.first()
        val newList = chats.map { if (it.id == updated.id) updated else it }
        saveAll(newList)
    }

    suspend fun createBranch(sourceChatId: String, fromMessageId: Long): Chat {
        val chats = chatsFlow.first()
        val source = chats.firstOrNull { it.id == sourceChatId } ?: error("Chat not found")
        val checkpointIndex = source.messages.indexOfFirst { it.id == fromMessageId }
        require(checkpointIndex >= 0) { "Checkpoint message not found" }

        val existingBranchCount = chats.count {
            it.parentChatId == source.id && it.branchedFromMessageId == fromMessageId
        }
        val newBranch = Chat(
            title = "${source.title} / Ветка ${existingBranchCount + 1}",
            rootChatId = source.rootChatId,
            parentChatId = source.id,
            branchedFromMessageId = fromMessageId,
            settings = source.settings,
            facts = "",
            factsMessageCount = 0,
            messages = source.messages.take(checkpointIndex + 1),
            metrics = emptyList()
        )

        saveAll(listOf(newBranch) + chats)
        return newBranch
    }

    suspend fun getChat(chatId: String): Chat? =
        chatsFlow.first().firstOrNull { it.id == chatId }
}
