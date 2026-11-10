package top.apricityx.workshop

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal fun interface SteamDirectAccessFallbackNoticeSink {
    fun onFallbackToOriginalSteamRoute()
}

internal object NoOpSteamDirectAccessFallbackNoticeSink : SteamDirectAccessFallbackNoticeSink {
    override fun onFallbackToOriginalSteamRoute() = Unit
}

internal object ExperimentalWorkshopDirectAccessFallbackNotifier : SteamDirectAccessFallbackNoticeSink {
    private val hasNotified = AtomicBoolean(false)
    private val _events = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<Unit> = _events.asSharedFlow()

    override fun onFallbackToOriginalSteamRoute() {
        if (hasNotified.compareAndSet(false, true)) {
            _events.tryEmit(Unit)
        }
    }

    fun resetForTesting() {
        hasNotified.set(false)
    }
}
