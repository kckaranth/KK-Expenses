package org.totschnig.myexpenses.compose

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Shared A2 Vibrant Premium semantic accents. Financial income/expense/transfer
 * colors continue to come from the existing financial color resources. */
object ArthvexaA2Accents {
    val emerald = Color(0xFF008B73)
    val mint = Color(0xFF75DFC0)
    val oceanBlue = Color(0xFF3B82F6)
    val royalPurple = Color(0xFF8B5CF6)
    val coral = Color(0xFFF97360)
    val amber = Color(0xFFF5A524)
    val rose = Color(0xFFEC4899)
    val skyBlue = Color(0xFF38BDF8)
}

object ArthvexaThemeColors {

    val Dark = darkColorScheme(
        primary = Color(0xFF75DFC0),
        onPrimary = Color(0xFF001B19),
        primaryContainer = Color(0xFF003B35),
        onPrimaryContainer = Color(0xFFB5FFF0),
        secondary = Color(0xFF8CBFFF),
        onSecondary = Color(0xFF001B19),
        background = Color(0xFF102521),
        onBackground = Color(0xFFE8F5F1),
        surface = Color(0xFF242729),
        surfaceDim = Color(0xFF102521),
        surfaceBright = Color(0xFF303739),
        surfaceContainerLowest = Color(0xFF102521),
        surfaceContainerLow = Color(0xFF1C2827),
        surfaceContainer = Color(0xFF242729),
        surfaceContainerHigh = Color(0xFF2B3333),
        surfaceContainerHighest = Color(0xFF34403E),
        onSurface = Color(0xFFE8F5F1),
        surfaceVariant = Color(0xFF174B44),
        onSurfaceVariant = Color(0xFFB8D2CB),
        outline = Color(0xFF668B82),
        outlineVariant = Color(0xFF315E57),
        error = Color(0xFFFF8A75),
        onError = Color(0xFF3B0B05)
    )

    val Light = lightColorScheme(
        primary = Color(0xFF007C67),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1F5E9),
        onPrimaryContainer = Color(0xFF003B35),
        secondary = Color(0xFF246DAD),
        onSecondary = Color.White,
        background = Color(0xFFF4F8F6),
        onBackground = Color(0xFF17332D),
        surface = Color.White,
        onSurface = Color(0xFF17332D),
        surfaceDim = Color(0xFFE2EBE6),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF4FAF7),
        surfaceContainer = Color(0xFFEAF3EF),
        surfaceContainerHigh = Color(0xFFE2F0EA),
        surfaceContainerHighest = Color(0xFFDCECE5),
        surfaceVariant = Color(0xFFE8F4EF),
        onSurfaceVariant = Color(0xFF45675D),
        outline = Color(0xFF78938B),
        outlineVariant = Color(0xFFB8D2C7),
        error = Color(0xFFC45136),
        onError = Color.White
    )
}
