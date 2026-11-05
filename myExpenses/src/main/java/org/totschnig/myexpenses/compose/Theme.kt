package org.totschnig.myexpenses.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.Color
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.util.getDateTimeFormatter

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Retained for compatibility with existing callers.
    // Arthvexa brand colors take precedence over dynamic colors.
    @Suppress("UNUSED_VARIABLE")
    val dynamicColorsRequested = dynamicColor

    val context = LocalContext.current
    val colorScheme = if (darkTheme) {
        ArthvexaThemeColors.Dark
    } else {
        ArthvexaThemeColors.Light
    }

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        val injector = context.injector
        CompositionLocalProvider(
            LocalCurrencyFormatter provides injector.currencyFormatter(),
            LocalDateFormatter provides getDateTimeFormatter(context),
            LocalColors provides Colors(
                income = colorResource(id = R.color.colorIncome),
                expense = colorResource(id = R.color.colorExpense),
                transfer = colorResource(id = R.color.colorTransfer),
            ),
            LocalTracker provides injector.tracker(),
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            LocalCurrencyContext provides injector.currencyContext(),
        ) {
           ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
        }
    }
}