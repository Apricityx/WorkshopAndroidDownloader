package top.apricityx.workshop.steam.protocol

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.junit.Test

class OkHttpTimeoutsTest {
    @Test
    fun `applySteamHttpCompatibility forces http1 only`() {
        val client = OkHttpClient.Builder()
            .applySteamHttpCompatibility()
            .build()

        assertThat(client.protocols).containsExactly(Protocol.HTTP_1_1)
    }

    @Test
    fun `newDefaultOkHttpClient uses steam-compatible protocols`() {
        val client = newDefaultOkHttpClient()

        assertThat(client.protocols).containsExactly(Protocol.HTTP_1_1)
    }
}
