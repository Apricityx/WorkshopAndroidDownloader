package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkshopScreenDestinationTest {
    @Test
    fun showsDownloadCenterShortcut_isFalseOnDownloadAndSettingsScreens() {
        assertThat(WorkshopScreenDestination.DownloadCenter.showsDownloadCenterShortcut()).isFalse()
        assertThat(WorkshopScreenDestination.DownloadTaskDetail.showsDownloadCenterShortcut()).isFalse()
        assertThat(WorkshopScreenDestination.Settings.showsDownloadCenterShortcut()).isFalse()
        assertThat(WorkshopScreenDestination.BaiduTranslationApiKey.showsDownloadCenterShortcut()).isFalse()
    }

    @Test
    fun showsDownloadCenterShortcut_isTrueOutsideDownloadAndSettingsScreens() {
        assertThat(WorkshopScreenDestination.GameLibrary.showsDownloadCenterShortcut()).isTrue()
        assertThat(WorkshopScreenDestination.ModLibrary.showsDownloadCenterShortcut()).isTrue()
        assertThat(WorkshopScreenDestination.GameWorkshop.showsDownloadCenterShortcut()).isTrue()
    }

    @Test
    fun showsSettingsShortcut_isFalseOnDownloadAndSettingsScreens() {
        assertThat(WorkshopScreenDestination.DownloadCenter.showsSettingsShortcut()).isFalse()
        assertThat(WorkshopScreenDestination.DownloadTaskDetail.showsSettingsShortcut()).isFalse()
        assertThat(WorkshopScreenDestination.Settings.showsSettingsShortcut()).isFalse()
        assertThat(WorkshopScreenDestination.BaiduTranslationApiKey.showsSettingsShortcut()).isFalse()
    }

    @Test
    fun showsSettingsShortcut_isTrueOutsideDownloadAndSettingsScreens() {
        assertThat(WorkshopScreenDestination.GameLibrary.showsSettingsShortcut()).isTrue()
        assertThat(WorkshopScreenDestination.ModLibrary.showsSettingsShortcut()).isTrue()
        assertThat(WorkshopScreenDestination.GameWorkshop.showsSettingsShortcut()).isTrue()
    }
}
