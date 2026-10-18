package org.totschnig.myexpenses.compose.main
import org.totschnig.myexpenses.util.convAmount

import android.content.Context
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
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
private data class ActivityEntry(val accountId: Long, val amount: Long, val epoch: Long, val description: String, val categoryId: Long)
private data class ActivitySnapshot(val entries: List<ActivityEntry>, val categories: Map<Long, String>)

private fun readActivity(context: Context): ActivitySnapshot {
    val entries = mutableListOf<ActivityEntry>()
    val categories = mutableMapOf<Long, String>()
    val selection = "$KEY_CATID != 0 AND $KEY_TRANSFER_PEER IS NULL AND ($KEY_CR_STATUS IS NULL OR $KEY_CR_STATUS != ?) AND ($KEY_STATUS IS NULL OR $KEY_STATUS != ?)"
    context.contentResolver.query(
        TransactionProvider.TRANSACTIONS_URI,
        arrayOf(KEY_ROWID, KEY_ACCOUNTID, KEY_AMOUNT, KEY_DATE, KEY_COMMENT, KEY_CATID),
        selection, arrayOf("VOID", STATUS_ARCHIVED.toString()), "$KEY_DATE DESC"
    )?.use { cursor ->
        val account = cursor.getColumnIndexOrThrow(KEY_ACCOUNTID)
        val amount = cursor.getColumnIndexOrThrow(KEY_AMOUNT)
        val date = cursor.getColumnIndexOrThrow(KEY_DATE)
        val comment = cursor.getColumnIndexOrThrow(KEY_COMMENT)
        val category = cursor.getColumnIndexOrThrow(KEY_CATID)
        while (cursor.moveToNext()) {
            entries.add(ActivityEntry(cursor.getLong(account), cursor.getLong(amount), cursor.getLong(date),
                cursor.getString(comment).orEmpty(), cursor.getLong(category)))
        }
    }
    // Category labels are optional; reporting must still work if the category provider differs.
    try {
        context.contentResolver.query(TransactionProvider.CATEGORIES_URI, arrayOf(KEY_ROWID, KEY_LABEL), null, null, null)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(KEY_ROWID)
            val label = cursor.getColumnIndexOrThrow(KEY_LABEL)
            while (cursor.moveToNext()) categories[cursor.getLong(id)] = cursor.getString(label).orEmpty()
        }
    } catch (_: Exception) { /* fall back to Uncategorized */ }
    return ActivitySnapshot(entries, categories)
}

@Composable
internal fun KKMonthlyActivity(accounts: List<FullAccount>) {
    val context = LocalContext.current
    val formatter = LocalCurrencyFormatter.current
    var revision by remember { mutableStateOf(0) }
    var snapshot by remember { mutableStateOf<ActivitySnapshot?>(null) }
    var error by remember { mutableStateOf(false) }
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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This month", style = MaterialTheme.typography.titleLarge)
            val monthStart = LocalDate.now().withDayOfMonth(1)
            Text(monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error) {
                Text("Monthly activity is temporarily unavailable. Account balances are unaffected.")
            } else if (data == null) {
                Text("Loading activity...")
            } else {
                val zone = ZoneId.systemDefault()
                val start = monthStart.atStartOfDay(zone).toEpochSecond()
                val end = monthStart.plusMonths(1).atStartOfDay(zone).toEpochSecond()
                val activeAccounts = accounts.filter { it.id > 0 }
                activeAccounts.forEach { account ->
                    val month = data.entries.filter { it.accountId == account.id && it.epoch >= start && it.epoch < end }
                    val income = month.filter { it.amount > 0 }.sumOf { it.amount }
                    val expense = month.filter { it.amount < 0 }.sumOf { it.amount }
                    Text(account.label, style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Income: " + formatter.convAmount(income, account.currencyUnit))
                        Text("Expenses: " + formatter.convAmount(expense, account.currencyUnit))
                    }
                    val byCategory = month.filter { it.amount < 0 }.groupBy { it.categoryId }
                        .mapValues { (_, txns) -> txns.sumOf { -it.amount } }
                        .toList().sortedByDescending { it.second }.take(4)
                    if (byCategory.isNotEmpty()) {
                        Text("Top spending categories", style = MaterialTheme.typography.labelLarge)
                        val maximum = byCategory.first().second.coerceAtLeast(1L)
                        byCategory.forEach { (id, value) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(data.categories[id]?.ifBlank { "Uncategorized" } ?: "Uncategorized", style = MaterialTheme.typography.bodySmall)
                                Text(formatter.convAmount(-value, account.currencyUnit), style = MaterialTheme.typography.bodySmall)
                            }
                            LinearProgressIndicator(progress = { (value.toDouble() / maximum).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    HorizontalDivider()
                }
                Text("Recent transactions", style = MaterialTheme.typography.titleMedium)
                val accountById = activeAccounts.associateBy { it.id }
                val recent = data.entries.filter { accountById.containsKey(it.accountId) }.take(5)
                if (recent.isEmpty()) Text("No transactions yet")
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
                Text("Transfers and voided transactions excluded. Amounts are not combined across currencies.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
