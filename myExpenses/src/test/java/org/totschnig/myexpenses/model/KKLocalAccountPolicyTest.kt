package org.totschnig.myexpenses.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KKLocalAccountPolicyTest {

    @Test
    fun firstAccountAllowed() {
        assertThat(
            KKLocalAccountPolicy.canCreate(0)
        ).isTrue()
    }

    @Test
    fun fifthAccountAllowed() {
        assertThat(
            KKLocalAccountPolicy.canCreate(4)
        ).isTrue()
    }

    @Test
    fun sixthAccountAllowed() {
        assertThat(
            KKLocalAccountPolicy.canCreate(5)
        ).isTrue()
    }

    @Test
    fun manyAccountsAllowed() {
        assertThat(
            KKLocalAccountPolicy.canCreate(100)
        ).isTrue()
    }

    @Test
    fun importMultipleAccountsAllowed() {
        assertThat(
            KKLocalAccountPolicy.canImport(5, 10)
        ).isTrue()
    }

    @Test
    fun importZeroAccountsAllowed() {
        assertThat(
            KKLocalAccountPolicy.canImport(5, 0)
        ).isTrue()
    }

    @Test
    fun negativeExistingCountRejected() {
        val result = runCatching {
            KKLocalAccountPolicy.canCreate(-1)
        }

        assertThat(result.exceptionOrNull())
            .isInstanceOf(
                IllegalArgumentException::class.java
            )
    }

    @Test
    fun negativeImportCountRejected() {
        val result = runCatching {
            KKLocalAccountPolicy.canImport(5, -1)
        }

        assertThat(result.exceptionOrNull())
            .isInstanceOf(
                IllegalArgumentException::class.java
            )
    }
}
