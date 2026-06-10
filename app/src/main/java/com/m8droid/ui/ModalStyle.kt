package com.m8droid.ui

/**
 * Shared overlay/modal geometry. The Help menu screenshot is the visual base:
 * centered panel, 88% phone width, 12dp internal padding, 8dp card corners,
 * dim purple container borders, and the same dark scrim.
 */
object ModalStyle {
    const val panelWidthFraction: Float = 0.88f
    const val panelPaddingDp: Int = 12
    const val cardCornerDp: Int = 8
    const val scrimAlpha: Float = 0.80f
    const val panelAlignment: String = "Center"
}
