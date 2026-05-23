package com.m8droid.browse

object FileHubTabs {
    const val defaultLabel: String = "RECENT"
    const val newActionLabel: String = "NEW"
    const val openActionLabel: String = "OPEN"

    fun labels(
        sourceLabels: List<String>,
        includeSd: Boolean = true,
        includeProjects: Boolean = true,
    ): List<String> = buildList {
        add(defaultLabel)
        addAll(sourceLabels)
        if (includeSd) add("SD")
        if (includeProjects) add("PROJECTS")
    }
}
