package com.example.vasganchalenge1.rag.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vasganchalenge1.rag.model.EmbeddingProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ragDataStore by preferencesDataStore(name = "rag_settings")

@Singleton
class RagSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val providerKey = stringPreferencesKey("embedding_provider_type")

    val selectedProviderFlow: Flow<EmbeddingProviderType> = context.ragDataStore.data
        .map { prefs -> EmbeddingProviderType.fromRaw(prefs[providerKey]) }

    suspend fun setSelectedProvider(type: EmbeddingProviderType) {
        context.ragDataStore.edit { prefs ->
            prefs[providerKey] = type.name
        }
    }
}
