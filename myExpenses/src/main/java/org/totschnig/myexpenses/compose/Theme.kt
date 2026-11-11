package org.totschnig.myexpenses.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
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

    // A2 shared type scale. Keep font scaling in sp and use the same Roboto-family
    // fallback as Android Views (sans-serif) without bundling new font assets.
    val baseTypography = Typography()
    val arthvexaTypography = baseTypography.copy(
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp)
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