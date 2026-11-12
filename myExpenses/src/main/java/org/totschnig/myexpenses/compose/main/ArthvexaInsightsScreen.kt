package org.totschnig.myexpenses.compose.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.compose.ArthvexaBrandMark
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.viewmodel.data.FullAccount

/** Read-only account insights. Values use existing account calculations and currencies. */
@Composable
internal fun ArthvexaInsightsScreen(accounts: List<FullAccount>) {
    val formatter = LocalCurrencyFormatter.current
    var monthlyExpanded by rememberSaveable { mutableStateOf(true) }
    // A null selection means all accounts; never combine their currencies into a total.
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    val activeAccounts = accounts.filter { it.id > 0 }
    val accountChipListState = rememberLazyListState()
    val selectedChipIndex = activeAccounts.indexOfFirst { it.id == selectedAccountId } + 1
    LaunchedEffect(selectedChipIndex) {
        accountChipListState.animateScrollToItem(selectedChipIndex.coerceAtLeast(0))
    }

    val visibleAccounts = activeAccounts.filter {
        selectedAccountId == null || it.id == selectedAccountId
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArthvexaBrandMark()
        Text("Insights", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (selectedAccountId == null) "Account balances" else "Monthly trends and spending analysis",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Accounts", style = MaterialTheme.typography.titleSmall)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = accountChipListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(
                selected = selectedAccountId == null,
                onClick = { selectedAccountId = null },
                label = { Text("All accounts") },
            ) }
            itemsIndexed(activeAccounts, key = { _, account -> account.id }) { _, account ->
                FilterChip(
                    selected = selectedAccountId == account.id,
                    onClick = { selectedAccountId = account.id },
                    label = { Text(account.label) },
                )
            }
        }
        if (selectedAccountId != null) {
            // A single account is the only input to detailed analytics. This avoids
            // stacking full charts for every account and never combines currencies.
            KKMonthlyActivity(
                accounts = visibleAccounts,
                expanded = monthlyExpanded,
                onExpandedChange = { monthlyExpanded = it },
                insightsMode = true
            )
        } else {
            Text("Account balances", style = MaterialTheme.typography.titleMedium)
        if (visibleAccounts.isEmpty()) {
            Text("Add an account to view financial insights.")
        }
        visibleAccounts.forEach { account ->
            // Compact balance card: avoid repeating a full-sized lifetime summary
            // while keeping every figure scoped to its own account/currency.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        account.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatter.convAmount(account.currentBalance, account.currencyUnit),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lifetime income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatter.convAmount(account.sumIncome, account.currencyUnit), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lifetime expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatter.convAmount(account.sumExpense, account.currencyUnit), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Text(
            "Select an account for monthly analysis. Amounts remain separate by currency.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
    }
}
