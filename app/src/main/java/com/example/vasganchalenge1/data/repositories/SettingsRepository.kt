package com.example.vasganchalenge1.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


private val Context.dataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val enabled: Boolean = false,
    val format: String = "",
    val lengthLimit: String = "",
    val stopSequence: String = "###END###",
    val maxTokens: Int = 200,
    val temperature: String = ""
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val format = stringPreferencesKey("format")
        val lengthLimit = stringPreferencesKey("length_limit")
        val stopSequence = stringPreferencesKey("stop_sequence")
        val maxTokens = intPreferencesKey("max_tokens")
        val temperature = stringPreferencesKey("temperature")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            enabled = prefs[Keys.enabled] ?: false,
            format = prefs[Keys.format] ?: "",
            lengthLimit = prefs[Keys.lengthLimit] ?: "",
            stopSequence = prefs[Keys.stopSequence] ?: "###END###",
            maxTokens = prefs[Keys.maxTokens] ?: 200,
            temperature = prefs[Keys.temperature] ?: "0.7"
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.enabled] = settings.enabled
            prefs[Keys.format] = settings.format
            prefs[Keys.lengthLimit] = settings.lengthLimit
            prefs[Keys.stopSequence] = settings.stopSequence
            prefs[Keys.maxTokens] = settings.maxTokens
            prefs[Keys.temperature] = settings.temperature
        }
    }
}