package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.apricityx.workshop.workshop.DownloadEvent

class DownloadCenterProgressRateTest {
    @Test
    fun `computeProgressDisplayUpdate emits first progress sample immediately`() {
        val update = computeProgressDisplayUpdate(
            previous = null,
            currentProgress = DownloadCenterProgressSnapshot(),
            event = progressEvent(writtenBytes = 0L, totalBytes = 1024L),
            nowMillis = 1_000L,
        )

        assertThat(update.shouldEmit).isTrue()
        assertThat(update.speedBytesPerSecond).isNull()
        assertThat(update.nextSample.lastUiUpdateTimestampMillis).isEqualTo(1_000L)
    }

    @Test
    fun `computeProgressDisplayUpdate throttles byte-only refreshes inside ui window`() {
        val previous = ProgressRateSample(
            lastObservedWrittenBytes = 0L,
            lastObservedTimestampMillis = 1_000L,
            lastUiUpdateTimestampMillis = 1_000L,
            speedAnchorWrittenBytes = 0L,
            speedAnchorTimestampMillis = 1_000L,
        )

        val update = computeProgressDisplayUpdate(
            previous = previous,
            currentProgress = DownloadCenterProgressSnapshot(
                writtenBytes = 0L,
                totalBytes = 4096L,
            ),
            event = progressEvent(writtenBytes = 2048L, totalBytes = 4096L),
            nowMillis = 1_100L,
        )

        assertThat(update.shouldEmit).isFalse()
        assertThat(update.speedBytesPerSecond).isNull()
        assertThat(update.nextSample.lastUiUpdateTimestampMillis).isEqualTo(1_000L)
    }

    @Test
    fun `computeProgressDisplayUpdate still emits when progress metadata becomes available`() {
        val previous = ProgressRateSample(
            lastObservedWrittenBytes = 4096L,
            lastObservedTimestampMillis = 1_000L,
            lastUiUpdateTimestampMillis = 1_000L,
            speedAnchorWrittenBytes = 0L,
            speedAnchorTimestampMillis = 0L,
        )

        val update = computeProgressDisplayUpdate(
            previous = previous,
            currentProgress = DownloadCenterProgressSnapshot(
                writtenBytes = 4096L,
                completedChunks = 1,
            ),
            event = progressEvent(
                writtenBytes = 4096L,
                totalBytes = 8192L,
                completedChunks = 2,
                totalChunks = 4,
            ),
            nowMillis = 1_050L,
        )

        assertThat(update.shouldEmit).isTrue()
        assertThat(update.nextSample.lastUiUpdateTimestampMillis).isEqualTo(1_050L)
    }

    @Test
    fun `computeProgressDisplayUpdate throttles chunk count changes inside ui window`() {
        val previous = ProgressRateSample(
            lastObservedWrittenBytes = 4096L,
            lastObservedTimestampMillis = 1_000L,
            lastUiUpdateTimestampMillis = 1_000L,
            speedAnchorWrittenBytes = 0L,
            speedAnchorTimestampMillis = 0L,
        )

        val update = computeProgressDisplayUpdate(
            previous = previous,
            currentProgress = DownloadCenterProgressSnapshot(
                writtenBytes = 4096L,
                totalBytes = 8192L,
                completedChunks = 1,
                totalChunks = 4,
            ),
            event = progressEvent(
                writtenBytes = 6144L,
                totalBytes = 8192L,
                completedChunks = 2,
                totalChunks = 4,
            ),
            nowMillis = 1_050L,
        )

        assertThat(update.shouldEmit).isFalse()
        assertThat(update.nextSample.lastUiUpdateTimestampMillis).isEqualTo(1_000L)
    }

    @Test
    fun `computeProgressDisplayUpdate smooths speed after minimum sample window`() {
        val firstSpeedUpdate = computeProgressDisplayUpdate(
            previous = ProgressRateSample(
                lastObservedWrittenBytes = 0L,
                lastObservedTimestampMillis = 0L,
                lastUiUpdateTimestampMillis = 0L,
                speedAnchorWrittenBytes = 0L,
                speedAnchorTimestampMillis = 0L,
            ),
            currentProgress = DownloadCenterProgressSnapshot(
                writtenBytes = 0L,
                totalBytes = 30_000L,
            ),
            event = progressEvent(writtenBytes = 12_000L, totalBytes = 30_000L),
            nowMillis = 1_200L,
        )
        val secondSpeedUpdate = computeProgressDisplayUpdate(
            previous = firstSpeedUpdate.nextSample,
            currentProgress = DownloadCenterProgressSnapshot(
                writtenBytes = 12_000L,
                totalBytes = 30_000L,
            ),
            event = progressEvent(writtenBytes = 36_000L, totalBytes = 40_000L),
            nowMillis = 2_400L,
        )

        assertThat(firstSpeedUpdate.speedBytesPerSecond).isEqualTo(10_000L)
        assertThat(secondSpeedUpdate.speedBytesPerSecond).isEqualTo(12_500L)
    }

    private fun progressEvent(
        writtenBytes: Long,
        totalBytes: Long? = null,
        completedChunks: Int? = null,
        totalChunks: Int? = null,
    ): DownloadEvent.Progress =
        DownloadEvent.Progress(
            writtenBytes = writtenBytes,
            totalBytes = totalBytes,
            completedChunks = completedChunks,
            totalChunks = totalChunks,
        )
}
