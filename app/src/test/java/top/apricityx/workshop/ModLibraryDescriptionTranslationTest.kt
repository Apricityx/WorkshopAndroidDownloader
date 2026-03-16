package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModLibraryDescriptionTranslationTest {
    @Test
    fun shouldPreserveModLibraryDescriptionTranslation_isTrueForSameModAndDescription() {
        val current = sampleGroup(description = "Same description")
        val refreshed = sampleGroup(description = "Same description")

        assertThat(
            shouldPreserveModLibraryDescriptionTranslation(
                previous = current,
                next = refreshed,
            ),
        ).isTrue()
    }

    @Test
    fun shouldPreserveModLibraryDescriptionTranslation_isFalseWhenDescriptionChanges() {
        val current = sampleGroup(description = "Old description")
        val refreshed = sampleGroup(description = "New description")

        assertThat(
            shouldPreserveModLibraryDescriptionTranslation(
                previous = current,
                next = refreshed,
            ),
        ).isFalse()
    }

    @Test
    fun shouldPreserveModLibraryDescriptionTranslation_isFalseWhenSelectedModChanges() {
        val current = sampleGroup(publishedFileId = 1uL, description = "Description")
        val refreshed = sampleGroup(publishedFileId = 2uL, description = "Description")

        assertThat(
            shouldPreserveModLibraryDescriptionTranslation(
                previous = current,
                next = refreshed,
            ),
        ).isFalse()
    }

    private fun sampleGroup(
        publishedFileId: ULong = 3677098410uL,
        description: String,
    ) = DownloadedModGroup(
        appId = 646570u,
        publishedFileId = publishedFileId,
        gameTitle = "Slay the Spire",
        itemTitle = "Skip The Spire",
        description = description,
        versions = emptyList(),
    )
}
