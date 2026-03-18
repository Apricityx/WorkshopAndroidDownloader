package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.apricityx.workshop.steam.protocol.STEAM_LANGUAGE_ENGLISH
import top.apricityx.workshop.steam.protocol.STEAM_LANGUAGE_SIMPLIFIED_CHINESE

class SteamLanguagePreferenceTest {
    @Test
    fun toSteamPublishedFileLanguage_mapsSupportedPreferences() {
        assertThat(SteamLanguagePreference.English.toSteamPublishedFileLanguage())
            .isEqualTo(STEAM_LANGUAGE_ENGLISH)
        assertThat(SteamLanguagePreference.SimplifiedChinese.toSteamPublishedFileLanguage())
            .isEqualTo(STEAM_LANGUAGE_SIMPLIFIED_CHINESE)
    }
}
