package org.totschnig.myexpenses.util

/** Presentation-only category accents shared by Compose and Android Views. */
fun arthvexaCategoryAccentArgb(categoryPath: String?): Int {
    val category = categoryPath.orEmpty().substringBefore(" > ").trim().lowercase()
    val rgb = when {
        category.contains("hous") || category.contains("rent") -> 0x00A99A
        category.contains("food") || category.contains("grocer") -> 0xE58A16
        category.contains("transport") || category.contains("travel") -> 0x238BCB
        category.contains("saving") || category.contains("invest") -> 0x8B65D6
        category.contains("loan") || category.contains("mortgage") -> 0xD16A55
        category.contains("income") || category.contains("salary") -> 0x148C65
        category.contains("health") || category.contains("medical") -> 0xD05A8A
        else -> 0x537C94
    }
    return (0xFF000000.toInt() or rgb)
}
