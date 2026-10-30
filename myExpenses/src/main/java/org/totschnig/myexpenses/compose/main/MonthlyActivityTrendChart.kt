package org.totschnig.myexpenses.compose.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.util.convAmount
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun MonthlyActivityTrendChart(
    points: List<MonthlyTrendPoint>,
    currency: CurrencyUnit,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val formatter = LocalCurrencyFormatter.current

    val incomeColor = Color(0xFF00BFA5)
    val expenseColor = Color(0xFFF59E0B)
    val netColor = Color(0xFF818CF8)

    var selectedIndex by remember(points) {
        mutableIntStateOf(points.lastIndex)
    }

    val selected = points[
        selectedIndex.coerceIn(0, points.lastIndex)
    ]

    val values = points.flatMap {
        listOf(
            it.income.toDouble(),
            it.expenses.toDouble(),
            it.netCashFlow.toDouble()
        )
    }

    val minimum = minOf(0.0, values.minOrNull() ?: 0.0)
    val maximum = maxOf(0.0, values.maxOrNull() ?: 0.0)
    val range = (maximum - minimum).coerceAtLeast(1.0)

    val axisFormatter = remember(currency.code) {
        NumberFormat.getNumberInstance(
            Locale.getDefault()
        ).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }
    }

    val fractionDigits = remember(currency.code) {
        runCatching {
            java.util.Currency.getInstance(
                currency.code
            ).defaultFractionDigits
        }.getOrDefault(2).coerceAtLeast(0)
    }

    fun axisLabel(value: Double): String {
        val major = value /
            Math.pow(10.0, fractionDigits.toDouble())

        val absolute = kotlin.math.abs(major)

        val divisor = when {
            absolute >= 1_000_000.0 -> 1_000_000.0
            absolute >= 1_000.0 -> 1_000.0
            else -> 1.0
        }

        val suffix = when (divisor) {
            1_000_000.0 -> "M"
            1_000.0 -> "K"
            else -> ""
        }

        return axisFormatter.format(major / divisor) +
            suffix
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Six-month trends",
            style = MaterialTheme.typography.titleSmall
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrendLegend("Income", incomeColor)
            TrendLegend("Expenses", expenseColor)
            TrendLegend("Net", netColor)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val left = 48.dp.toPx()
                        val right = size.width - 12.dp.toPx()
                        val width = (right - left)
                            .coerceAtLeast(1f)

                        val fraction = (
                            (tap.x - left) / width
                        ).coerceIn(0f, 1f)

                        selectedIndex = (
                            fraction * points.lastIndex
                        ).roundToInt().coerceIn(
                            0, points.lastIndex
                        )
                    }
                }
        ) {
            val left = 48.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val top = 12.dp.toPx()
            val bottom = size.height - 12.dp.toPx()

            val width = right - left
            val height = bottom - top

            fun x(index: Int): Float =
                if (points.size == 1) {
                    left + width / 2f
                } else {
                    left + width * index / points.lastIndex
                }

            fun y(value: Long): Float =
                (
                    bottom -
                    ((value.toDouble() - minimum) / range) *
                        height
                ).toFloat()

            val baseline = (
                bottom - ((0.0 - minimum) / range) * height
            ).toFloat()

            // Subtle gridlines with compact axis values.
            val axisPaint = android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = android.graphics.Color.LTGRAY
                alpha = 180
                textSize = 10.sp.toPx()
                textAlign =
                    android.graphics.Paint.Align.RIGHT
            }

            for (step in 0..4) {
                val fraction = step / 4.0
                val gridY = top + height * step / 4f
                val gridValue =
                    maximum - fraction * range

                drawLine(
                    color = Color.Gray.copy(alpha = 0.12f),
                    start = Offset(left, gridY),
                    end = Offset(right, gridY),
                    strokeWidth = 0.7.dp.toPx()
                )

                val zeroLabelClearance =
                    axisPaint.textSize * 1.4f

                if (
                    kotlin.math.abs(gridY - baseline) >
                    zeroLabelClearance
                ) {
                    drawContext.canvas.nativeCanvas.drawText(
                        axisLabel(gridValue),
                        left - 7.dp.toPx(),
                        gridY + axisPaint.textSize * 0.35f,
                        axisPaint
                    )
                }
            }

            // Emphasize the exact zero baseline.
            drawLine(
                color = Color.Gray.copy(alpha = 0.65f),
                start = Offset(left, baseline),
                end = Offset(right, baseline),
                strokeWidth = 1.3.dp.toPx()
            )

            // Always label zero at its actual position.
            val zeroPaint = android.graphics.Paint(
                axisPaint
            ).apply {
                color = android.graphics.Color.WHITE
                alpha = 220
                isFakeBoldText = true
            }

            drawContext.canvas.nativeCanvas.drawText(
                "0",
                left - 7.dp.toPx(),
                baseline + zeroPaint.textSize * 0.35f,
                zeroPaint
            )

            // Highlight the selected month.
            val safeSelectedIndex = selectedIndex.coerceIn(
                0, points.lastIndex
            )

            val selectedX = x(safeSelectedIndex)

            drawLine(
                color = Color.Gray.copy(alpha = 0.45f),
                start = Offset(selectedX, top),
                end = Offset(selectedX, bottom),
                strokeWidth = 1.dp.toPx()
            )

            fun DrawScope.drawSeries(
                color: Color,
                selector: (MonthlyTrendPoint) -> Long
            ) {
                val path = Path()

                points.forEachIndexed { index, point ->
                    val position = Offset(
                        x(index),
                        y(selector(point))
                    )

                    if (index == 0) {
                        path.moveTo(position.x, position.y)
                    } else {
                        path.lineTo(position.x, position.y)
                    }
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                val safeIndex = selectedIndex.coerceIn(
                    0, points.lastIndex
                )
                val point = points[safeIndex]

                val markerCenter = Offset(
                    x(safeIndex),
                    y(selector(point))
                )

                // Outer ring improves visibility.
                drawCircle(
                    color = color.copy(alpha = 0.25f),
                    radius = 8.dp.toPx(),
                    center = markerCenter
                )

                drawCircle(
                    color = color,
                    radius = 4.5.dp.toPx(),
                    center = markerCenter
                )
            }

            drawSeries(incomeColor) { it.income }
            drawSeries(expenseColor) { it.expenses }
            drawSeries(netColor) { it.netCashFlow }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { index, point ->
                Text(
                    text = point.month.format(
                        DateTimeFormatter.ofPattern("MMM")
                    ),
                    style = if (index == selectedIndex) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelSmall
                    },
                    color = if (index == selectedIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Text(
            text = selected.month.format(
                DateTimeFormatter.ofPattern("MMMM yyyy")
            ),
            style = MaterialTheme.typography.labelLarge
        )

        TrendValue(
            label = "Income",
            value = formatter.convAmount(
                selected.income, currency
            ),
            color = incomeColor
        )

        TrendValue(
            label = "Expenses",
            value = formatter.convAmount(
                selected.expenses, currency
            ),
            color = expenseColor
        )

        TrendValue(
            label = "Net cash flow",
            value = formatter.convAmount(
                selected.netCashFlow, currency
            ),
            color = netColor
        )
    }
}

@Composable
private fun TrendLegend(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun TrendValue(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
