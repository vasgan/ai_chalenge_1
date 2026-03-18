package com.example.vasganchalenge1.rag.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vasganchalenge1.rag.model.RagQualityMode
import com.example.vasganchalenge1.rag.model.RagRetrievalConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatRagDataStore by preferencesDataStore(name = "chat_rag_settings")

@Singleton
class ChatRagSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun observeConfig(chatId: String): Flow<ChatRagSettings> {
        val enabledKey = ragEnabledKey(chatId)
        val modeKey = ragModeKey(chatId)
        val topKBeforeKey = ragTopKBeforeKey(chatId)
        val topKAfterKey = ragTopKAfterKey(chatId)
        val thresholdKey = ragThresholdKey(chatId)
        return context.chatRagDataStore.data.map { prefs ->
            ChatRagSettings(
                enabled = prefs[enabledKey] ?: false,
                retrievalConfig = RagRetrievalConfig(
                    mode = RagQualityMode.fromRaw(prefs[modeKey]),
                    topKBefore = (prefs[topKBeforeKey] ?: RagRetrievalConfig().topKBefore)
                        .coerceAtLeast(1),
                    topKAfter = (prefs[topKAfterKey] ?: RagRetrievalConfig().topKAfter)
                        .coerceAtLeast(1),
                    similarityThreshold = (prefs[thresholdKey]
                        ?: RagRetrievalConfig().similarityThreshold)
                        .coerceIn(0f, 1f)
                )
            )
        }.distinctUntilChanged()
    }

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

    suspend fun setQualityMode(chatId: String, mode: RagQualityMode) {
        val key = ragModeKey(chatId)
        context.chatRagDataStore.edit { prefs ->
            prefs[key] = mode.name
        }
    }

    suspend fun setTopKBefore(chatId: String, value: Int) {
        val key = ragTopKBeforeKey(chatId)
        context.chatRagDataStore.edit { prefs ->
            prefs[key] = value.coerceAtLeast(1)
        }
    }

    suspend fun setTopKAfter(chatId: String, value: Int) {
        val key = ragTopKAfterKey(chatId)
        context.chatRagDataStore.edit { prefs ->
            prefs[key] = value.coerceAtLeast(1)
        }
    }

    suspend fun setSimilarityThreshold(chatId: String, value: Float) {
        val key = ragThresholdKey(chatId)
        context.chatRagDataStore.edit { prefs ->
            prefs[key] = value.coerceIn(0f, 1f)
        }
    }

    private fun ragEnabledKey(chatId: String) = booleanPreferencesKey("rag_enabled_$chatId")
    private fun ragModeKey(chatId: String) = stringPreferencesKey("rag_mode_$chatId")
    private fun ragTopKBeforeKey(chatId: String) = intPreferencesKey("rag_top_k_before_$chatId")
    private fun ragTopKAfterKey(chatId: String) = intPreferencesKey("rag_top_k_after_$chatId")
    private fun ragThresholdKey(chatId: String) = floatPreferencesKey("rag_threshold_$chatId")
}

data class ChatRagSettings(
    val enabled: Boolean = false,
    val retrievalConfig: RagRetrievalConfig = RagRetrievalConfig()
)
