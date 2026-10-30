package org.totschnig.myexpenses.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression coverage for the free account-count boundary.
 *
 * These tests do not grant licensing entitlements
 * or modify account records.
 */
class AccountLimitPolicyTest {

    @Test
    fun freeLimitIsFive() {
        assertThat(ContribFeature.FREE_ACCOUNTS)
            .isEqualTo(5)
    }

    @Test
    fun firstAccountAllowed() {
        assertThat(canCreateAnotherAccount(0, false)).isTrue()
    }

    @Test
    fun fifthAccountAllowed() {
        assertThat(canCreateAnotherAccount(4, false)).isTrue()
    }

    @Test
    fun sixthAccountRequiresEntitlement() {
        assertThat(canCreateAnotherAccount(5, false)).isFalse()
    }

    @Test
    fun unlimitedEntitlementAllowsSixthAccount() {
        assertThat(canCreateAnotherAccount(5, true)).isTrue()
    }

    @Test
    fun unlimitedEntitlementAllowsMoreAccounts() {
        assertThat(canCreateAnotherAccount(100, true)).isTrue()
    }

    @Test
    fun negativeAccountCountRejected() {
        val result = runCatching {
            canCreateAnotherAccount(-1, false)
        }

        assertThat(result.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun sixExistingAccountsRequireUnlimitedAccess() {
        assertThat(
            canCreateAnotherAccount(6, false)
        ).isFalse()

        assertThat(
            canCreateAnotherAccount(6, true)
        ).isTrue()
    }
}
