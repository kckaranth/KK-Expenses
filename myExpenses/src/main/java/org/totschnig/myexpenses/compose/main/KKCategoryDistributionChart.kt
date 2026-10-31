package org.totschnig.myexpenses.compose.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.util.convAmount
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieEntry
import org.totschnig.myexpenses.compose.PieChartCompose

/**
 * Personal category spending visualization.
 *
 * Amounts are supplied in integer minor units.
 * No currency conversion or entitlement changes occur.
 */
@Composable
internal fun KKCategoryDistributionChart(
    categoryTotals: Map<Long, Long>,
    categoryNames: Map<Long, String>,
    currency: CurrencyUnit,
    modifier: Modifier = Modifier
) {
    val formatter = LocalCurrencyFormatter.current

    val categories = remember(
        categoryTotals,
        categoryNames
    ) {
        categoryTotals
            .filterValues { it > 0L }
            .toList()
            .sortedByDescending { it.second }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Spending by category",
            style = MaterialTheme.typography.titleMedium
        )

        if (categories.isEmpty()) {
            Text(
                text = "No category spending for this period",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            var selectedIndex by remember(categories) {
                mutableIntStateOf(0)
            }

            val palette = listOf(
                0xFF00BFA5.toInt(),
                0xFF818CF8.toInt(),
                0xFFF59E0B.toInt(),
                0xFF38BDF8.toInt(),
                0xFFF472B6.toInt(),
                0xFFA78BFA.toInt()
            )

            val maximumAmount =
                categories.maxOf { it.second }.toDouble()

            PieChartCompose(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                factory = { context ->
                    PieChart(context)
                },
                data = categories.map { (id, amount) ->
                    PieEntry(
                        (amount.toDouble() /
                            maximumAmount).toFloat(),
                        categoryNames[id] ?: "Uncategorized"
                    )
                },
                colors = categories.indices.map {
                    palette[it % palette.size]
                },
                onValueSelected = { index ->
                    if (index != null) {
                        selectedIndex = index.coerceIn(
                            categories.indices
                        )
                    }
                }
            )

            val selected = categories[
                selectedIndex.coerceIn(categories.indices)
            ]

            Text(
                text = (
                    categoryNames[selected.first]
                        ?: "Uncategorized"
                ) + ": " + formatter.convAmount(
                    selected.second,
                    currency
                ),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
