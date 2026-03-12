package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkshopInputValidatorTest {
    @Test
    fun `blank values are rejected`() {
        assertThat(WorkshopInputValidator.validate("", "")).isEqualTo("AppID and PublishedFileId are required.")
    }

    @Test
    fun `zero values are rejected`() {
        assertThat(WorkshopInputValidator.validate("0", "1")).isEqualTo("AppID must be a positive integer.")
        assertThat(WorkshopInputValidator.validate("1", "0")).isEqualTo("PublishedFileId must be a positive integer.")
    }

    @Test
    fun `positive integers are accepted`() {
        assertThat(WorkshopInputValidator.validate("480", "123456")).isNull()
    }
}
