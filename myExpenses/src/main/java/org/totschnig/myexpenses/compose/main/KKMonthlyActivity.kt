package org.totschnig.myexpenses.compose.main
import org.totschnig.myexpenses.util.convAmount

import android.content.Context
import android.util.Log
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_PATH
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_CR_STATUS
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_STATUS
import org.totschnig.myexpenses.provider.KEY_TRANSFER_PEER
import org.totschnig.myexpenses.provider.STATUS_ARCHIVED
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Financial amounts are stored in the upstream database's integer minor units.
private data class ActivityEntry(
    val id: Long,
    val accountId: Long,
    val amount: Long,
    val epoch: Long,
    val description: String,
    val categoryId: Long
)
private data class ActivitySnapshot(
    val entries: List<ActivityEntry>,
    val categoryEntries: List<ActivityEntry>,
    val categories: Map<Long, String>
)

private fun readActivity(context: Context): ActivitySnapshot {
    val entries = mutableListOf<ActivityEntry>()
    val categories = mutableMapOf<Long, String>()
    val categoryEntries = mutableListOf<ActivityEntry>()
    val splitParentIds = mutableSetOf<Long>()
    // Transfers have a peer transaction and must not count as income or spending.
    // Do not exclude uncategorized transactions (cat_id may be NULL).
    val selection = "$KEY_TRANSFER_PEER IS NULL AND $KEY_PARENTID IS NULL AND $KEY_STATUS != $STATUS_ARCHIVED AND $KEY_CR_STATUS != 'VOID'"
    context.contentResolver.query(
        TransactionProvider.TRANSACTIONS_URI,
        arrayOf(KEY_ROWID, KEY_ACCOUNTID, KEY_AMOUNT, KEY_DATE, KEY_COMMENT, KEY_CATID),
        selection, null, "$KEY_DATE DESC"
    )?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(KEY_ROWID)
        val account = cursor.getColumnIndexOrThrow(KEY_ACCOUNTID)
        val amount = cursor.getColumnIndexOrThrow(KEY_AMOUNT)
        val date = cursor.getColumnIndexOrThrow(KEY_DATE)
        val comment = cursor.getColumnIndexOrThrow(KEY_COMMENT)
        val category = cursor.getColumnIndexOrThrow(KEY_CATID)
        while (cursor.moveToNext()) {
            entries.add(ActivityEntry(cursor.getLong(id), cursor.getLong(account), cursor.getLong(amount), cursor.getLong(date),
                cursor.getString(comment).orEmpty(), cursor.getLong(category)))
        }
    }
    // Read split parts separately for category attribution.
    // Monthly totals continue to use parent-level entries.
    context.contentResolver.query(
        TransactionProvider.TRANSACTIONS_URI,
        arrayOf(
            KEY_ROWID, KEY_ACCOUNTID, KEY_AMOUNT,
            KEY_DATE, KEY_COMMENT, KEY_CATID, KEY_PARENTID
        ),
        "$KEY_PARENTID IS NOT NULL AND $KEY_TRANSFER_PEER IS NULL",
        null,
        "$KEY_DATE DESC"
    )?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(KEY_ROWID)
        val account = cursor.getColumnIndexOrThrow(KEY_ACCOUNTID)
        val amount = cursor.getColumnIndexOrThrow(KEY_AMOUNT)
        val date = cursor.getColumnIndexOrThrow(KEY_DATE)
        val comment = cursor.getColumnIndexOrThrow(KEY_COMMENT)
        val category = cursor.getColumnIndexOrThrow(KEY_CATID)
        val parent = cursor.getColumnIndexOrThrow(KEY_PARENTID)

        val parentsById = entries.associateBy { it.id }

        while (cursor.moveToNext()) {
            if (cursor.isNull(parent)) continue

            val parentId = cursor.getLong(parent)
            val parentEntry = parentsById[parentId] ?: continue

            if (parentEntry.categoryId != 0L) continue

            splitParentIds.add(parentId)

            categoryEntries.add(
                ActivityEntry(
                    cursor.getLong(id),
                    parentEntry.accountId,
                    cursor.getLong(amount),
                    parentEntry.epoch,
                    cursor.getString(comment).orEmpty(),
                    cursor.getLong(category)
                )
            )
        }
    }

    // Category labels are optional; reporting must still work if the category provider differs.
    try {
        context.contentResolver.query(
            TransactionProvider.CATEGORIES_URI.buildUpon()
                .appendQueryParameter(
                    TransactionProvider.QUERY_PARAMETER_HIERARCHICAL,
                    "true"
                ).build(),
            arrayOf(KEY_ROWID, KEY_PATH),
            null, null, null
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(KEY_ROWID)
            val label = cursor.getColumnIndexOrThrow(KEY_PATH)
            while (cursor.moveToNext()) categories[cursor.getLong(id)] = cursor.getString(label).orEmpty()
        }
    } catch (e: Exception) {
        Log.e("KKMonthlyActivity", "Category lookup failed", e)
    }
    val categoryReportEntries =
        entries.filter { it.id !in splitParentIds } + categoryEntries

    return ActivitySnapshot(entries, categoryReportEntries, categories)
}

@Composable
internal fun KKMonthlyActivity(accounts: List<FullAccount>) {
    val context = LocalContext.current
    val formatter = LocalCurrencyFormatter.current
    var revision by remember { mutableStateOf(0) }
    var snapshot by remember { mutableStateOf<ActivitySnapshot?>(null) }
    var error by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var recentExpanded by remember {
        mutableStateOf(false)
    }
    var expandedComparisons by remember {
        mutableStateOf(setOf<Long>())
    }
    var expandedAccounts by remember {
        mutableStateOf(setOf<Long>())
    }
    var selectedMonth by remember {
        mutableStateOf(LocalDate.now().withDayOfMonth(1))
    }
    // Observe upstream provider notifications, including edits/deletions from the legacy editor.
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { revision++ }
        }
        context.contentResolver.registerContentObserver(TransactionProvider.TRANSACTIONS_URI, true, observer)
        context.contentResolver.registerContentObserver(TransactionProvider.CATEGORIES_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(context, revision) {
        try {
            snapshot = withContext(Dispatchers.IO) { readActivity(context) }
            error = false
        } catch (_: Exception) {
            snapshot = null
            error = true
        }
    }
    val data = snapshot
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val currentMonth = LocalDate.now().withDayOfMonth(1)
            val monthStart = selectedMonth
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Monthly activity",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        monthStart.format(
                            DateTimeFormatter.ofPattern("MMMM yyyy")
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    TextButton(
                        onClick = {
                            selectedMonth = selectedMonth.minusMonths(1)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "Previous month"
                        )
                    }
                    TextButton(
                        onClick = {
                            selectedMonth = selectedMonth.plusMonths(1)
                        },
                        enabled = monthStart.isBefore(currentMonth)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Next month"
                        )
                    }
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.ExpandLess
                        } else {
                            Icons.Filled.ExpandMore
                        },
                        contentDescription = if (expanded) {
                            "Collapse monthly activity"
                        } else {
                            "Expand monthly activity"
                        }
                    )
                }
            }
            if (expanded) {
            if (error) {
                Text("Monthly activity is temporarily unavailable. Account balances are unaffected.")
            } else if (data == null) {
                Text("Loading activity...")
            } else {
                val zone = ZoneId.systemDefault()
                val start = monthStart.atStartOfDay(zone).toEpochSecond()
                val end = monthStart.plusMonths(1).atStartOfDay(zone).toEpochSecond()
                val previousStart = monthStart.minusMonths(1)
                    .atStartOfDay(zone).toEpochSecond()
                val activeAccounts = accounts.filter { it.id > 0 }
                activeAccounts.forEach { account ->
                    val month = data.categoryEntries.filter {
                        it.accountId == account.id &&
                        it.epoch >= start &&
                        it.epoch < end
                    }
                    val income = month.filter { it.amount > 0 }.sumOf { it.amount }
                    val expense = month.filter { it.amount < 0 }.sumOf { it.amount }
                    val previousMonth = data.categoryEntries.filter {
                        it.accountId == account.id &&
                        it.epoch >= previousStart &&
                        it.epoch < start
                    }
                    val previousIncome = previousMonth
                        .filter { it.amount > 0 }
                        .sumOf { it.amount }
                    val previousExpense = previousMonth
                        .filter { it.amount < 0 }
                        .sumOf { it.amount }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            account.label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                expandedAccounts =
                                    if (account.id in expandedAccounts) {
                                        expandedAccounts - account.id
                                    } else {
                                        expandedAccounts + account.id
                                    }
                            }
                        ) {
                            Icon(
                                imageVector = if (account.id in expandedAccounts) {
                                    Icons.Filled.ExpandLess
                                } else {
                                    Icons.Filled.ExpandMore
                                },
                                contentDescription =
                                    if (account.id in expandedAccounts) {
                                        "Collapse " + account.label
                                    } else {
                                        "Expand " + account.label
                                    }
                            )
                        }
                    }
                    if (month.isEmpty()) {
                        Text(
                            "No activity this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Income",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatter.convAmount(
                                        income, account.currencyUnit
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    softWrap = true
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Expenses",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatter.convAmount(
                                        -expense, account.currencyUnit
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    softWrap = true
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Net cash flow",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatter.convAmount(
                                    income + expense,
                                    account.currencyUnit
                                ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.End,
                                softWrap = true
                            )
                        }
                    }
                    if (account.id in expandedAccounts) {
                    if (month.isEmpty() && previousMonth.isEmpty()) {
                        Text(
                            "No activity in the previous month either.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Compared with " +
                                monthStart.minusMonths(1).format(
                                    DateTimeFormatter.ofPattern(
                                        "MMMM yyyy"
                                    )
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                expandedComparisons =
                                    if (account.id in expandedComparisons) {
                                        expandedComparisons - account.id
                                    } else {
                                        expandedComparisons + account.id
                                    }
                            }
                        ) {
                            Icon(
                                imageVector =
                                    if (account.id in expandedComparisons) {
                                        Icons.Filled.ExpandLess
                                    } else {
                                        Icons.Filled.ExpandMore
                                    },
                                contentDescription =
                                    if (account.id in expandedComparisons) {
                                        "Collapse comparison for " +
                                            account.label
                                    } else {
                                        "Expand comparison for " +
                                            account.label
                                    }
                            )
                        }
                    }

                    if (account.id in expandedComparisons) {
                    val incomeChange = income - previousIncome
                    val expenseChange = (-expense) - (-previousExpense)
                    val netChange = (income + expense) -
                        (previousIncome + previousExpense)

                    val expensePercent = if (previousExpense != 0L) {
                        String.format(
                            java.util.Locale.getDefault(),
                            "%+.1f%%",
                            expenseChange.toDouble() /
                                kotlin.math.abs(previousExpense.toDouble()) * 100.0
                        )
                    } else {
                        "N/A"
                    }

                    val incomePercent = if (previousIncome != 0L) {
                        String.format(
                            java.util.Locale.getDefault(),
                            "%+.1f%%",
                            incomeChange.toDouble() /
                                kotlin.math.abs(previousIncome.toDouble()) * 100.0
                        )
                    } else {
                        "N/A"
                    }

                    fun signedAmount(value: Long): String {
                        val formatted = formatter.convAmount(
                            value,
                            account.currencyUnit
                        )
                        return if (value > 0L) "+$formatted" else formatted
                    }

                    fun performanceLabel(
                        change: Long,
                        higherIsBetter: Boolean
                    ): String {
                        if (change == 0L) return "Neutral"
                        val favorable = if (higherIsBetter) {
                            change > 0L
                        } else {
                            change < 0L
                        }
                        return if (favorable) {
                            "Favorable"
                        } else {
                            "Unfavorable"
                        }
                    }

                    @Composable
                    fun performanceColor(
                        change: Long,
                        higherIsBetter: Boolean
                    ): androidx.compose.ui.graphics.Color {
                        if (change == 0L) {
                            return MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val favorable = if (higherIsBetter) {
                            change > 0L
                        } else {
                            change < 0L
                        }
                        return if (favorable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    }

                    @Composable
                    fun ChangeRow(
                        label: String,
                        change: Long,
                        higherIsBetter: Boolean,
                        percentage: String? = null
                    ) {
                        val status = performanceLabel(
                            change, higherIsBetter
                        )
                        val amount = signedAmount(change)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = performanceColor(
                                        change, higherIsBetter
                                    )
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    amount,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    softWrap = true
                                )

                                if (percentage != null) {
                                    Text(
                                        "($percentage)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End,
                                    softWrap = true
                                    )
                                }
                            }
                        }
                    }

                    ChangeRow(
                        label = "Income change",
                        change = incomeChange,
                        higherIsBetter = true,
                        percentage = incomePercent
                    )

                    ChangeRow(
                        label = "Expense change",
                        change = expenseChange,
                        higherIsBetter = false,
                        percentage = expensePercent
                    )

                    ChangeRow(
                        label = "Net cash flow change",
                        change = netChange,
                        higherIsBetter = true
                    )

                    val currentCategories = month
                        .filter { it.amount < 0 }
                        .groupBy { it.categoryId }
                        .mapValues { (_, items) ->
                            items.sumOf { -it.amount }
                        }

                    val previousCategories = previousMonth
                        .filter { it.amount < 0 }
                        .groupBy { it.categoryId }
                        .mapValues { (_, items) ->
                            items.sumOf { -it.amount }
                        }

                    val categoryChanges = (
                        currentCategories.keys + previousCategories.keys
                    ).distinct().map { categoryId ->
                        val current = currentCategories[categoryId] ?: 0L
                        val previous = previousCategories[categoryId] ?: 0L
                        categoryId to (current - previous)
                    }.filter { it.second != 0L }
                        .sortedByDescending { kotlin.math.abs(it.second) }
                        .take(4)

                    if (categoryChanges.isNotEmpty()) {
                        Text(
                            "Category spending changes",
                            style = MaterialTheme.typography.labelLarge
                        )

                        categoryChanges.forEach { (categoryId, change) ->
                            val spendingLabel = when {
                                change > 0L -> "Spending up"
                                change < 0L -> "Spending down"
                                else -> "No change"
                            }

                            val spendingColor = when {
                                change > 0L ->
                                    MaterialTheme.colorScheme.error
                                change < 0L ->
                                    MaterialTheme.colorScheme.primary
                                else ->
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        data.categories[categoryId]
                                            ?.ifBlank { "Uncategorized" }
                                            ?: "Uncategorized",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        spendingLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = spendingColor
                                    )
                                }

                                Text(
                                    signedAmount(change),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    softWrap = true
                                )
                            }
                        }
                    }
                    }
                    }

                    val byCategory = data.categoryEntries
                    .filter {
                        it.accountId == account.id &&
                        it.epoch >= start &&
                        it.epoch < end &&
                        it.amount < 0
                    }
                    .groupBy { it.categoryId }
                        .mapValues { (_, txns) -> txns.sumOf { -it.amount } }
                        .toList().sortedByDescending { it.second }.take(4)
                    if (byCategory.isNotEmpty()) {
                        Text("Top spending categories", style = MaterialTheme.typography.labelLarge)
                        val maximum = byCategory.first().second.coerceAtLeast(1L)
                        byCategory.forEach { (id, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    data.categories[id]
                                        ?.ifBlank { "Uncategorized" }
                                        ?: "Uncategorized",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    softWrap = true
                                )
                                Text(
                                    formatter.convAmount(
                                        value,
                                        account.currencyUnit
                                    ),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    softWrap = true
                                )
                            }
                            LinearProgressIndicator(progress = { (value.toDouble() / maximum).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    }
                    HorizontalDivider()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent transactions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            recentExpanded = !recentExpanded
                        }
                    ) {
                        Icon(
                            imageVector = if (recentExpanded) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = if (recentExpanded) {
                                "Collapse recent transactions"
                            } else {
                                "Expand recent transactions"
                            }
                        )
                    }
                }
                if (recentExpanded) {
                val accountById = activeAccounts.associateBy { it.id }
                val recent = data.entries.filter {
                    accountById.containsKey(it.accountId) &&
                    it.epoch >= start &&
                    it.epoch < end
                }.take(5)
                if (recent.isEmpty()) Text("No transactions for this month")
                recent.forEach { entry ->
                    val account = accountById[entry.accountId] ?: return@forEach
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.description.ifBlank { data.categories[entry.categoryId] ?: "Transaction" }, style = MaterialTheme.typography.bodyMedium)
                            Text(account.label + " · " + Instant.ofEpochSecond(entry.epoch).atZone(zone).toLocalDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatter.convAmount(entry.amount, account.currencyUnit), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                }
                Text("Transfers excluded. Split expenses are grouped by their assigned categories. Amounts are not combined across currencies.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
}
