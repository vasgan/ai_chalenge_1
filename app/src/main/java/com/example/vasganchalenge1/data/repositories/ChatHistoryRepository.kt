package com.example.vasganchalenge1.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vasganchalenge1.data.UiChatMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "chat_prefs")

@Singleton
class ChatHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {

    private val key = stringPreferencesKey("chat_history")

    private val adapter = moshi.adapter<List<UiChatMessage>>(
        Types.newParameterizedType(
            List::class.java,
            UiChatMessage::class.java
        )
    )

    val historyFlow: Flow<List<UiChatMessage>> =
        context.dataStore.data.map { prefs ->
            prefs[key]?.let { json ->
                adapter.fromJson(json)
            } ?: emptyList()
        }

    suspend fun saveHistory(messages: List<UiChatMessage>) {
        val json = adapter.toJson(messages)
        context.dataStore.edit { prefs ->
            prefs[key] = json
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(key) }
    }
}