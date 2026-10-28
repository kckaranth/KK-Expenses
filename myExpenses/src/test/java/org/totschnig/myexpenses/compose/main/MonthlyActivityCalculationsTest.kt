package org.totschnig.myexpenses.compose.main

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MonthlyActivityCalculationsTest {

    private fun entry(
        id: Long,
        accountId: Long,
        amount: Long,
        categoryId: Long = 0L,
        epoch: Long = 0L
    ) = ActivityEntry(
        id = id,
        accountId = accountId,
        amount = amount,
        epoch = epoch,
        description = "",
        categoryId = categoryId
    )

    @Test
    fun ordinaryIncomeAndExpense() {
        val entries = listOf(
            entry(1, 1, 50000),
            entry(2, 1, -12000),
            entry(3, 1, -3000)
        )

        val (income, expense) =
            incomeExpenseTotals(entries)

        assertThat(income).isEqualTo(50000L)
        assertThat(expense).isEqualTo(-15000L)
        assertThat(income + expense).isEqualTo(35000L)
    }

    @Test
    fun splitExpenseUsesParentForTotals() {
        val parent = listOf(
            entry(1, 1, -100000)
        )

        val categories = listOf(
            entry(2, 1, -70000, 10),
            entry(3, 1, -30000, 20)
        )

        val (income, expense) =
            incomeExpenseTotals(parent)

        val spending = categorySpending(categories)

        assertThat(income).isEqualTo(0L)
        assertThat(expense).isEqualTo(-100000L)
        assertThat(spending[10L]).isEqualTo(70000L)
        assertThat(spending[20L]).isEqualTo(30000L)
    }

    @Test
    fun refundCountsAsIncome() {
        val entries = listOf(
            entry(1, 1, -10000),
            entry(2, 1, 2500)
        )

        val (income, expense) =
            incomeExpenseTotals(entries)

        assertThat(income).isEqualTo(2500L)
        assertThat(expense).isEqualTo(-10000L)
    }

    @Test
    fun zeroAmountDoesNotAffectTotals() {
        val (income, expense) =
            incomeExpenseTotals(
                listOf(entry(1, 1, 0))
            )

        assertThat(income).isEqualTo(0L)
        assertThat(expense).isEqualTo(0L)
    }

    @Test
    fun categorySpendingGroupsExpenses() {
        val entries = listOf(
            entry(1, 1, -1000, 10),
            entry(2, 1, -2000, 10),
            entry(3, 1, -500, 20),
            entry(4, 1, 300, 10)
        )

        val spending = categorySpending(entries)

        assertThat(spending[10L]).isEqualTo(3000L)
        assertThat(spending[20L]).isEqualTo(500L)
    }

    @Test
    fun accountFilteringKeepsBalancesSeparate() {
        val entries = listOf(
            entry(1, 1, 10000),
            entry(2, 2, 20000),
            entry(3, 1, -3000)
        )

        val accountOne = entries.filter {
            it.accountId == 1L
        }

        val accountTwo = entries.filter {
            it.accountId == 2L
        }

        assertThat(
            incomeExpenseTotals(accountOne)
        ).isEqualTo(10000L to -3000L)

        assertThat(
            incomeExpenseTotals(accountTwo)
        ).isEqualTo(20000L to 0L)
    }
}
