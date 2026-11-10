package org.totschnig.myexpenses.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

    val baseTypography = Typography()
    val arthvexaTypography = baseTypography.copy(
        headlineMedium = baseTypography.headlineMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = baseTypography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = baseTypography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = baseTypography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = baseTypography.bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = baseTypography.bodyMedium.copy(fontSize = 14.sp),
        labelMedium = baseTypography.labelMedium.copy(fontSize = 12.sp)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = arthvexaTypography
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