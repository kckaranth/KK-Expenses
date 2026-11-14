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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class MonthlyAccountSummary(
    val current: List<ActivityEntry>,
    val previous: List<ActivityEntry>,
    val income: Long,
    val expense: Long,
    val previousIncome: Long,
    val previousExpense: Long,
    val currentCategorySpending: Map<Long, Long>,
    val previousCategorySpending: Map<Long, Long>,
    val reconciliation: MixedSplitReconciliation
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
        monthlySplitChildSelection(),
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
private fun arthvexaFinancialColor(kind: String, amount: Long = 0L): Color {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val dark = (configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val income = if (dark) Color(0xFF80E0C7) else Color(0xFF00796B)
    val expense = if (dark) Color(0xFFFF9B86) else Color(0xFFB54745)
    return when (kind) {
        "income" -> income
        "expense" -> expense
        "net" -> when {
            amount > 0L -> income
            amount < 0L -> expense
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun ArthvexaMonthlyAccountCard(
    content: @Composable () -> Unit
) {
    val configuration =
        androidx.compose.ui.platform.LocalConfiguration.current

    val isDark =
        (configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    if (isDark) {
        content()
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                content()
            }
        }
    }
}


@Composable
private fun ArthvexaMonthlyAnalyticsCard(
    subtleDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val configuration =
        androidx.compose.ui.platform.LocalConfiguration.current

    val isDark =
        (configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isDark && subtleDark) {
            Color(0xFF303138)
        } else if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            content()
        }
    }
}

// Extracted without changing month navigation or expansion ownership.
// The parent retains selectedMonth and expanded state for both Home and Insights.
@Composable
private fun ArthvexaMonthlyPeriodHeader(
    insightsMode: Boolean,
    monthStart: LocalDate,
    currentMonth: LocalDate,
    expanded: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onExpandedChange: (Boolean) -> Unit
) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        if (insightsMode) "Financial analysis" else "Monthly activity",
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
                            onPreviousMonth()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "Previous month"
                        )
                    }
                    TextButton(
                        onClick = {
                            onNextMonth()
                        },
                        enabled = monthStart.isBefore(currentMonth)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Next month"
                        )
                    }
                }
                TextButton(
                            modifier = Modifier.semantics {
                                stateDescription = if (expanded) "Expanded" else "Collapsed"
                                contentDescription = "Monthly activity"
                            },
onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.ExpandLess
                        } else {
                            Icons.Filled.ExpandMore
                        },
                        contentDescription = null
                    )
                }
            }
}

@Composable
internal fun KKMonthlyActivity(
    accounts: List<FullAccount>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    insightsMode: Boolean = false
) {
    val context = LocalContext.current
    val formatter = LocalCurrencyFormatter.current
    var revision by remember { mutableStateOf(0) }
    var snapshot by remember { mutableStateOf<ActivitySnapshot?>(null) }
    var error by remember { mutableStateOf(false) }
    var recentExpanded by remember {
        mutableStateOf(false)
    }
    var expandedComparisons by remember(insightsMode, accounts.map { it.id }) {
        mutableStateOf(if (insightsMode) accounts.filter { it.id > 0 }.map { it.id }.toSet() else emptySet())
    }
    // Insights-only detail state; Home retains its original expanded presentation.
    var expandedCategoryChanges by remember(insightsMode, accounts.map { it.id }) {
        mutableStateOf(emptySet<Long>())
    }
    var expandedAccounts by remember(insightsMode, accounts.map { it.id }) {
        mutableStateOf(if (insightsMode) accounts.filter { it.id > 0 }.map { it.id }.toSet() else emptySet())
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            snapshot = null
            error = true
        }
    }
    val data = snapshot
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (insightsMode) 14.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val currentMonth = LocalDate.now().withDayOfMonth(1)
            val monthStart = selectedMonth
            ArthvexaMonthlyPeriodHeader(
                insightsMode = insightsMode,
                monthStart = monthStart,
                currentMonth = currentMonth,
                expanded = expanded,
                onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) },
                onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
                onExpandedChange = onExpandedChange
            )
            if (!expanded) {
                if (error) {
                    Text(
                        "Monthly snapshot is temporarily unavailable.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (data == null) {
                    Text("Loading monthly snapshot...")
                } else {
                    val zone = ZoneId.systemDefault()
                    val start = monthStart.atStartOfDay(zone)
                        .toEpochSecond()
                    val end = monthStart.plusMonths(1)
                        .atStartOfDay(zone).toEpochSecond()

                    Text(
                        "Financial snapshot",
                        style = MaterialTheme.typography.titleMedium
                    )

                    val activeAccounts = accounts.filter {
                        it.id > 0
                    }

                    if (activeAccounts.isEmpty()) {
                        Text("No accounts available")
                    }

                    activeAccounts.forEach { account ->
                        key(account.id) {
                            val summary = remember(
                                data,
                                account.id,
                                start,
                                end
                            ) {
                                monthlyFinancialSnapshot(
                                    entries = data.entries,
                                    accountId = account.id,
                                    startEpoch = start,
                                    endEpoch = end
                                )
                            }

                            ArthvexaMonthlyAccountCard {
                            Column(
                                verticalArrangement =
                                    Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    account.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (summary.transactionCount == 0) {
                                    Text(
                                        "No activity this month",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                "Income",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                formatter.convAmount(
                                                    summary.income,
                                                    account.currencyUnit
                                                ),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFeatureSettings = "tnum"
                                                ),
                                                color = arthvexaFinancialColor("income"),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                "Expenses",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                formatter.convAmount(
                                                    summary.expenses,
                                                    account.currencyUnit
                                                ),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFeatureSettings = "tnum"
                                                ),
                                                color = arthvexaFinancialColor("expense"),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.SpaceBetween
                                    ) {
                                        Text("Net cash flow")
                                        Text(
                                            formatter.convAmount(
                                                summary.netCashFlow,
                                                account.currencyUnit
                                            ),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFeatureSettings = "tnum"
                                            ),
                                            fontWeight = FontWeight.SemiBold,
                                            color = arthvexaFinancialColor("net", summary.netCashFlow),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                            }

                            HorizontalDivider()
                        }
                    }

                    Text(
                        "Expand for trends, comparisons, and category spending. " +
                            "Currencies remain separate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                val activeAccounts = remember(accounts) {
                    accounts.filter { it.id > 0 }
                }
                val entriesByAccount = remember(data) {
                    data.entries.groupBy { it.accountId }
                }

                val categoryEntriesByAccount = remember(data) {
                    data.categoryEntries.groupBy { it.accountId }
                }

                activeAccounts.forEach { account ->
                    key(account.id) {
                    val accountEntries =
                        entriesByAccount[account.id].orEmpty()

                    val accountCategoryEntries =
                        categoryEntriesByAccount[account.id].orEmpty()

                    val monthlySummary = remember(
                        data,
                        monthStart,
                        account.id,
                        zone
                    ) {
                        val current = accountEntries.filter {
                            it.epoch >= start &&
                            it.epoch < end
                        }
                        val previous = accountEntries.filter {
                            it.epoch >= previousStart &&
                            it.epoch < start
                        }
                        val (income, expense) =
                            incomeExpenseTotals(current)

                        val (previousIncome, previousExpense) =
                            incomeExpenseTotals(previous)

                        val currentCategoryEntries =
                            accountCategoryEntries.filter {
                                it.epoch >= start &&
                                    it.epoch < end
                            }

                        val previousCategoryEntries =
                            accountCategoryEntries.filter {
                                it.epoch >= previousStart &&
                                    it.epoch < start
                            }

                        MonthlyAccountSummary(
                            current = current,
                            previous = previous,
                            income = income,
                            expense = expense,
                            previousIncome = previousIncome,
                            previousExpense = previousExpense,
                            currentCategorySpending =
                                categorySpending(currentCategoryEntries),
                            previousCategorySpending =
                                categorySpending(previousCategoryEntries),
                            reconciliation = mixedSplitReconciliation(
                                parentEntries = current,
                                categoryEntries = currentCategoryEntries
                            )
                        )
                    }

                    val month = monthlySummary.current
                    val previousMonth = monthlySummary.previous
                    val income = monthlySummary.income
                    val expense = monthlySummary.expense
                    val previousIncome = monthlySummary.previousIncome
                    val previousExpense = monthlySummary.previousExpense
                    ArthvexaMonthlyAccountCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            account.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            modifier = Modifier.semantics {
                                stateDescription = if (account.id in expandedAccounts) "Expanded" else "Collapsed"
                                contentDescription = account.label
                            },

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
                                contentDescription = null
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
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = arthvexaFinancialColor("income"),
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
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = arthvexaFinancialColor("expense"),
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
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = arthvexaFinancialColor("net", income + expense),
                                textAlign = TextAlign.End,
                                softWrap = true
                            )
                        }
                    }
                    }
                    if (account.id in expandedAccounts) {
                    val trendPoints = remember(
                        data,
                        monthStart,
                        account.id,
                        zone
                    ) {
                        monthlyActivityTrend(
                            entries = accountEntries,
                            selectedMonth = YearMonth.from(monthStart),
                            zone = zone
                        )
                    }

                    val insights = remember(
                        monthlySummary
                    ) {
                        monthlySpendingInsights(
                            currentCategories =
                                monthlySummary.currentCategorySpending,
                            currentSignedExpense =
                                monthlySummary.expense,
                            previousSignedExpense =
                                monthlySummary.previousExpense
                        )
                    }

                    // Keep all account data and expansion state in this parent.
                    // On wide Insights panes, put spending and trends side by side.
                    // Home always retains the original vertical presentation.
                    @Composable
                    fun SpendingInsightsPanel() {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Spending insights",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Net monthly expenses",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Text(
                                    formatter.convAmount(
                                        insights.currentNetExpenses,
                                        account.currencyUnit
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = arthvexaFinancialColor("expense")
                                )
                            }

                            val hasExpenseBaseline =
                                insights.expenseChangePercent != null
                            val changeLabel = when {
                                !hasExpenseBaseline && insights.expenseChange != 0L ->
                                    "Spending this month"
                                insights.expenseChange > 0L ->
                                    "Spending increased"
                                insights.expenseChange < 0L ->
                                    "Spending decreased"
                                else -> "Spending unchanged"
                            }

                            val changeColor = when {
                                !hasExpenseBaseline ->
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                insights.expenseChange > 0L ->
                                    MaterialTheme.colorScheme.error
                                insights.expenseChange < 0L ->
                                    MaterialTheme.colorScheme.primary
                                else ->
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            val changePercent =
                                insights.expenseChangePercent?.let {
                                    String.format(
                                        java.util.Locale.getDefault(),
                                        "%.1f%%",
                                        kotlin.math.abs(it)
                                    )
                                } ?: if (insights.expenseChange == 0L) {
                                    "No change"
                                } else {
                                    "No prior baseline"
                                }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {
                                Text(
                                    changeLabel,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = changeColor
                                )

                                Text(
                                    changePercent,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = changeColor,
                                    maxLines = 2,
                                    textAlign = TextAlign.End
                                )
                            }

                            if (insights.topCategoryId != null) {
                                HorizontalDivider()

                                Text(
                                    "Top spending category",
                                    style = MaterialTheme.typography.labelLarge
                                )

                                Text(
                                    data.categories[
                                        insights.topCategoryId
                                    ]?.ifBlank {
                                        "Uncategorized"
                                    } ?: "Uncategorized",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Text(
                                    formatter.convAmount(
                                        insights.topCategoryAmount,
                                        account.currencyUnit
                                    ) + "  •  " +
                                        String.format(
                                            java.util.Locale.getDefault(),
                                            "%.1f%% of gross category spending",
                                            insights.topCategoryPercent
                                        ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    "No category spending this month",
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (
                                insights.grossCategorySpending !=
                                insights.currentNetExpenses
                            ) {
                                Text(
                                    "Gross category spending differs from " +
                                        "net transaction expenses because of " +
                                        "transaction category allocation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    }

                    @Composable
                    fun MonthlyTrendPanel() {
                    ArthvexaMonthlyAnalyticsCard {
                    MonthlyActivityTrendChart(
                        points = trendPoints,
                        currency = account.currencyUnit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                    }
                    }

                    // C3: Preserve the same state and data while arranging presentation.
                    fun signedAmount(value: Long): String {
                        val formatted = formatter.convAmount(
                            value,
                            account.currencyUnit
                        )
                        return if (value > 0L) "+$formatted" else formatted
                    }

                    @Composable
                    fun MonthlyComparisonPanel() {
                    if (month.isEmpty() && previousMonth.isEmpty()) {
                        Text(
                            "No activity in the previous month either.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                    ArthvexaMonthlyAnalyticsCard(subtleDark = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            monthStart.format(
                                DateTimeFormatter.ofPattern(
                                    "MMMM yyyy"
                                )
                            ) + " compared with " +
                                monthStart.minusMonths(1).format(
                                    DateTimeFormatter.ofPattern(
                                        "MMMM yyyy"
                                    )
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            modifier = Modifier.semantics {
                                stateDescription = if (account.id in expandedComparisons) "Expanded" else "Collapsed"
                                contentDescription = "Comparison for " + account.label
                            },

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
                                contentDescription = null
                            )
                        }
                    }
                    if (account.id in expandedComparisons) {
                    Spacer(modifier = Modifier.height(12.dp))
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
                        "No prior baseline"
                    }

                    val incomePercent = if (previousIncome != 0L) {
                        String.format(
                            java.util.Locale.getDefault(),
                            "%+.1f%%",
                            incomeChange.toDouble() /
                                kotlin.math.abs(previousIncome.toDouble()) * 100.0
                        )
                    } else {
                        "No prior baseline"
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
                                    color = performanceColor(change, higherIsBetter),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    ChangeRow(
                        label = "Expense change",
                        change = expenseChange,
                        higherIsBetter = false,
                        percentage = expensePercent
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ChangeRow(
                        label = "Net cash flow change",
                        change = netChange,
                        higherIsBetter = true
                    )
                    }
                    }

                    }
                    }

                    @Composable
                    fun CategoryDistributionPanel() {
                    // Center the complete category section, including reconciliation
                    // and spending changes, without narrowing its parent surface.
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                    Column(
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                    ) {
                    if (account.id in expandedComparisons) {
                    KKCategoryDistributionChart(
                        categoryTotals =
                            monthlySummary.currentCategorySpending,
                        categoryNames = data.categories,
                        currency = account.currencyUnit,
                        compact = insightsMode,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val reconciliation = monthlySummary.reconciliation

                    if (reconciliation.reconciliationDifference != 0L) {
                        Text(
                            "Category spending reconciliation",
                            style = MaterialTheme.typography.labelLarge
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Gross category expenses")
                            Text(
                                formatter.convAmount(
                                    reconciliation.grossCategoryExpenses,
                                    account.currencyUnit
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reconciliation difference")
                            Text(
                                formatter.convAmount(
                                    reconciliation.reconciliationDifference,
                                    account.currencyUnit
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Net transaction expenses")
                            Text(
                                formatter.convAmount(
                                    reconciliation.netTransactionExpenses,
                                    account.currencyUnit
                                )
                            )
                        }
                    }

                    val currentCategories =
                        monthlySummary.currentCategorySpending

                    val previousCategories =
                        monthlySummary.previousCategorySpending

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
                        if (insightsMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Category spending changes", style = MaterialTheme.typography.labelLarge)
                                TextButton(onClick = {
                                    expandedCategoryChanges = if (account.id in expandedCategoryChanges)
                                        expandedCategoryChanges - account.id
                                    else expandedCategoryChanges + account.id
                                }) {
                                    Text(if (account.id in expandedCategoryChanges) "Hide" else "Show")
                                    Icon(
                                        if (account.id in expandedCategoryChanges) Icons.Filled.ExpandLess
                                        else Icons.Filled.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }
                        } else {
                            Text("Category spending changes", style = MaterialTheme.typography.labelLarge)
                        }

                        if (!insightsMode || account.id in expandedCategoryChanges) categoryChanges.forEach { (categoryId, change) ->
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
                                    modifier = Modifier.weight(1.4f)
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
                                    color = spendingColor,
                                    textAlign = TextAlign.End,
                                    softWrap = true
                                )
                            }
                        }
                    }
                    }
                    }
                    } // Category content column
                    } // Centering box

                    androidx.compose.foundation.layout.BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                        val useColumns = insightsMode && maxWidth >= 700.dp && fontScale <= 1.15f
                        if (useColumns) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SpendingInsightsPanel()
                                    MonthlyComparisonPanel()
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MonthlyTrendPanel()
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpendingInsightsPanel()
                                MonthlyTrendPanel()
                                MonthlyComparisonPanel()
                            }
                        }
                    }

                    // Category analysis is a separate full-width section in both
                    // orientations. Never compose it inside the landscape Row.
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.height(if (insightsMode) 12.dp else 8.dp)
                    )
                    CategoryDistributionPanel()

                    val byCategory =
                        monthlySummary.currentCategorySpending
                            .toList()
                            .sortedByDescending { it.second }
                            .take(4)
                    // The compact Insights donut already lists the top categories.
                    if (byCategory.isNotEmpty() && !insightsMode) {
                        Text("Top spending categories", style = MaterialTheme.typography.labelLarge)
                        val maximum = byCategory.first().second.coerceAtLeast(1L)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            byCategory.forEach { (id, value) ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
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
                                val fraction =
                                    (value.toDouble() / maximum)
                                        .toFloat()
                                        .coerceIn(0f, 1f)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .semantics {
                                            progressBarRangeInfo =
                                                ProgressBarRangeInfo(
                                                    fraction,
                                                    0f..1f
                                                )
                                        }
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .height(4.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary
                                            )
                                    )
                                }
                                }
                        }
                        }
                    }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                }
                if (!insightsMode) {
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
                            modifier = Modifier.semantics {
                                stateDescription = if (recentExpanded) "Expanded" else "Collapsed"
                                contentDescription = "Recent transactions"
                            },

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
                            contentDescription = null
                        )
                    }
                }
                if (recentExpanded) {
                val accountById = remember(activeAccounts) {
                    activeAccounts.associateBy { it.id }
                }
                val recent = remember(data, accountById, monthStart, zone) {
                    data.entries.filter {
                        accountById.containsKey(it.accountId) &&
                        it.epoch >= start &&
                        it.epoch < end
                    }.take(5)
                }
                if (recent.isEmpty()) Text("No transactions for this month")
                recent.forEach { entry ->
                    val account = accountById[entry.accountId] ?: return@forEach
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.description.ifBlank { data.categories[entry.categoryId] ?: "Transaction" }, style = MaterialTheme.typography.bodyMedium)
                            Text(account.label + " · " + Instant.ofEpochSecond(entry.epoch).atZone(zone).toLocalDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            formatter.convAmount(entry.amount, account.currencyUnit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                entry.amount > 0L -> arthvexaFinancialColor("income")
                                entry.amount < 0L -> arthvexaFinancialColor("expense")
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Text("Transfers excluded. Split expenses are grouped by their assigned categories. Amounts are not combined across currencies.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                } // Home-only recent transactions
            }
        }
    }
    }
}
