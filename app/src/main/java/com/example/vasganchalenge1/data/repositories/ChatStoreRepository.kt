package com.example.vasganchalenge1.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vasganchalenge1.data.Chat
import com.example.vasganchalenge1.data.LongTermMemory
import com.example.vasganchalenge1.data.LongTermMode
import com.example.vasganchalenge1.data.Profile
import com.example.vasganchalenge1.data.TaskItem
import com.example.vasganchalenge1.data.WorkingMemoryState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "chat_prefs")

@Singleton
class ChatStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    private val key = stringPreferencesKey("profiles")

    private val listType = Types.newParameterizedType(List::class.java, Profile::class.java)
    private val adapter = moshi.adapter<List<Profile>>(listType)

    val profilesFlow: Flow<List<Profile>> = context.dataStore.data.map { prefs ->
        val json = prefs[key]
        if (json.isNullOrBlank()) emptyList() else adapter.fromJson(json).orEmpty()
    }

    suspend fun saveAll(profiles: List<Profile>) {
        val json = adapter.toJson(profiles)
        context.dataStore.edit { it[key] = json }
    }

    suspend fun createProfile(title: String, longTermMode: LongTermMode): Profile {
        val newProfile = Profile(
            title = title,
            longTermMemory = LongTermMemory(mode = longTermMode)
        )
        val profiles = profilesFlow.first()
        saveAll(listOf(newProfile) + profiles)
        return newProfile
    }

    suspend fun createTask(profileId: String, title: String): TaskItem {
        val newTask = TaskItem(title = title)
        val profiles = profilesFlow.first()
        val updatedProfiles = profiles.map { profile ->
            if (profile.id != profileId) profile
            else profile.copy(
                tasks = listOf(newTask) + profile.tasks,
                updatedAt = System.currentTimeMillis()
            )
        }
        saveAll(updatedProfiles)
        return newTask
    }

    suspend fun createChat(taskId: String, title: String): Chat {
        val newChat = Chat(title = title)
        val profiles = profilesFlow.first()
        saveAll(
            profiles.map { profile ->
                profile.copy(
                    tasks = profile.tasks.map { task ->
                        if (task.id != taskId) task
                        else task.copy(
                            chats = listOf(newChat) + task.chats,
                            updatedAt = System.currentTimeMillis()
                        )
                    },
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
        return newChat
    }

    suspend fun deleteChat(chatId: String) {
        val profiles = profilesFlow.first()
        saveAll(
            profiles.map { profile ->
                profile.copy(
                    tasks = profile.tasks.map { task ->
                        task.copy(
                            chats = task.chats.filterNot { it.id == chatId },
                            updatedAt = System.currentTimeMillis()
                        )
                    },
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
    }

    suspend fun updateChat(updated: Chat) {
        val profiles = profilesFlow.first()
        saveAll(
            profiles.map { profile ->
                profile.copy(
                    tasks = profile.tasks.map { task ->
                        task.copy(
                            chats = task.chats.map { if (it.id == updated.id) updated else it },
                            updatedAt = System.currentTimeMillis()
                        )
                    },
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
    }

    suspend fun createBranch(sourceChatId: String, fromMessageId: Long): Chat {
        val profiles = profilesFlow.first()
        val sourceChat = profiles.asSequence()
            .flatMap { it.tasks.asSequence() }
            .flatMap { it.chats.asSequence() }
            .firstOrNull { it.id == sourceChatId } ?: error("Chat not found")

        val parentTaskId = profiles.asSequence()
            .flatMap { profile -> profile.tasks.asSequence() }
            .firstOrNull { task -> task.chats.any { it.id == sourceChatId } }
            ?.id ?: error("Task not found")

        val checkpointIndex = sourceChat.messages.indexOfFirst { it.id == fromMessageId }
        require(checkpointIndex >= 0) { "Checkpoint message not found" }

        val existingBranchCount = profiles.asSequence()
            .flatMap { it.tasks.asSequence() }
            .flatMap { it.chats.asSequence() }
            .count { it.parentChatId == sourceChat.id && it.branchedFromMessageId == fromMessageId }

        val newBranch = Chat(
            title = "${sourceChat.title} / Ветка ${existingBranchCount + 1}",
            rootChatId = sourceChat.rootChatId,
            parentChatId = sourceChat.id,
            branchedFromMessageId = fromMessageId,
            settings = sourceChat.settings,
            facts = "",
            factsMessageCount = 0,
            messages = sourceChat.messages.take(checkpointIndex + 1),
            metrics = emptyList()
        )

        val updatedProfiles = profiles.map { profile ->
            profile.copy(
                tasks = profile.tasks.map { task ->
                    if (task.id != parentTaskId) task
                    else task.copy(
                        chats = listOf(newBranch) + task.chats,
                        updatedAt = System.currentTimeMillis()
                    )
                },
                updatedAt = System.currentTimeMillis()
            )
        }
        saveAll(updatedProfiles)
        return newBranch
    }

    suspend fun getProfile(profileId: String): Profile? =
        profilesFlow.first().firstOrNull { it.id == profileId }

    suspend fun updateProfileLongTerm(profileId: String, longTermMemory: LongTermMemory) {
        val profiles = profilesFlow.first()
        saveAll(
            profiles.map { profile ->
                if (profile.id != profileId) profile
                else profile.copy(
                    longTermMemory = longTermMemory,
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
    }

    suspend fun updateProfileInvariants(profileId: String, invariants: List<String>) {
        val normalized = invariants.map { it.trim().take(280) }.filter { it.isNotBlank() }
        val profiles = profilesFlow.first()
        saveAll(
            profiles.map { profile ->
                if (profile.id != profileId) profile
                else profile.copy(
                    invariants = normalized,
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
    }

    suspend fun getProfileByChatId(chatId: String): Profile? =
        profilesFlow.first().firstOrNull { profile ->
            profile.tasks.any { task -> task.chats.any { it.id == chatId } }
        }

    suspend fun getTask(taskId: String): TaskItem? =
        profilesFlow.first().asSequence()
            .flatMap { it.tasks.asSequence() }
            .firstOrNull { it.id == taskId }

    suspend fun getTaskByChatId(chatId: String): TaskItem? =
        profilesFlow.first().asSequence()
            .flatMap { it.tasks.asSequence() }
            .firstOrNull { task -> task.chats.any { it.id == chatId } }

    suspend fun updateTaskWorkingMemory(taskId: String, workingMemory: WorkingMemoryState) {
        val profiles = profilesFlow.first()
        saveAll(
            profiles.map { profile ->
                profile.copy(
                    tasks = profile.tasks.map { task ->
                        if (task.id != taskId) task
                        else task.copy(
                            workingMemory = workingMemory.copy(taskId = task.id),
                            updatedAt = System.currentTimeMillis()
                        )
                    },
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
    }

    suspend fun getChat(chatId: String): Chat? =
        profilesFlow.first().asSequence()
            .flatMap { it.tasks.asSequence() }
            .flatMap { it.chats.asSequence() }
            .firstOrNull { it.id == chatId }
}
