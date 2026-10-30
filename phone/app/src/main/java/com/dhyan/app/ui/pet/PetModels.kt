package com.dhyan.app.ui.pet

import androidx.compose.ui.graphics.Color

enum class PetEmotion {
    CURIOUS, HAPPY, LOVE, MEDITATING, SLEEPY, SURPRISED, DIZZY, PROUD, FOCUSED, POUTING
}

enum class BreathingPhase { INHALE, HOLD, EXHALE, NONE }

/** Dimensionless animation targets. Slant/curve signs select expression direction. */
data class PetFacialParams(
    val eyeOpen: Float,
    val eyeSlant: Float,
    val pupilScale: Float,
    val mouthCurve: Float,
    val blushAlpha: Float,
    val primaryColor: Color,
    val auraIntensity: Float
)

/** Pure targets, independent of composition; the renderer interpolates between them. */
fun PetEmotion.toFacialParams(isBlinking: Boolean): PetFacialParams {
    val params = when (this) {
        PetEmotion.CURIOUS -> PetFacialParams(1f, 0f, 1f, .2f, .12f, Color(0xFF7CE89E), .25f)
        PetEmotion.HAPPY -> PetFacialParams(.15f, -.65f, .9f, .85f, .55f, Color(0xFFFDE047), .5f)
        PetEmotion.LOVE -> PetFacialParams(.12f, -.45f, .85f, .7f, .85f, Color(0xFFF6CCD9), .6f)
        PetEmotion.MEDITATING -> PetFacialParams(.1f, .5f, .8f, .12f, .2f, Color(0xFFCBC6F7), .75f)
        PetEmotion.SLEEPY -> PetFacialParams(.3f, .12f, .85f, .05f, .1f, Color(0xFF7CC8F8), .15f)
        PetEmotion.SURPRISED -> PetFacialParams(1.35f, 0f, .6f, 0f, .22f, Color(0xFFFFDCC3), .65f)
        PetEmotion.DIZZY -> PetFacialParams(.9f, .25f, .85f, -.15f, .35f, Color(0xFFE2CEF1), .45f)
        PetEmotion.PROUD -> PetFacialParams(.65f, -.25f, .95f, .65f, .4f, Color(0xFFFFE7AA), .7f)
        PetEmotion.FOCUSED -> PetFacialParams(.78f, -.15f, .85f, .08f, .08f, Color(0xFF80D8C4), .2f)
        PetEmotion.POUTING -> PetFacialParams(.7f, .35f, .95f, -.6f, .3f, Color(0xFFE8D1CF), .12f)
    }
    return if (isBlinking) params.copy(eyeOpen = 0f) else params
}

/** Transient, app-authored speech; never a container for notification content. */
data class PetSpeechBubble(val text: String, val durationMs: Long = 2500L)
