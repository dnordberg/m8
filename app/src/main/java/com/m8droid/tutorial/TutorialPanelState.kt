package com.m8droid.tutorial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

enum class TutorialPanelHeight(val fraction: Float) {
    COMPACT(0.18f),
    HALF(0.34f),
    EXPANDED(0.58f),
}

class TutorialPanelState(initialHeight: TutorialPanelHeight = TutorialPanelHeight.HALF) {
    var height by mutableStateOf(initialHeight)
        private set

    val heightFraction: Float
        get() = height.fraction

    fun toggleHeight() {
        height = when (height) {
            TutorialPanelHeight.COMPACT -> TutorialPanelHeight.HALF
            TutorialPanelHeight.HALF -> TutorialPanelHeight.EXPANDED
            TutorialPanelHeight.EXPANDED -> TutorialPanelHeight.COMPACT
        }
    }

    fun expand() {
        height = TutorialPanelHeight.EXPANDED
    }

    fun compact() {
        height = TutorialPanelHeight.COMPACT
    }

    fun setHeightFromFraction(fraction: Float) {
        height = TutorialPanelHeight.values().minBy { candidate -> abs(candidate.fraction - fraction) }
    }

    fun snapDragged(offsetFraction: Float) {
        setHeightFromFraction(heightFraction + offsetFraction)
    }
}
