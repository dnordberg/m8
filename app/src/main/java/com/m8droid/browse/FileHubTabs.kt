package com.m8droid.browse

object FileHubTabs {
    const val defaultLabel: String = "RECENT"
    const val newSongBannerLabel: String = "+ NEW SONG · clears current"

    val topTabLabels: List<String> = listOf(
        "RECENT",
        "OPEN DEVICE",
        "DOWNLOAD",
    )

    fun downloadSourceLabels(sourceLabels: List<String>): List<String> = sourceLabels
}
