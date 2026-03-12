package top.apricityx.workshop.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameLibraryRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun loadGameIds(): List<UInt> = withContext(Dispatchers.IO) {
        preferences.getString(KEY_GAME_IDS, null)
            .orEmpty()
            .split(',')
            .mapNotNull { token -> token.trim().takeIf(String::isNotEmpty)?.toUIntOrNull() }
    }

    suspend fun addGame(appId: UInt) = withContext(Dispatchers.IO) {
        val ids = loadGameIds().toMutableList()
        if (appId !in ids) {
            ids += appId
            saveGameIds(ids)
        }
    }

    suspend fun removeGame(appId: UInt) = withContext(Dispatchers.IO) {
        saveGameIds(loadGameIds().filterNot { it == appId })
    }

    suspend fun replaceGameIds(appIds: List<UInt>) = withContext(Dispatchers.IO) {
        saveGameIds(appIds.distinct())
    }

    private fun saveGameIds(appIds: List<UInt>) {
        preferences.edit()
            .putString(KEY_GAME_IDS, appIds.joinToString(","))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "game_library"
        const val KEY_GAME_IDS = "game_ids"
    }
}
