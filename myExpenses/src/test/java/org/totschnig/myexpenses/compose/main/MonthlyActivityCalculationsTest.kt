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

    @Test
    fun sixMonthTrendIncludesEmptyMonths() {
        val zone = java.time.ZoneId.of("UTC")
        val selected = java.time.YearMonth.of(2026, 10)

        val points = monthlyActivityTrend(
            emptyList(), selected, zone
        )

        assertThat(points).hasSize(6)
        assertThat(points.first().month)
            .isEqualTo(java.time.YearMonth.of(2026, 5))
        assertThat(points.last().month)
            .isEqualTo(selected)

        points.forEach {
            assertThat(it.income).isEqualTo(0L)
            assertThat(it.expenses).isEqualTo(0L)
            assertThat(it.netCashFlow).isEqualTo(0L)
        }
    }

    @Test
    fun trendCalculatesIncomeExpensesAndNetFlow() {
        val zone = java.time.ZoneId.of("UTC")
        val epoch = java.time.LocalDate.of(2026, 10, 15)
            .atStartOfDay(zone).toEpochSecond()

        val entries = listOf(
            entry(1, 1, 500000, epoch = epoch),
            entry(2, 1, -200000, epoch = epoch)
        )

        val october = monthlyActivityTrend(
            entries,
            java.time.YearMonth.of(2026, 10),
            zone
        ).last()

        assertThat(october.income).isEqualTo(500000L)
        assertThat(october.expenses).isEqualTo(200000L)
        assertThat(october.netCashFlow).isEqualTo(300000L)
    }

    @Test
    fun trendSupportsNegativeCashFlow() {
        val zone = java.time.ZoneId.of("UTC")
        val epoch = java.time.LocalDate.of(2026, 10, 10)
            .atStartOfDay(zone).toEpochSecond()

        val points = monthlyActivityTrend(
            listOf(
                entry(1, 1, 100000, epoch = epoch),
                entry(2, 1, -250000, epoch = epoch)
            ),
            java.time.YearMonth.of(2026, 10),
            zone
        )

        assertThat(points.last().netCashFlow)
            .isEqualTo(-150000L)
    }

    @Test
    fun trendExcludesTransactionsOutsideWindow() {
        val zone = java.time.ZoneId.of("UTC")

        fun epoch(year: Int, month: Int) =
            java.time.LocalDate.of(year, month, 15)
                .atStartOfDay(zone).toEpochSecond()

        val entries = listOf(
            entry(1, 1, 10000, epoch = epoch(2026, 4)),
            entry(2, 1, 20000, epoch = epoch(2026, 5)),
            entry(3, 1, 30000, epoch = epoch(2026, 10)),
            entry(4, 1, 40000, epoch = epoch(2026, 11))
        )

        val points = monthlyActivityTrend(
            entries,
            java.time.YearMonth.of(2026, 10),
            zone
        )

        assertThat(points.sumOf { it.income })
            .isEqualTo(50000L)
    }

    @Test
    fun trendHandlesYearBoundary() {
        val points = monthlyActivityTrend(
            emptyList(),
            java.time.YearMonth.of(2026, 2),
            java.time.ZoneId.of("UTC")
        )

        assertThat(points.map { it.month }).containsExactly(
            java.time.YearMonth.of(2025, 9),
            java.time.YearMonth.of(2025, 10),
            java.time.YearMonth.of(2025, 11),
            java.time.YearMonth.of(2025, 12),
            java.time.YearMonth.of(2026, 1),
            java.time.YearMonth.of(2026, 2)
        ).inOrder()
    }

    @Test
    fun trendRespectsLocalTimezone() {
        val zone = java.time.ZoneId.of("Asia/Kolkata")

        val epoch = java.time.Instant.parse(
            "2026-09-30T20:00:00Z"
        ).epochSecond

        val points = monthlyActivityTrend(
            listOf(entry(1, 1, 5000, epoch = epoch)),
            java.time.YearMonth.of(2026, 10),
            zone
        )

        assertThat(points.last().income).isEqualTo(5000L)
    }

    @Test
    fun mixedSplitReconciliationBalances() {
        val parents = listOf(
            entry(1, 1, -40000)
        )

        val categories = listOf(
            entry(2, 1, -50000, 10),
            entry(3, 1, 10000, 20)
        )

        val result = mixedSplitReconciliation(
            parents,
            categories
        )

        assertThat(result.grossCategoryExpenses)
            .isEqualTo(50000L)
        assertThat(result.netTransactionExpenses)
            .isEqualTo(40000L)
        assertThat(result.reconciliationDifference)
            .isEqualTo(10000L)
    }

    @Test
    fun ordinaryExpenseHasNoOffset() {
        val entries = listOf(
            entry(1, 1, -20000, 10)
        )

        val result = mixedSplitReconciliation(
            entries,
            entries
        )

        assertThat(result.reconciliationDifference)
            .isEqualTo(0L)
    }

    @Test
    fun emptyReconciliationIsZero() {
        val result = mixedSplitReconciliation(
            emptyList(),
            emptyList()
        )

        assertThat(result.grossCategoryExpenses)
            .isEqualTo(0L)
        assertThat(result.netTransactionExpenses)
            .isEqualTo(0L)
        assertThat(result.reconciliationDifference)
            .isEqualTo(0L)
    }

    @Test
    fun multipleMixedSplitsReconcile() {
        val parents = listOf(
            entry(1, 1, -40000),
            entry(2, 1, -25000)
        )

        val categories = listOf(
            entry(3, 1, -50000, 10),
            entry(4, 1, 10000, 20),
            entry(5, 1, -30000, 30),
            entry(6, 1, 5000, 40)
        )

        val result = mixedSplitReconciliation(
            parents,
            categories
        )

        assertThat(result.grossCategoryExpenses)
            .isEqualTo(80000L)

        assertThat(result.netTransactionExpenses)
            .isEqualTo(65000L)

        assertThat(result.reconciliationDifference)
            .isEqualTo(15000L)
    }

    @Test
    fun positiveOnlyTransactionsHaveNoExpenses() {
        val entries = listOf(
            entry(1, 1, 10000, 10),
            entry(2, 1, 5000, 20)
        )

        val result = mixedSplitReconciliation(
            entries,
            entries
        )

        assertThat(result.grossCategoryExpenses)
            .isEqualTo(0L)

        assertThat(result.netTransactionExpenses)
            .isEqualTo(0L)

        assertThat(result.reconciliationDifference)
            .isEqualTo(0L)
    }

    @Test
    fun multipleOrdinaryExpensesHaveNoDifference() {
        val entries = listOf(
            entry(1, 1, -10000, 10),
            entry(2, 1, -20000, 20),
            entry(3, 1, -5000, 30)
        )

        val result = mixedSplitReconciliation(
            entries,
            entries
        )

        assertThat(result.grossCategoryExpenses)
            .isEqualTo(35000L)

        assertThat(result.netTransactionExpenses)
            .isEqualTo(35000L)

        assertThat(result.reconciliationDifference)
            .isEqualTo(0L)
    }

    @Test
    fun mixedSplitAndOrdinaryExpenseReconcile() {
        val parents = listOf(
            entry(1, 1, -40000),
            entry(2, 1, -20000, 30)
        )

        val categories = listOf(
            entry(3, 1, -50000, 10),
            entry(4, 1, 10000, 20),
            entry(2, 1, -20000, 30)
        )

        val result = mixedSplitReconciliation(
            parents,
            categories
        )

        assertThat(result.grossCategoryExpenses)
            .isEqualTo(70000L)

        assertThat(result.netTransactionExpenses)
            .isEqualTo(60000L)

        assertThat(result.reconciliationDifference)
            .isEqualTo(10000L)
    }

    @Test
    fun categoryShortfallProducesNegativeDifference() {
        val parents = listOf(
            entry(1, 1, -50000)
        )

        val categories = listOf(
            entry(2, 1, -30000, 10)
        )

        val result = mixedSplitReconciliation(
            parents,
            categories
        )

        assertThat(result.grossCategoryExpenses)
            .isEqualTo(30000L)

        assertThat(result.netTransactionExpenses)
            .isEqualTo(50000L)

        assertThat(result.reconciliationDifference)
            .isEqualTo(-20000L)
    }

}
