package org.totschnig.myexpenses.fragment.preferences

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import org.totschnig.myexpenses.R

/**
 * Draws light-theme grouped Settings surfaces.
 *
 * Preference views, navigation, persistence, accessibility,
 * and selection handling remain owned by PreferenceFragmentCompat.
 */
class ArthvexaSettingsDecoration(
    context: Context
) : RecyclerView.ItemDecoration() {

    private val density =
        context.resources.displayMetrics.density

    private val inset = 12f * density
    private val radius = 16f * density
    private val borderWidth = density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(
            context,
            R.color.arthvexa_settings_card
        )
        style = Paint.Style.FILL
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(
            context,
            R.color.arthvexa_settings_border
        )
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
    }

    private fun isCategory(
        adapter: PreferenceGroupAdapter,
        position: Int
    ): Boolean {
        if (position !in 0 until adapter.itemCount) {
            return false
        }
        return adapter.getItem(position) is PreferenceCategory
    }

    private fun isFirst(
        adapter: PreferenceGroupAdapter,
        position: Int
    ): Boolean =
        position == 0 ||
            isCategory(adapter, position - 1)

    private fun isLast(
        adapter: PreferenceGroupAdapter,
        position: Int
    ): Boolean =
        position == adapter.itemCount - 1 ||
            isCategory(adapter, position + 1)

    override fun onDraw(
        canvas: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.onDraw(canvas, parent, state)

        val adapter =
            parent.adapter as? PreferenceGroupAdapter
                ?: return

        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position =
                parent.getChildAdapterPosition(child)

            if (position == RecyclerView.NO_POSITION) {
                continue
            }

            if (isCategory(adapter, position)) {
                continue
            }

            val first = isFirst(adapter, position)
            val last = isLast(adapter, position)

            val rect = RectF(
                inset,
                child.top.toFloat(),
                parent.width - inset,
                child.bottom.toFloat()
            )

            if (rect.bottom <= rect.top) continue

            val topRadius = if (first) radius else 0f
            val bottomRadius = if (last) radius else 0f

            val corners = floatArrayOf(
                topRadius, topRadius,
                topRadius, topRadius,
                bottomRadius, bottomRadius,
                bottomRadius, bottomRadius
            )

            val path = Path().apply {
                addRoundRect(
                    rect,
                    corners,
                    Path.Direction.CW
                )
            }

            canvas.drawPath(path, fill)

            // Draw horizontal borders only at actual group edges.
            if (first || last) {
                canvas.drawPath(path, stroke)
            }
        }
    }
}
