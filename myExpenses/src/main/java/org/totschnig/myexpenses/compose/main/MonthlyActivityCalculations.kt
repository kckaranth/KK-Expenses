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
