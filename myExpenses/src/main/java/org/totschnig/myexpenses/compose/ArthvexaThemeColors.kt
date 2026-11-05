package org.totschnig.myexpenses.compose

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object ArthvexaThemeColors {

    val Dark = darkColorScheme(
        primary = Color(0xFFB5FFF0),
        onPrimary = Color(0xFF001B19),
        primaryContainer = Color(0xFF003B35),
        onPrimaryContainer = Color(0xFFB5FFF0),
        secondary = Color(0xFF00BFA5),
        onSecondary = Color(0xFF001B19),
        background = Color(0xFF001B19),
        onBackground = Color(0xFFE8F5F1),
        surface = Color(0xFF003B35),
        onSurface = Color(0xFFE8F5F1),
        surfaceVariant = Color(0xFF174B44),
        onSurfaceVariant = Color(0xFFB8D2CB),
        outline = Color(0xFF668B82),
        outlineVariant = Color(0xFF315E57),
        error = Color(0xFFFF8A75),
        onError = Color(0xFF3B0B05)
    )

    val Light = lightColorScheme(
        primary = Color(0xFF006B5A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1F5E9),
        onPrimaryContainer = Color(0xFF003B35),
        secondary = Color(0xFF008F7A),
        onSecondary = Color.White,
        background = Color(0xFFF3F7F5),
        onBackground = Color(0xFF17332D),
        surface = Color.White,
        onSurface = Color(0xFF17332D),
        surfaceDim = Color(0xFFE2EBE6),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8FBF9),
        surfaceContainer = Color(0xFFEFF5F1),
        surfaceContainerHigh = Color(0xFFE8F1EC),
        surfaceContainerHighest = Color(0xFFDCEBE5),
        surfaceVariant = Color(0xFFE8F4EF),
        onSurfaceVariant = Color(0xFF45675D),
        outline = Color(0xFF78938B),
        outlineVariant = Color(0xFFD5E3DD),
        error = Color(0xFFC45136),
        onError = Color.White
    )
}
