package org.totschnig.myexpenses.compose.main

// Financial amounts are stored in the upstream database's integer minor units.
internal data class ActivityEntry(
    val id: Long,
    val accountId: Long,
    val amount: Long,
    val epoch: Long,
    val description: String,
    val categoryId: Long
)
internal fun incomeExpenseTotals(
    entries: List<ActivityEntry>
): Pair<Long, Long> {
    var income = 0L
    var expense = 0L

    entries.forEach { entry ->
        when {
            entry.amount > 0L -> income += entry.amount
            entry.amount < 0L -> expense += entry.amount
        }
    }

    return income to expense
}

internal fun categorySpending(
    entries: List<ActivityEntry>
): Map<Long, Long> =
    entries
        .filter { it.amount < 0 }
        .groupBy { it.categoryId }
        .mapValues { (_, items) ->
            items.sumOf { -it.amount }
        }

internal data class MonthlyTrendPoint(
    val month: java.time.YearMonth,
    val income: Long,
    val expenses: Long,
    val netCashFlow: Long
)

internal fun monthlyActivityTrend(
    entries: List<ActivityEntry>,
    selectedMonth: java.time.YearMonth,
    zone: java.time.ZoneId,
    monthCount: Int = 6
): List<MonthlyTrendPoint> {
    require(monthCount > 0)

    val firstMonth = selectedMonth.minusMonths(
        (monthCount - 1).toLong()
    )

    val totalsByMonth = entries
        .asSequence()
        .map { entry ->
            val month = java.time.YearMonth.from(
                java.time.Instant.ofEpochSecond(entry.epoch)
                    .atZone(zone)
            )
            month to entry
        }
        .filter { (month, _) ->
            month >= firstMonth && month <= selectedMonth
        }
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )
        .mapValues { (_, monthEntries) ->
            incomeExpenseTotals(monthEntries)
        }

    return (0 until monthCount).map { offset ->
        val month = firstMonth.plusMonths(offset.toLong())
        val (income, expense) =
            totalsByMonth[month] ?: (0L to 0L)

        MonthlyTrendPoint(
            month = month,
            income = income,
            expenses = -expense,
            netCashFlow = income + expense
        )
    }
}


/**
 * Reconciles gross category expenses with net parent
 * transaction expenses.
 *
 * All amounts use integer minor currency units.
 *
 * This is a reporting calculation only.
 */
internal data class MixedSplitReconciliation(
    val grossCategoryExpenses: Long,
    val netTransactionExpenses: Long,
    val reconciliationDifference: Long
)

internal fun mixedSplitReconciliation(
    parentEntries: List<ActivityEntry>,
    categoryEntries: List<ActivityEntry>
): MixedSplitReconciliation {
    val gross = categoryEntries
        .filter { it.amount < 0L }
        .sumOf { -it.amount }

    val net = parentEntries
        .filter { it.amount < 0L }
        .sumOf { -it.amount }

    return MixedSplitReconciliation(
        grossCategoryExpenses = gross,
        netTransactionExpenses = net,
        reconciliationDifference = gross - net
    )
}

/**
 * Selection for eligible split transaction children.
 * Excludes transfers, archived entries and void entries.
 */
internal fun monthlySplitChildSelection(): String =
    "parent_id IS NOT NULL AND transfer_peer IS NULL AND " +
        "status != 5 AND cr_status != 'VOID'"
