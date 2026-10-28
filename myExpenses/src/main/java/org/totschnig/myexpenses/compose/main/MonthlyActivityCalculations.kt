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
