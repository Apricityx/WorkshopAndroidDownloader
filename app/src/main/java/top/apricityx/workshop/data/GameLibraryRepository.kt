package top.apricityx.workshop.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GameLibraryRepository(
    context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    },
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun loadGameIds(): List<UInt> = withContext(Dispatchers.IO) {
        val persistedIds = preferences.getString(KEY_GAME_IDS, null)
            .orEmpty()
            .split(',')
            .mapNotNull { token -> token.trim().takeIf(String::isNotEmpty)?.toUIntOrNull() }
        if (persistedIds.isNotEmpty()) {
            return@withContext persistedIds
        }

        return@withContext loadGames().map(SteamGame::appId)
    }

    suspend fun loadGames(): List<SteamGame> = withContext(Dispatchers.IO) {
        val persistedGames = preferences.getString(KEY_GAMES_JSON, null)
            ?.takeIf(String::isNotBlank)
            ?: return@withContext emptyList()

        return@withContext runCatching {
            json.decodeFromString<List<SteamGame>>(persistedGames)
        }.getOrDefault(emptyList())
    }

    suspend fun addGame(game: SteamGame) = withContext(Dispatchers.IO) {
        val ids = loadGameIds().toMutableList()
        if (game.appId !in ids) {
            ids += game.appId
        }
        val games = loadGames().toMutableList()
        val existingIndex = games.indexOfFirst { it.appId == game.appId }
        if (existingIndex >= 0) {
            games[existingIndex] = game
        } else {
            games += game
        }
        saveLibrary(ids, games)
    }

    suspend fun removeGame(appId: UInt) = withContext(Dispatchers.IO) {
        saveLibrary(
            appIds = loadGameIds().filterNot { it == appId },
            games = loadGames().filterNot { it.appId == appId },
        )
    }

    suspend fun replaceGameIds(appIds: List<UInt>) = withContext(Dispatchers.IO) {
        saveGameIds(appIds.distinct())
    }

    suspend fun replaceGames(games: List<SteamGame>) = withContext(Dispatchers.IO) {
        saveLibrary(
            appIds = games.map(SteamGame::appId),
            games = games.distinctBy(SteamGame::appId),
        )
    }

    private fun saveGameIds(appIds: List<UInt>) {
        preferences.edit()
            .putString(KEY_GAME_IDS, appIds.joinToString(","))
            .apply()
    }

    private fun saveLibrary(
        appIds: List<UInt>,
        games: List<SteamGame>,
    ) {
        val distinctIds = appIds.distinct()
        preferences.edit()
            .putString(KEY_GAME_IDS, distinctIds.joinToString(","))
            .putString(KEY_GAMES_JSON, json.encodeToString(games.distinctBy(SteamGame::appId)))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "game_library"
        const val KEY_GAME_IDS = "game_ids"
        const val KEY_GAMES_JSON = "games_json"
    }
}
