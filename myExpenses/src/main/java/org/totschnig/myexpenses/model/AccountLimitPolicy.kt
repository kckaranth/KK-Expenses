package org.totschnig.myexpenses.model

/**
 * Determines whether another account may be created.
 *
 * This function does not grant or modify entitlements.
 */
internal fun canCreateAnotherAccount(
    accountCount: Int,
    hasUnlimitedAccess: Boolean
): Boolean {
    require(accountCount >= 0)

    return hasUnlimitedAccess ||
        accountCount < ContribFeature.FREE_ACCOUNTS
}
