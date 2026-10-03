package io.keystudio.core.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import io.keystudio.core.layout.PlacedKey
import io.keystudio.core.model.EffectsConfig
import io.keystudio.core.model.KeyColorEffect
import io.keystudio.core.model.KeyMotionEffect
import io.keystudio.core.model.ParticleEffect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Key motion, key colour decay and particle emission.
 *
 * Particles live in parallel float arrays rather than objects: at 120 Hz with a
 * few hundred live particles, per-particle allocation is the difference between
 * smooth and visibly stuttering on an Adreno 618.
 */
class EffectsEngine(private var density: Float) {

    private companion object {
        const val MAX_PARTICLES = 384
        const val GRAVITY = 1400f      // px/s^2 at density 1
        const val SPRING_STIFFNESS = 520f
        const val SPRING_DAMPING = 28f
    }

    // --- particle state (structure of arrays) ---------------------------------
    private val px = FloatArray(MAX_PARTICLES)
    private val py = FloatArray(MAX_PARTICLES)
    private val pvx = FloatArray(MAX_PARTICLES)
    private val pvy = FloatArray(MAX_PARTICLES)
    private val pLife = FloatArray(MAX_PARTICLES)
    private val pMaxLife = FloatArray(MAX_PARTICLES)
    private val pSize = FloatArray(MAX_PARTICLES)
    private val pRot = FloatArray(MAX_PARTICLES)
    private val pVRot = FloatArray(MAX_PARTICLES)
    private val pColor = IntArray(MAX_PARTICLES)
    private var liveCount = 0

    // --- per-key spring state -------------------------------------------------
    private var scaleVelocity = FloatArray(64)
    private var offsetVelocity = FloatArray(64)
    /** Keys currently held. Several at once: fast typing overlaps presses. */
    private var down = BooleanArray(64)
    private var downCount = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val starPath = Path()
    private val heartPath = Path()
    private val scratchPath = Path()
    private val random = Random(System.nanoTime())

    var config: EffectsConfig = EffectsConfig()

    init {
        buildStarPath()
        buildHeartPath()
    }

    fun setDensity(value: Float) {
        density = value
    }

    fun ensureCapacity(keyCount: Int) {
        if (keyCount <= scaleVelocity.size) return
        val n = Integer.highestOneBit(keyCount - 1) shl 1
        scaleVelocity = scaleVelocity.copyOf(n)
        offsetVelocity = offsetVelocity.copyOf(n)
        down = down.copyOf(n)
    }

    /** True while anything still needs redrawing. */
    val isAnimating: Boolean
        get() = liveCount > 0 || downCount > 0 || settling

    private var settling = false

    // -------------------------------------------------------------------------
    // Input events
    // -------------------------------------------------------------------------

    fun onKeyDown(keyIndex: Int, placed: PlacedKey, touchX: Float, touchY: Float) {
        ensureCapacity(keyIndex + 1)
        if (!down[keyIndex]) {
            down[keyIndex] = true
            downCount++
        }
        settling = true
        emit(touchX, touchY, placed)
    }

    fun onKeyUp(keyIndex: Int) {
        if (keyIndex in down.indices && down[keyIndex]) {
            down[keyIndex] = false
            downCount--
        }
        settling = true
    }

    fun cancel() {
        down.fill(false)
        downCount = 0
        settling = true
    }

    /** Emits particles at a point without pressing anything; used by swipe trails. */
    fun emitAt(x: Float, y: Float, placed: PlacedKey) = emit(x, y, placed)

    // -------------------------------------------------------------------------
    // Simulation
    // -------------------------------------------------------------------------

    /**
     * Advances every effect by [dt] seconds and writes the result into [state].
     * Returns true if another frame is needed.
     */
    fun update(dt: Float, state: KeyVisualState, keys: List<PlacedKey>): Boolean {
        val step = dt.coerceAtMost(0.05f)   // clamp after a stall, or springs explode
        ensureCapacity(keys.size)
        state.ensure(keys.size)

        var busy = updateKeys(step, state, keys)
        busy = updateParticles(step) || busy
        settling = busy
        return busy
    }

    private fun updateKeys(dt: Float, state: KeyVisualState, keys: List<PlacedKey>): Boolean {
        var busy = false
        val intensity = config.intensity.coerceIn(0f, 2f)
        val colorDecay = 1f - (dt * 1000f / config.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)

        for (i in keys.indices) {
            val isDown = down[i]

            // --- colour ---
            val targetPress = if (isDown && config.keyColor != KeyColorEffect.None) 1f else 0f
            val press = state.pressAmount[i]
            val newPress = if (targetPress > press) targetPress else press * colorDecay
            state.pressAmount[i] = if (newPress < 0.002f) 0f else newPress
            if (state.pressAmount[i] > 0f) busy = true

            if (config.keyColor == KeyColorEffect.Footprint) {
                if (isDown) state.afterglow[i] = 0.55f
                val glow = state.afterglow[i] * (1f - dt * 0.6f)
                state.afterglow[i] = if (glow < 0.004f) 0f else glow
                if (state.afterglow[i] > 0f) busy = true
            } else if (state.afterglow[i] != 0f) {
                state.afterglow[i] = 0f
            }

            // --- motion ---
            val targetScale = if (isDown) pressedScaleFor(config.motion, intensity) else 1f
            val targetOffset = if (isDown) pressedOffsetFor(config.motion, intensity) else 0f

            if (config.motion == KeyMotionEffect.None) {
                state.scale[i] = 1f
                state.offsetY[i] = 0f
                continue
            }

            busy = spring(i, state, targetScale, targetOffset, dt) || busy
        }
        return busy
    }

    private fun pressedScaleFor(motion: KeyMotionEffect, intensity: Float): Float = when (motion) {
        KeyMotionEffect.None -> 1f
        KeyMotionEffect.Bounce -> 1f - 0.08f * intensity
        KeyMotionEffect.Shrink -> 1f - 0.14f * intensity
        KeyMotionEffect.Pop -> 1f + 0.12f * intensity
        KeyMotionEffect.Jelly -> 1f - 0.10f * intensity
        KeyMotionEffect.Tilt -> 1f - 0.04f * intensity
        KeyMotionEffect.Travel -> 1f - 0.02f * intensity
    }

    private fun pressedOffsetFor(motion: KeyMotionEffect, intensity: Float): Float = when (motion) {
        KeyMotionEffect.Travel -> 3f * density * intensity
        KeyMotionEffect.Tilt -> 1.5f * density * intensity
        else -> 0f
    }

    /** Critically-ish damped spring. Jelly uses a looser damping to wobble. */
    private fun spring(
        i: Int,
        state: KeyVisualState,
        targetScale: Float,
        targetOffset: Float,
        dt: Float,
    ): Boolean {
        val damping = if (config.motion == KeyMotionEffect.Jelly) SPRING_DAMPING * 0.42f
        else SPRING_DAMPING

        var s = state.scale[i]
        var sv = scaleVelocity[i]
        sv += (SPRING_STIFFNESS * (targetScale - s) - damping * sv) * dt
        s += sv * dt

        var o = state.offsetY[i]
        var ov = offsetVelocity[i]
        ov += (SPRING_STIFFNESS * (targetOffset - o) - damping * ov) * dt
        o += ov * dt

        val settledScale = abs(sv) < 0.002f && abs(targetScale - s) < 0.0015f
        val settledOffset = abs(ov) < 0.02f && abs(targetOffset - o) < 0.05f
        if (settledScale) {
            s = targetScale
            sv = 0f
        }
        if (settledOffset) {
            o = targetOffset
            ov = 0f
        }

        state.scale[i] = s
        state.offsetY[i] = o
        scaleVelocity[i] = sv
        offsetVelocity[i] = ov
        return !(settledScale && settledOffset)
    }

    private fun updateParticles(dt: Float): Boolean {
        if (liveCount == 0) return false
        val gravity = GRAVITY * density
        var i = 0
        while (i < liveCount) {
            pLife[i] -= dt
            if (pLife[i] <= 0f) {
                swapRemove(i)
                continue
            }
            when (config.particle) {
                ParticleEffect.Bubble -> pvy[i] -= gravity * 0.12f * dt
                ParticleEffect.Snow -> {
                    pvy[i] += gravity * 0.04f * dt
                    pvx[i] += sin(pLife[i] * 3f) * 12f * density * dt
                }
                ParticleEffect.Neon -> { /* rings expand, no physics */ }
                else -> pvy[i] += gravity * dt
            }
            px[i] += pvx[i] * dt
            py[i] += pvy[i] * dt
            pRot[i] += pVRot[i] * dt
            i++
        }
        return liveCount > 0
    }

    private fun swapRemove(i: Int) {
        val last = liveCount - 1
        if (i != last) {
            px[i] = px[last]; py[i] = py[last]
            pvx[i] = pvx[last]; pvy[i] = pvy[last]
            pLife[i] = pLife[last]; pMaxLife[i] = pMaxLife[last]
            pSize[i] = pSize[last]; pRot[i] = pRot[last]
            pVRot[i] = pVRot[last]; pColor[i] = pColor[last]
        }
        liveCount = last
    }

    // -------------------------------------------------------------------------
    // Emission
    // -------------------------------------------------------------------------

    private fun emit(x: Float, y: Float, placed: PlacedKey) {
        val effect = config.particle
        if (effect == ParticleEffect.None) return

        val intensity = config.intensity.coerceIn(0f, 2f)
        val count = (baseCountFor(effect) * intensity).toInt().coerceIn(1, 48)

        repeat(count) {
            if (liveCount >= MAX_PARTICLES) return
            val i = liveCount++
            val color = pickColor()
            pColor[i] = color
            px[i] = x
            py[i] = y
            pRot[i] = random.nextFloat() * 6.283f
            pVRot[i] = (random.nextFloat() - 0.5f) * 8f

            when (effect) {
                ParticleEffect.Spark -> {
                    val a = random.nextFloat() * 6.283f
                    val sp = (110f + random.nextFloat() * 190f) * density
                    pvx[i] = cos(a) * sp
                    pvy[i] = sin(a) * sp - 60f * density
                    pSize[i] = (1.4f + random.nextFloat() * 1.8f) * density
                    setLife(i, 0.35f + random.nextFloat() * 0.3f)
                }
                ParticleEffect.Firework -> {
                    val a = random.nextFloat() * 6.283f
                    val sp = (180f + random.nextFloat() * 260f) * density
                    pvx[i] = cos(a) * sp
                    pvy[i] = sin(a) * sp
                    pSize[i] = (1.8f + random.nextFloat() * 2.2f) * density
                    setLife(i, 0.5f + random.nextFloat() * 0.45f)
                }
                ParticleEffect.Bubble -> {
                    pvx[i] = (random.nextFloat() - 0.5f) * 60f * density
                    pvy[i] = -(40f + random.nextFloat() * 70f) * density
                    pSize[i] = (2.5f + random.nextFloat() * 5f) * density
                    setLife(i, 0.8f + random.nextFloat() * 0.6f)
                }
                ParticleEffect.Star, ParticleEffect.Heart -> {
                    val a = -1.57f + (random.nextFloat() - 0.5f) * 1.9f
                    val sp = (140f + random.nextFloat() * 160f) * density
                    pvx[i] = cos(a) * sp
                    pvy[i] = sin(a) * sp
                    pSize[i] = (4f + random.nextFloat() * 4f) * density
                    setLife(i, 0.6f + random.nextFloat() * 0.4f)
                }
                ParticleEffect.Snow -> {
                    pvx[i] = (random.nextFloat() - 0.5f) * 40f * density
                    pvy[i] = (20f + random.nextFloat() * 40f) * density
                    pSize[i] = (1.5f + random.nextFloat() * 2.5f) * density
                    setLife(i, 1.2f + random.nextFloat() * 0.8f)
                }
                ParticleEffect.Confetti -> {
                    val a = -1.57f + (random.nextFloat() - 0.5f) * 2.4f
                    val sp = (160f + random.nextFloat() * 220f) * density
                    pvx[i] = cos(a) * sp
                    pvy[i] = sin(a) * sp
                    pSize[i] = (3f + random.nextFloat() * 3f) * density
                    pVRot[i] = (random.nextFloat() - 0.5f) * 18f
                    setLife(i, 0.7f + random.nextFloat() * 0.5f)
                }
                ParticleEffect.Trail -> {
                    pvx[i] = (random.nextFloat() - 0.5f) * 30f * density
                    pvy[i] = (random.nextFloat() - 0.5f) * 30f * density
                    pSize[i] = (2f + random.nextFloat() * 3f) * density
                    setLife(i, 0.25f + random.nextFloat() * 0.2f)
                }
                ParticleEffect.Neon -> {
                    // One expanding ring centred on the key, not a spray.
                    px[i] = placed.centerX
                    py[i] = placed.centerY
                    pvx[i] = 0f
                    pvy[i] = 0f
                    pSize[i] = minOf(placed.width, placed.height) * 0.4f
                    setLife(i, 0.45f)
                }
                ParticleEffect.None -> Unit
            }
        }
    }

    private fun setLife(i: Int, seconds: Float) {
        pLife[i] = seconds
        pMaxLife[i] = seconds
    }

    private fun baseCountFor(effect: ParticleEffect): Int = when (effect) {
        ParticleEffect.None -> 0
        ParticleEffect.Neon -> 1
        ParticleEffect.Spark -> 10
        ParticleEffect.Firework -> 16
        ParticleEffect.Bubble -> 6
        ParticleEffect.Star -> 5
        ParticleEffect.Heart -> 4
        ParticleEffect.Snow -> 5
        ParticleEffect.Confetti -> 12
        ParticleEffect.Trail -> 4
    }

    private fun pickColor(): Int {
        val palette = config.particleColors
        return if (palette.isEmpty()) config.accentColor
        else palette[random.nextInt(palette.size)]
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    fun draw(canvas: Canvas) {
        if (liveCount == 0) return
        val effect = config.particle

        paint.style = if (effect == ParticleEffect.Neon) Paint.Style.STROKE else Paint.Style.FILL

        for (i in 0 until liveCount) {
            val t = (pLife[i] / pMaxLife[i]).coerceIn(0f, 1f)
            val base = pColor[i]
            paint.color = Color.argb(
                (Color.alpha(base) * t).toInt().coerceIn(0, 255),
                Color.red(base), Color.green(base), Color.blue(base),
            )

            when (effect) {
                ParticleEffect.Neon -> {
                    paint.strokeWidth = 2.5f * density * t
                    canvas.drawCircle(px[i], py[i], pSize[i] * (1.9f - t), paint)
                }
                ParticleEffect.Confetti -> {
                    val s = pSize[i]
                    canvas.save()
                    canvas.rotate(Math.toDegrees(pRot[i].toDouble()).toFloat(), px[i], py[i])
                    canvas.drawRect(px[i] - s, py[i] - s * 0.5f, px[i] + s, py[i] + s * 0.5f, paint)
                    canvas.restore()
                }
                ParticleEffect.Star -> drawShape(canvas, starPath, i, t)
                ParticleEffect.Heart -> drawShape(canvas, heartPath, i, t)
                else -> canvas.drawCircle(px[i], py[i], pSize[i] * t, paint)
            }
        }
    }

    private fun drawShape(canvas: Canvas, unitPath: Path, i: Int, t: Float) {
        matrix.reset()
        matrix.setScale(pSize[i] * t, pSize[i] * t)
        matrix.postRotate(Math.toDegrees(pRot[i].toDouble()).toFloat())
        matrix.postTranslate(px[i], py[i])
        scratchPath.rewind()
        unitPath.transform(matrix, scratchPath)
        canvas.drawPath(scratchPath, paint)
    }

    /** Five-pointed star in unit space, centred on the origin. */
    private fun buildStarPath() {
        starPath.rewind()
        val outer = 1f
        val inner = 0.42f
        for (k in 0 until 10) {
            val r = if (k % 2 == 0) outer else inner
            val a = -1.5708 + k * 0.6283
            val x = (cos(a) * r).toFloat()
            val y = (sin(a) * r).toFloat()
            if (k == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
    }

    /** Heart in unit space, centred on the origin. */
    private fun buildHeartPath() {
        heartPath.rewind()
        heartPath.moveTo(0f, 0.75f)
        heartPath.cubicTo(-1.4f, -0.1f, -0.65f, -1.05f, 0f, -0.35f)
        heartPath.cubicTo(0.65f, -1.05f, 1.4f, -0.1f, 0f, 0.75f)
        heartPath.close()
    }
}
