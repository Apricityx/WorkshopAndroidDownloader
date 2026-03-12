package top.apricityx.workshop.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class SteamGameParsersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseSearchSuggestionIds_returnsDistinctIdsInOrder() {
        val payload = """
            <a class="match" data-ds-appid="646570"><div class="match_name">Slay the Spire</div></a>
            <a class="match" data-ds-appid="4000"><div class="match_name">Garry's Mod</div></a>
            <a class="match" data-ds-appid="646570"><div class="match_name">Slay the Spire</div></a>
        """.trimIndent()

        assertThat(SteamGameParsers.parseSearchSuggestionIds(payload))
            .containsExactly(646570u, 4000u)
            .inOrder()
    }

    @Test
    fun parseAppDetails_marksWorkshopSupportFromCategories() {
        val payload = """
            {
              "646570": {
                "success": true,
                "data": {
                  "steam_appid": 646570,
                  "name": "Slay the Spire",
                  "short_description": "Deckbuilder",
                  "header_image": "header-a",
                  "capsule_image": "capsule-a",
                  "categories": [
                    { "id": 2, "description": "Single-player" },
                    { "id": 30, "description": "Steam Workshop" }
                  ]
                }
              },
              "570": {
                "success": true,
                "data": {
                  "steam_appid": 570,
                  "name": "Dota 2",
                  "short_description": "MOBA",
                  "header_image": "header-b",
                  "capsule_image": "capsule-b",
                  "categories": [
                    { "id": 1, "description": "Multi-player" }
                  ]
                }
              }
            }
        """.trimIndent()

        val games = SteamGameParsers.parseAppDetails(payload, json)

        assertThat(games).hasSize(2)
        assertThat(games.first { it.appId == 646570u }.supportsWorkshop).isTrue()
        assertThat(games.first { it.appId == 570u }.supportsWorkshop).isFalse()
    }
}
