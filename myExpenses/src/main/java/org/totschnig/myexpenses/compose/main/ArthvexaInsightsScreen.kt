package org.totschnig.myexpenses.compose.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.compose.ArthvexaBrandMark
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.viewmodel.data.FullAccount

/** Read-only account insights. Values use existing account calculations and currencies. */
@Composable
internal fun ArthvexaInsightsScreen(accounts: List<FullAccount>) {
    val formatter = LocalCurrencyFormatter.current
    var selectedView by remember { mutableStateOf(0) }
    var monthlyExpanded by remember { mutableStateOf(true) }
    // A null selection means all accounts; never combine their currencies into a total.
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    val activeAccounts = accounts.filter { it.id > 0 }
    val visibleAccounts = activeAccounts.filter {
        selectedAccountId == null || it.id == selectedAccountId
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArthvexaBrandMark()
        Text("Insights", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (selectedView == 0) "Lifetime account overview" else "Monthly account analysis",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Overview", "Monthly analytics").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedView == index,
                    onClick = { selectedView = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    label = { Text(label) }
                )
            }
        }
        Text("Filter by account", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedAccountId == null,
                onClick = { selectedAccountId = null },
                label = { Text("All accounts") },
            )
            activeAccounts.forEach { account ->
                FilterChip(
                    selected = selectedAccountId == account.id,
                    onClick = { selectedAccountId = account.id },
                    label = { Text(account.label) },
                )
            }
        }
        if (selectedView == 1) {
            KKMonthlyActivity(
                accounts = visibleAccounts,
                expanded = monthlyExpanded,
                onExpandedChange = { monthlyExpanded = it }
            )
        } else {
        if (visibleAccounts.isEmpty()) {
            Text("Add an account to view financial insights.")
        }
        visibleAccounts.forEach { account ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(account.label, style = MaterialTheme.typography.titleMedium)
                    Text("Current balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatter.convAmount(account.currentBalance, account.currencyUnit), style = MaterialTheme.typography.titleLarge)
                    Text("Lifetime income: " + formatter.convAmount(account.sumIncome, account.currencyUnit), color = MaterialTheme.colorScheme.primary)
                    Text("Lifetime expenses: " + formatter.convAmount(account.sumExpense, account.currencyUnit), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text(
            "Lifetime figures are shown per account and currency. Transfers and split transactions follow existing account calculations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
    }
}
