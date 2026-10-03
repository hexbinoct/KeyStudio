package io.keystudio.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import io.keystudio.core.model.AnimatedBackground
import io.keystudio.core.model.BackgroundSpec
import io.keystudio.core.model.ColorSpec
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the keyboard backdrop.
 *
 * Animated backdrops are rendered into a small offscreen bitmap (an eighth of
 * the keyboard's size) and upscaled with bilinear filtering. The upscale *is*
 * the blur, which is what makes a soft moving gradient essentially free on a
 * mid-range GPU -- a full-resolution blur would not hold 120 Hz here.
 */
class BackgroundRenderer(private var density: Float) {

    private companion object {
        const val DOWNSCALE = 8
        const val BLOB_COUNT = 4
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val srcRect = Rect()
    private val dstRect = RectF()
    private val shaderMatrix = Matrix()

    private var lowResBitmap: Bitmap? = null
    private var lowResCanvas: Canvas? = null

    private var userImage: Bitmap? = null
    private var userImageUri: String? = null
    private var userImageBlur = 0f
    /** The decoded file, kept so a blur change does not mean decoding again. */
    private var userSource: Bitmap? = null

    private var staticShader: Shader? = null
    private var staticColor: Int = Color.DKGRAY

    /** Unit-space shaders for animated backdrops, positioned per frame. */
    private var blobShaders: Array<RadialGradient>? = null
    private var driftShader: LinearGradient? = null
    private val blobColors = IntArray(2)

    private var width = 0f
    private var height = 0f
    private var spec: BackgroundSpec? = null
    private var elapsed = 0f

    fun setDensity(value: Float) {
        density = value
    }

    /** True when this backdrop needs a frame callback even with no input. */
    val isAnimated: Boolean
        get() = spec is BackgroundSpec.Animated

    fun prepare(spec: BackgroundSpec, widthPx: Float, heightPx: Float) {
        val sizeChanged = widthPx != width || heightPx != height
        this.spec = spec
        width = widthPx
        height = heightPx
        if (widthPx <= 0f || heightPx <= 0f) return

        when (spec) {
            is BackgroundSpec.Fill -> {
                staticShader = buildShader(spec.color, widthPx, heightPx)
                staticColor = flatColor(spec.color)
                releaseLowRes()
            }
            is BackgroundSpec.Image -> {
                if (userImageUri != spec.uri || userImageBlur != spec.blurRadius) {
                    loadUserImage(spec.uri, spec.blurRadius)
                }
                staticColor = Color.BLACK
                releaseLowRes()
            }
            is BackgroundSpec.Animated -> {
                staticColor = spec.colors.firstOrNull() ?: Color.DKGRAY
                if (sizeChanged || lowResBitmap == null) allocateLowRes(widthPx, heightPx)
                buildAnimatedShaders(spec)
            }
        }
    }

    /**
     * Builds every shader an animated backdrop needs, once. Blob gradients are
     * unit-radius and centred on the origin; [drawAnimated] positions them with
     * a reused matrix so the frame loop allocates nothing.
     */
    private fun buildAnimatedShaders(spec: BackgroundSpec.Animated) {
        val colors = spec.colors.ifEmpty { listOf(staticColor) }
        blobShaders = Array(BLOB_COUNT) { i ->
            val color = colors[(i + 1) % colors.size]
            blobColors[0] = withAlpha(color, 210)
            blobColors[1] = withAlpha(color, 0)
            RadialGradient(0f, 0f, 1f, blobColors.copyOf(), null, Shader.TileMode.CLAMP)
        }
        driftShader = LinearGradient(
            0f, 0f, 1f, 1f, colors.toIntArray(), null, Shader.TileMode.MIRROR,
        )
    }

    private fun allocateLowRes(widthPx: Float, heightPx: Float) {
        releaseLowRes()
        val w = (widthPx / DOWNSCALE).toInt().coerceAtLeast(2)
        val h = (heightPx / DOWNSCALE).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        lowResBitmap = bmp
        lowResCanvas = Canvas(bmp)
        srcRect.set(0, 0, w, h)
    }

    private fun releaseLowRes() {
        lowResBitmap?.recycle()
        lowResBitmap = null
        lowResCanvas = null
    }

    /**
     * The decoded file is kept apart from the bitmap actually drawn, because
     * dragging the blur slider re-runs this on every frame and decoding a photo
     * sixty times a second would jank the preview. Only the (cheap) rescale
     * repeats; the decode happens once per image.
     */
    private fun loadUserImage(uri: String, blurRadius: Float) {
        if (userImageUri != uri) {
            userSource?.recycle()
            userSource = runCatching { BitmapFactory.decodeFile(uri) }.getOrNull()
            userImageUri = uri
        }
        val source = userSource
        if (userImage !== source) userImage?.recycle()
        userImage = when {
            source == null -> null
            blurRadius > 0.5f -> blur(source, blurRadius)
            else -> source
        }
        userImageBlur = blurRadius
    }

    /**
     * Blur by downscaling and letting bilinear filtering do the smoothing on the
     * way back up -- the same trick the animated backdrops use, and the reason
     * they are affordable.
     *
     * A real Gaussian would mean `RenderEffect` (API 33, so not this phone) or
     * RenderScript (removed). At the radii a keyboard backdrop wants, which are
     * large and soft, the difference is not visible; the cost is one scaled copy
     * at load rather than work on every frame.
     */
    private fun blur(source: Bitmap, radius: Float): Bitmap {
        val factor = (1f + radius / 2f).coerceIn(1f, 24f)
        val w = (source.width / factor).toInt().coerceAtLeast(1)
        val h = (source.height / factor).toInt().coerceAtLeast(1)
        // Never recycles the source: it is the cached original and is reused for
        // every later radius.
        return runCatching { Bitmap.createScaledBitmap(source, w, h, true) }.getOrNull() ?: source
    }

    private fun buildShader(spec: ColorSpec, w: Float, h: Float): Shader? = when (spec) {
        is ColorSpec.Solid -> null
        is ColorSpec.LinearGradient -> {
            val rad = Math.toRadians(spec.angleDeg.toDouble())
            val dx = cos(rad).toFloat()
            val dy = sin(rad).toFloat()
            LinearGradient(
                w * (0.5f - dx * 0.5f), h * (0.5f - dy * 0.5f),
                w * (0.5f + dx * 0.5f), h * (0.5f + dy * 0.5f),
                spec.colors.toIntArray(), spec.stops?.toFloatArray(), Shader.TileMode.CLAMP,
            )
        }
        is ColorSpec.RadialGradient -> RadialGradient(
            w * spec.centerX, h * spec.centerY,
            maxOf(w, h) * spec.radiusScale.coerceAtLeast(0.01f),
            spec.colors.toIntArray(), null, Shader.TileMode.CLAMP,
        )
    }

    private fun flatColor(spec: ColorSpec): Int = when (spec) {
        is ColorSpec.Solid -> spec.color
        is ColorSpec.LinearGradient -> spec.colors.firstOrNull() ?: Color.DKGRAY
        is ColorSpec.RadialGradient -> spec.colors.firstOrNull() ?: Color.DKGRAY
    }

    /** Advances animated backdrops. No-op for static ones. */
    fun update(dt: Float) {
        if (isAnimated) elapsed += dt
    }

    fun draw(canvas: Canvas) {
        val spec = this.spec ?: return
        dstRect.set(0f, 0f, width, height)

        when (spec) {
            is BackgroundSpec.Fill -> {
                paint.shader = staticShader
                paint.color = staticColor
                canvas.drawRect(dstRect, paint)
                paint.shader = null
            }
            is BackgroundSpec.Image -> drawImage(canvas, spec)
            is BackgroundSpec.Animated -> drawAnimated(canvas, spec)
        }
    }

    private fun drawImage(canvas: Canvas, spec: BackgroundSpec.Image) {
        val bmp = userImage
        if (bmp == null || bmp.isRecycled) {
            paint.shader = null
            paint.color = staticColor
            canvas.drawRect(dstRect, paint)
            return
        }
        // Centre-crop so the image fills the keyboard without distortion.
        val scale = maxOf(width / bmp.width, height / bmp.height)
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        dstRect.set(
            (width - drawW) * 0.5f, (height - drawH) * 0.5f,
            (width + drawW) * 0.5f, (height + drawH) * 0.5f,
        )
        srcRect.set(0, 0, bmp.width, bmp.height)
        paint.shader = null
        paint.color = Color.WHITE
        // A centre-crop is deliberately bigger than the keyboard on one axis, so
        // it has to be clipped here rather than relying on the host to do it:
        // the Studio embeds this view in Compose, which does not clip by
        // default, and the overflow paints over the editor above the keyboard.
        val saved = canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        canvas.restoreToCount(saved)

        if (spec.scrim > 0f) {
            paint.color = Color.argb((spec.scrim * 255).toInt().coerceIn(0, 255), 0, 0, 0)
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }

    private fun drawAnimated(canvas: Canvas, spec: BackgroundSpec.Animated) {
        val bmp = lowResBitmap
        val lowCanvas = lowResCanvas
        if (bmp == null || lowCanvas == null || bmp.isRecycled) return

        val t = elapsed * spec.speed
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val colors = spec.colors.ifEmpty { listOf(staticColor) }

        lowCanvas.drawColor(colors.first(), android.graphics.PorterDuff.Mode.SRC)

        when (spec.kind) {
            AnimatedBackground.Aurora, AnimatedBackground.Mesh -> {
                // A few large radial blobs on slow Lissajous paths. Upscaling
                // blends them into a continuous field.
                //
                // The shaders are built once in prepare() at unit radius and
                // moved with a reused matrix. Rebuilding them here allocated
                // four gradients and four int arrays *per frame*, which showed
                // up as "Slow UI thread" jank on an Adreno 618.
                val shaders = blobShaders ?: return
                for (i in 0 until BLOB_COUNT) {
                    val phase = i * 1.7f
                    val cx = w * (0.5f + 0.42f * cos(t * (0.23f + i * 0.07f) + phase))
                    val cy = h * (0.5f + 0.38f * sin(t * (0.31f + i * 0.05f) + phase * 1.3f))
                    val radius = (maxOf(w, h) * (0.55f + 0.12f * sin(t * 0.4f + phase)))
                        .coerceAtLeast(1f)

                    shaderMatrix.reset()
                    shaderMatrix.setScale(radius, radius)
                    shaderMatrix.postTranslate(cx, cy)
                    shaders[i].setLocalMatrix(shaderMatrix)

                    blobPaint.shader = shaders[i]
                    lowCanvas.drawCircle(cx, cy, radius, blobPaint)
                }
                blobPaint.shader = null
            }
            AnimatedBackground.Drift -> {
                val shader = driftShader ?: return
                shaderMatrix.reset()
                // Unit shader -> full size, then spin about the centre.
                shaderMatrix.setScale(w, h)
                shaderMatrix.postRotate(t * 18f, w * 0.5f, h * 0.5f)
                shader.setLocalMatrix(shaderMatrix)
                blobPaint.shader = shader
                lowCanvas.drawRect(0f, 0f, w, h, blobPaint)
                blobPaint.shader = null
            }
            AnimatedBackground.Bubbles, AnimatedBackground.Snow -> {
                // Ambient drifting dots; the colour field stays flat behind them.
                val rising = spec.kind == AnimatedBackground.Bubbles
                blobPaint.shader = null
                for (i in 0 until 14) {
                    val seed = i * 0.618f
                    val speed = 0.12f + (i % 5) * 0.035f
                    val progress = ((t * speed + seed) % 1f)
                    val y = if (rising) h * (1f - progress) else h * progress
                    val x = w * (((seed * 7.3f) % 1f) + 0.08f * sin(t * 0.7f + seed * 6f))
                    val r = (1.2f + (i % 4) * 0.9f)
                    val alpha = (160 * kotlin.math.sin(progress * Math.PI).toFloat()).toInt()
                    blobPaint.color = withAlpha(colors[(i + 1) % colors.size], alpha)
                    lowCanvas.drawCircle(x % w, y, r, blobPaint)
                }
            }
        }

        srcRect.set(0, 0, bmp.width, bmp.height)
        dstRect.set(0f, 0f, width, height)
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 255
        canvas.drawBitmap(bmp, srcRect, dstRect, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color),
    )

    fun release() {
        releaseLowRes()
        // userImage is either the source itself or a scaled copy of it, so
        // recycle the copy only when it is actually a separate bitmap.
        if (userImage !== userSource) userImage?.recycle()
        userSource?.recycle()
        userImage = null
        userSource = null
        userImageUri = null
        userImageBlur = 0f
    }
}
