package com.m8droid.browse

import com.m8droid.ui.ModalStyle

object FileHubTabs {
    const val defaultLabel: String = "RECENT"
    const val newSongBannerLabel: String = "+ NEW SONG · clears current"

    val topTabLabels: List<String> = listOf(
        "RECENT",
        "OPEN DEVICE",
        "DOWNLOAD",
    )

    fun downloadSourceLabels(sourceLabels: List<String>): List<String> = sourceLabels

    fun compactDownloadSourceLabels(sourceLabels: List<String>): List<String> =
        sourceLabels.map { label ->
            when (label) {
                "Archive.org" -> "Archive"
                else -> label
            }
        }
}

object FileHubLayout {
    const val dialogWidthFraction: Float = ModalStyle.panelWidthFraction
    const val dialogHeightFraction: Float = 0.88f
    const val edgePaddingDp: Int = 0
    const val panelAlignment: String = ModalStyle.panelAlignment
    const val downloadSourcesFillRow: Boolean = true
    const val detailScrollIndicatorWidthDp: Int = 3
}
