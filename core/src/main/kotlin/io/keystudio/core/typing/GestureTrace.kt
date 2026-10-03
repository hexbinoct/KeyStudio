package io.keystudio.core.typing

/**
 * Where a letter sits on screen, captured when a swipe ends. The decoder works
 * from the *actual* key rectangles, so a keyboard the user has resized or
 * rearranged still swipes correctly.
 */
class KeyAnchor(
    val char: Char,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/** One swipe: the finger's path in pixels, and the letters it was drawn over. */
class GestureTrace(
    val xs: FloatArray,
    val ys: FloatArray,
    val times: LongArray,
    val size: Int,
    val anchors: List<KeyAnchor>,
) {
    /** Typical key width, the natural unit for every distance in decoding. */
    val keyWidth: Float = anchors.map { it.width }.sorted().let { if (it.isEmpty()) 1f else it[it.size / 2] }
}
