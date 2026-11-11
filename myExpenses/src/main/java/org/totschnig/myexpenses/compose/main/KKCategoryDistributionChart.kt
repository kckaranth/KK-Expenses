package org.totschnig.myexpenses.compose.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieEntry
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.compose.PieChartCompose
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.util.convAmount
import java.util.Locale

/**
 * Account-specific category spending chart.
 *
 * Uses existing monthly category totals in minor units.
 * Does not change transactions or financial calculations.
 */
@Composable
internal fun KKCategoryDistributionChart(
    categoryTotals: Map<Long, Long>,
    categoryNames: Map<Long, String>,
    currency: CurrencyUnit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val formatter = LocalCurrencyFormatter.current

    val categories = remember(categoryTotals, categoryNames) {
        categoryTotals
            .filterValues { it > 0L }
            .toList()
            .sortedWith(
                compareByDescending<Pair<Long, Long>> { it.second }
                    .thenBy { it.first }
            )
    }

    val palette = remember {
        listOf(
            0xFF00BFA5.toInt(),
            0xFF818CF8.toInt(),
            0xFFF59E0B.toInt(),
            0xFF38BDF8.toInt(),
            0xFFF472B6.toInt(),
            0xFFA78BFA.toInt()
        )
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
            var showAllCategories by remember(categories, compact) { mutableStateOf(!compact) }
            val chartCategories = remember(categories, compact) {
                if (!compact || categories.size <= 5) categories.map { Triple(it.first, it.second, false) }
                else categories.take(5).map { Triple(it.first, it.second, false) } +
                    Triple(Long.MIN_VALUE, categories.drop(5).sumOf { it.second }, true)
            }
            var selectedIndex by remember(chartCategories) {
                mutableIntStateOf(0)
            }

            val selected = chartCategories[
                selectedIndex.coerceIn(chartCategories.indices)
            ]

            val total = categories.sumOf { it.second }
            val maximumAmount =
                chartCategories.maxOf { it.second }.toDouble()

            fun percentage(amount: Long): String {
                val value = if (total > 0L) {
                    amount.toDouble() * 100.0 / total.toDouble()
                } else {
                    0.0
                }

                return String.format(
                    Locale.getDefault(),
                    "%.1f%%",
                    value
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 182.dp else 230.dp),
                contentAlignment = Alignment.Center
            ) {
                PieChartCompose(
                    modifier = Modifier
                        .size(if (compact) 174.dp else 220.dp),
                    factory = { context ->
                        PieChart(context).apply {
                            setDrawEntryLabels(false)
                            setDrawMarkers(false)
                            setExtraOffsets(0f, 0f, 0f, 0f)
                            minOffset = 0f
                        }
                    },
                    holeRadius = 72f,
                    showEntryLabels = false,
                    showSelectionCenterText = false,
                    data = chartCategories.map { (id, amount, other) ->
                        PieEntry(
                            (amount.toDouble() /
                                maximumAmount).toFloat(),
                            if (other) "Others" else categoryNames[id] ?: "Uncategorized"
                        )
                    },
                    colors = chartCategories.indices.map {
                        palette[it % palette.size]
                    },
                    onValueSelected = { index ->
                        if (index != null &&
                            index in chartCategories.indices
                        ) {
                            selectedIndex = index
                        }
                    }
                )
            }

            Text(
                text = (
                    if (selected.third) "Others" else categoryNames[selected.first]
                        ?: "Uncategorized"
                ) + "  •  " + percentage(selected.second),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = formatter.convAmount(
                    selected.second,
                    currency
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Category breakdown",
                style = MaterialTheme.typography.labelLarge
            )

            val displayedCategories = if (showAllCategories) categories else categories.take(5)
            displayedCategories.forEachIndexed { index, category ->
                val (id, amount) = category

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedIndex = index.coerceAtMost(chartCategories.lastIndex)
                        },
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = CircleShape,
                        color = Color(
                            palette[index % palette.size]
                        )
                    ) {}

                    Text(
                        text = categoryNames[id]
                            ?: "Uncategorized",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        softWrap = true
                    )

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = formatter.convAmount(
                                amount,
                                currency
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = percentage(amount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }
                }
            }
            if (compact && categories.size > 5) {
                TextButton(onClick = { showAllCategories = !showAllCategories }) {
                    Text(if (showAllCategories) "Show fewer categories" else "Show all ${categories.size} categories")
                }
                if (!showAllCategories) {
                    Text(
                        "Others: " + formatter.convAmount(categories.drop(5).sumOf { it.second }, currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
