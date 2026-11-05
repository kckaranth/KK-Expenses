package org.totschnig.myexpenses.compose

import androidx.compose.ui.graphics.Color

object ArthvexaIconColors {
    private val palette = listOf(
        Color(0xFFF59E0B),
        Color(0xFF3B82F6),
        Color(0xFF10B981),
        Color(0xFFA855F7),
        Color(0xFFF43F5E),
        Color(0xFF06B6D4),
        Color(0xFFEC4899),
        Color(0xFF6366F1)
    )

    fun forIcon(iconId: String): Color {
        val index = (
            iconId.hashCode().toLong()
                .and(0x7FFFFFFFL) % palette.size
        ).toInt()

        return palette[index]
    }
}
