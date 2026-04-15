package com.m8droid.browse

/**
 * A browsable source of downloadable M8 content (songs, instruments,
 * themes, samples, packs). Implementations fetch a list of items from
 * a remote index and hand back fully-populated RemoteItems.
 */
interface ContentSource {
    val displayName: String
    val description: String

    /**
     * Fetch a page of items. Implementations may ignore the query string
     * if their backend doesn't support search. Pagination is best-effort.
     */
    suspend fun fetchItems(query: String = "", page: Int = 1): List<RemoteItem>
}
