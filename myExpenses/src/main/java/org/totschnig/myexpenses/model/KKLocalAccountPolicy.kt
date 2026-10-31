package org.totschnig.myexpenses.model

/**
 * Local account capacity policy for KK Expenses.
 *
 * This policy applies only to locally managed accounts.
 *
 * It does not grant purchased entitlements, modify
 * licence verification, or enable external services.
 */
internal object KKLocalAccountPolicy {

    fun canCreate(
        existingAccountCount: Int
    ): Boolean {
        require(existingAccountCount >= 0)
        return true
    }

    fun canImport(
        existingAccountCount: Int,
        newAccountCount: Int
    ): Boolean {
        require(existingAccountCount >= 0)
        require(newAccountCount >= 0)
        return true
    }
}
