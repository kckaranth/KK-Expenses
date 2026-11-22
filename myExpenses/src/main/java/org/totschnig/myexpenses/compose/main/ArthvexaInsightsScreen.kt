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
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.compose.ArthvexaBrandMark
import org.totschnig.myexpenses.viewmodel.data.FullAccount

/** Read-only account insights. Values use existing account calculations and currencies. */
@Composable
internal fun ArthvexaInsightsScreen(accounts: List<FullAccount>) {
    var monthlyExpanded by rememberSaveable { mutableStateOf(true) }
    // A null selection means all accounts; never combine their currencies into a total.
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    val activeAccounts = accounts.filter { it.id > 0 }
    val accountChipListState = rememberLazyListState()
    LaunchedEffect(activeAccounts.map { it.id }) {
        if (selectedAccountId == null && activeAccounts.isNotEmpty()) {
            selectedAccountId = activeAccounts.first().id
        } else if (selectedAccountId != null && activeAccounts.none { it.id == selectedAccountId }) {
            selectedAccountId = activeAccounts.firstOrNull()?.id
        }
    }
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
            "Spending patterns, monthly comparisons and financial trends",
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
            Text(
                if (activeAccounts.isEmpty()) "Add an account to view financial insights."
                else "Select an account above to explore its trends and spending analysis. Currencies are never combined.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
