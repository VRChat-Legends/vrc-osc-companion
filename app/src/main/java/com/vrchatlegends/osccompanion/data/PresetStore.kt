package com.vrchatlegends.osccompanion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.presetDataStore: DataStore<Preferences> by preferencesDataStore(name = "presets")

@Serializable
data class ChatboxPreset(
    val id: String,
    val label: String,
    val text: String,
)

/** Lines that cycle through the chatbox when rotation is on. */
@Serializable
data class StatusLine(
    val id: String,
    val text: String,
    val enabled: Boolean = true,
)

class PresetStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val CHATBOX = stringPreferencesKey("chatbox_presets")
        val ROTATION = stringPreferencesKey("rotation_lines")
    }

    val chatboxPresets: Flow<List<ChatboxPreset>> = context.presetDataStore.data.map { p ->
        decode(p[Keys.CHATBOX], ChatboxPreset.serializer(), DEFAULT_PRESETS)
    }

    val rotationLines: Flow<List<StatusLine>> = context.presetDataStore.data.map { p ->
        decode(p[Keys.ROTATION], StatusLine.serializer(), emptyList())
    }

    suspend fun saveChatboxPresets(list: List<ChatboxPreset>) {
        context.presetDataStore.edit {
            it[Keys.CHATBOX] = json.encodeToString(ListSerializer(ChatboxPreset.serializer()), list)
        }
    }

    suspend fun saveRotationLines(list: List<StatusLine>) {
        context.presetDataStore.edit {
            it[Keys.ROTATION] = json.encodeToString(ListSerializer(StatusLine.serializer()), list)
        }
    }

    private fun <T> decode(
        raw: String?,
        serializer: kotlinx.serialization.KSerializer<T>,
        fallback: List<T>,
    ): List<T> {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { json.decodeFromString(ListSerializer(serializer), raw) }.getOrDefault(fallback)
    }

    companion object {
        val DEFAULT_PRESETS = listOf(
            ChatboxPreset("afk", "AFK", "AFK - back soon"),
            ChatboxPreset("brb", "BRB", "BRB"),
            ChatboxPreset("gm", "Hello", "Hi! o/"),
            ChatboxPreset("music", "Listening", "Listening to music"),
            ChatboxPreset("sleep", "Sleeping", "Sleeping, please be quiet"),
            ChatboxPreset("vrcl", "Legends", "VRChat Legends | discord.gg/6xPkZ7Dxp9"),
        )
    }
}
