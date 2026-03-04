package com.example.vasganchalenge1.data.taskfsm

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.taskFsmDataStore by preferencesDataStore(name = "task_fsm_prefs")

@Singleton
class TaskStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) {
    private val json = TaskStateJson(moshi)

    suspend fun get(taskId: String): TaskState? {
        val key = stringPreferencesKey(keyFor(taskId))
        return context.taskFsmDataStore.data
            .map { prefs -> prefs[key] }
            .first()
            ?.let(json::fromJson)
    }

    suspend fun save(state: TaskState) {
        val key = stringPreferencesKey(keyFor(state.taskId))
        context.taskFsmDataStore.edit { prefs ->
            prefs[key] = json.toJson(state)
        }
    }

    suspend fun clear(taskId: String) {
        val key = stringPreferencesKey(keyFor(taskId))
        context.taskFsmDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    private fun keyFor(taskId: String): String = "current_task_$taskId"
}
