package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModLibraryDisplayModeTest {
    @Test
    fun next_cyclesThroughAllModes() {
        assertThat(ModLibraryDisplayMode.CompactList.next()).isEqualTo(ModLibraryDisplayMode.Overview)
        assertThat(ModLibraryDisplayMode.Overview.next()).isEqualTo(ModLibraryDisplayMode.LargePreview)
        assertThat(ModLibraryDisplayMode.LargePreview.next()).isEqualTo(ModLibraryDisplayMode.CompactList)
    }

    @Test
    fun fromStorageValue_fallsBackToCompactListForUnknownValue() {
        assertThat(ModLibraryDisplayMode.fromStorageValue("unexpected")).isEqualTo(ModLibraryDisplayMode.CompactList)
    }
}
