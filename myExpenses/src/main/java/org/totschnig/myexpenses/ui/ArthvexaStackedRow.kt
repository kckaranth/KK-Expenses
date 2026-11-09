package org.totschnig.myexpenses.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TableRow

/**
 * Vertical presentation for editor rows while retaining their TableRow identity
 * and direct children. In particular, do not apply this to AccountRow,
 * TransferAccountRow, AmountRow or TransferAmountRow: TransferDelegate moves
 * those views between their existing parents.
 */
class ArthvexaStackedRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TableRow(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
            .coerceAtLeast(0)
        var totalHeight = paddingTop + paddingBottom
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as ViewGroup.MarginLayoutParams
            val childHeight = getChildMeasureSpec(
                heightMeasureSpec,
                paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
                lp.height
            )
            child.measure(
                View.MeasureSpec.makeMeasureSpec(
                    (available - lp.leftMargin - lp.rightMargin).coerceAtLeast(0),
                    View.MeasureSpec.EXACTLY
                ),
                childHeight
            )
            totalHeight += child.measuredHeight + lp.topMargin + lp.bottomMargin
        }
        val resolvedWidth = View.resolveSize(
            View.MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec
        )
        setMeasuredDimension(resolvedWidth, View.resolveSize(totalHeight, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var y = paddingTop
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as ViewGroup.MarginLayoutParams
            y += lp.topMargin
            val x = paddingLeft + lp.leftMargin
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            y += child.measuredHeight + lp.bottomMargin
        }
    }
}
