package org.totschnig.myexpenses.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = Color(0xFF80D5C3),
            onPrimary = Color(0xFF00382E),
            secondary = Color(0xFFA4D0C3),
            background = Color(0xFF101B1A),
            surface = Color(0xFF152220),
            surfaceVariant = Color(0xFF394945)
        )
        else -> lightColorScheme(
            primary = Color(0xFF006B5A),
            onPrimary = Color.White,
            secondary = Color(0xFF45675D),
            background = Color(0xFFF5FAF7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDCEBE4)
        )
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