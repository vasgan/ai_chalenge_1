package com.example.vasganchalenge1.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatModelDataStore by preferencesDataStore(name = "chat_model_selection")

@Singleton
class ChatModelSelectionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun observe(chatId: String): Flow<ModelType?> {
        val key = modelKey(chatId)
        return context.chatModelDataStore.data.map { prefs ->
            ModelType.fromRaw(prefs[key])
        }
    }

    suspend fun set(chatId: String, modelType: ModelType) {
        val key = modelKey(chatId)
        context.chatModelDataStore.edit { prefs ->
            prefs[key] = modelType.name
        }
    }

    private fun modelKey(chatId: String) = stringPreferencesKey("chat_model_type_$chatId")
}

