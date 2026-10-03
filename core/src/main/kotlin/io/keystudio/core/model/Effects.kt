package io.keystudio.core.model

import kotlinx.serialization.Serializable

/**
 * How the pressed key's own colour reacts. Keys Cafe calls this the
 * "key color effect".
 */
@Serializable
enum class KeyColorEffect {
    None,
    /** Circular wave expanding from the touch point, clipped to the key. */
    Ripple,
    /** Accent-coloured bloom around the key's edge. */
    Glow,
    /** Instant accent fill that decays back to the base colour. */
    Flash,
    /** Accent outline that fades out. */
    Outline,
    /** Fills from the bottom up, like a level meter. */
    Fill,
    /** Leaves a fading tint behind on recently pressed keys. */
    Footprint,
}

/**
 * What the key does physically. Keys Cafe calls this the "key motion effect".
 */
@Serializable
enum class KeyMotionEffect {
    None,
    /** Scales down on press and springs back. */
    Bounce,
    /** Tilts towards the touch point in fake 3D. */
    Tilt,
    /** Shrinks and stays small while held. */
    Shrink,
    /** Overshoots larger, then settles. */
    Pop,
    /** Squash-and-stretch wobble. */
    Jelly,
    /** Slides down slightly, like a real keycap travelling. */
    Travel,
}

/**
 * Particles emitted over the whole keyboard surface. Keys Cafe calls this the
 * "keyboard color effect".
 */
@Serializable
enum class ParticleEffect {
    None,
    /** Small fast sparks in the accent colour. */
    Spark,
    /** Radial burst that arcs and falls under gravity. */
    Firework,
    /** Slow rising circles. */
    Bubble,
    /** Star glyphs that spin as they fall. */
    Star,
    /** Soft drifting flakes. */
    Snow,
    /** Rectangular confetti with rotation. */
    Confetti,
    /** A short comet trail following the finger. */
    Trail,
    /** Expanding neon ring. */
    Neon,
    /** Tiny hearts. */
    Heart,
}

@Serializable
enum class SoundProfile {
    None, System, Typewriter, Mechanical, Soft, Bubble, Click,
}

@Serializable
data class SoundConfig(
    val profile: SoundProfile = SoundProfile.System,
    /** 0f..1f */
    val volume: Float = 0.4f,
    val hapticsEnabled: Boolean = true,
    /** Milliseconds; 0 disables. */
    val hapticDurationMs: Int = 12,
)

@Serializable
data class EffectsConfig(
    val keyColor: KeyColorEffect = KeyColorEffect.Ripple,
    val motion: KeyMotionEffect = KeyMotionEffect.Bounce,
    val particle: ParticleEffect = ParticleEffect.None,
    /** Drives every effect that isn't given its own colour. */
    val accentColor: Int = 0xFF7C4DFF.toInt(),
    /** Overrides [accentColor] for particles when set. */
    val particleColors: List<Int> = emptyList(),
    /** Global multiplier on particle count and motion amplitude. */
    val intensity: Float = 1f,
    /** Milliseconds an effect takes to decay. */
    val durationMs: Int = 420,
) {
    /** Whether anything here needs a per-frame callback while idle. */
    val needsContinuousFrames: Boolean
        get() = particle != ParticleEffect.None || keyColor == KeyColorEffect.Footprint
}
