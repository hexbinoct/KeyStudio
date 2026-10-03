package io.keystudio.core.layout

import io.keystudio.core.model.KeyDef
import io.keystudio.core.model.KeyRow
import io.keystudio.core.model.Metrics

/**
 * A key resolved to pixels. Mutable and pooled: the engine reuses these across
 * re-layouts so that rotating the device or resizing the keyboard allocates
 * nothing steady-state.
 */
class PlacedKey {
    lateinit var key: KeyDef
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    var rowIndex = 0
    /** Position in [LayoutEngine.keys]; also the index into per-key animation state. */
    var index = 0
    /** Position within its row, for editors that address keys by row and column. */
    var indexInRow = 0

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom

    /** Squared distance from (x,y) to this key's rect; 0 when inside. */
    fun distanceSq(x: Float, y: Float): Float {
        val dx = when {
            x < left -> left - x
            x > right -> x - right
            else -> 0f
        }
        val dy = when {
            y < top -> top - y
            y > bottom -> y - bottom
            else -> 0f
        }
        return dx * dx + dy * dy
    }
}

/**
 * Converts weight-based [KeyRow]s into pixel rectangles.
 *
 * The engine is deliberately dumb about theming: it produces geometry only, so
 * the same result feeds both the live keyboard and the Studio's preview.
 */
class LayoutEngine {

    private val pool = ArrayList<PlacedKey>(64)
    private var count = 0

    /** Placed keys from the last [compute]. Valid until the next call. */
    val keys: List<PlacedKey> get() = pool.subList(0, count)

    var contentTop = 0f
        private set
    var contentBottom = 0f
        private set

    /**
     * Lays [rows] out inside [widthPx] x [heightPx].
     *
     * @param density pixels per dp, used to convert [metrics].
     * @param originY where the key area starts, leaving room above it for the
     *   suggestion strip. [heightPx] is measured from the top of the view.
     */
    fun compute(
        rows: List<KeyRow>,
        widthPx: Float,
        heightPx: Float,
        metrics: Metrics,
        density: Float,
        originY: Float = 0f,
    ): List<PlacedKey> {
        count = 0
        if (rows.isEmpty() || widthPx <= 0f || heightPx <= 0f) return keys

        val sidePad = metrics.sidePaddingDp * density
        val topPad = metrics.topPaddingDp * density
        val bottomPad = metrics.bottomPaddingDp * density
        val gap = metrics.keyGapDp * density
        val xOffset = metrics.horizontalOffsetDp * density

        val usableWidth = widthPx * metrics.widthFraction - sidePad * 2f
        val originX = sidePad + xOffset + (widthPx - widthPx * metrics.widthFraction) * 0.5f

        contentTop = originY + topPad
        contentBottom = heightPx - bottomPad
        val usableHeight = contentBottom - contentTop

        val totalRowWeight = rows.sumOf { it.heightWeight.toDouble() }.toFloat()
        if (totalRowWeight <= 0f) return keys

        var y = contentTop
        rows.forEachIndexed { rowIndex, row ->
            val rowHeight = usableHeight * (row.heightWeight / totalRowWeight)

            // Gaps are carved out of the row width before distributing weights,
            // so a key's weight always means the same fraction of usable space.
            val interKeyGaps = (row.keys.size - 1).coerceAtLeast(0) * gap
            val edgeGaps = (row.leadingGap + row.trailingGap)
            val totalKeyWeight = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat() + edgeGaps
            if (totalKeyWeight <= 0f) {
                y += rowHeight
                return@forEachIndexed
            }
            val unitWidth = (usableWidth - interKeyGaps) / totalKeyWeight

            var x = originX + row.leadingGap * unitWidth
            for ((column, keyDef) in row.keys.withIndex()) {
                val keyWidth = unitWidth * keyDef.widthWeight
                val keyHeight = rowHeight * keyDef.heightWeight

                val placed = obtain()
                placed.key = keyDef
                placed.index = count - 1
                placed.indexInRow = column
                placed.rowIndex = rowIndex
                placed.left = x
                placed.top = y
                placed.right = x + keyWidth
                // Taller-than-row keys grow downward from the row's top edge.
                placed.bottom = y + keyHeight

                x += keyWidth + gap
            }
            y += rowHeight
        }
        return keys
    }

    private fun obtain(): PlacedKey {
        val placed = if (count < pool.size) pool[count] else PlacedKey().also { pool.add(it) }
        count++
        return placed
    }

    /**
     * Finds the key at (x,y).
     *
     * Falls back to the nearest key within [slopPx] when the touch lands in a
     * gap, which is what keeps typing accurate near key edges. Spacers are
     * never returned.
     */
    fun hitTest(x: Float, y: Float, slopPx: Float = 0f): PlacedKey? {
        var nearest: PlacedKey? = null
        var nearestDistSq = Float.MAX_VALUE
        val slopSq = slopPx * slopPx

        for (i in 0 until count) {
            val placed = pool[i]
            if (placed.key.code == io.keystudio.core.model.KeyCode.Spacer) continue
            if (placed.contains(x, y)) return placed
            val d = placed.distanceSq(x, y)
            if (d < nearestDistSq) {
                nearestDistSq = d
                nearest = placed
            }
        }
        return if (nearestDistSq <= slopSq) nearest else null
    }
}
