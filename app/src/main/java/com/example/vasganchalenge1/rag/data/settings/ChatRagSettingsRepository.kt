package com.example.vasganchalenge1.rag.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatRagDataStore by preferencesDataStore(name = "chat_rag_settings")

@Singleton
class ChatRagSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun observeRagEnabled(chatId: String): Flow<Boolean> {
        val key = ragEnabledKey(chatId)
        return context.chatRagDataStore.data.map { prefs ->
            prefs[key] ?: false
        }
    }

    suspend fun setRagEnabled(chatId: String, enabled: Boolean) {
        val key = ragEnabledKey(chatId)
        context.chatRagDataStore.edit { prefs ->
            prefs[key] = enabled
        }
    }

    private fun ragEnabledKey(chatId: String) = booleanPreferencesKey("rag_enabled_$chatId")
}
