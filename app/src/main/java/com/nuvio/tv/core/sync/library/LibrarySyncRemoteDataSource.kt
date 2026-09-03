package com.nuvio.tv.core.sync.library

import com.nuvio.tv.domain.model.LibraryDeltaEvent
import com.nuvio.tv.domain.model.LibrarySyncKey
import com.nuvio.tv.domain.model.SavedLibraryItem

interface LibrarySyncRemoteDataSource {
    suspend fun pullSnapshot(
        profileId: String,
        pageSize: Int
    ): List<SavedLibraryItem>

    suspend fun getDeltaCursor(profileId: String): Long

    suspend fun pullDelta(
        profileId: String,
        sinceEventId: Long,
        limit: Int
    ): List<LibraryDeltaEvent>

    suspend fun pushItems(
        profileId: String,
        items: Collection<SavedLibraryItem>
    )

    suspend fun deleteItems(
        profileId: String,
        keys: Collection<LibrarySyncKey>
    )
}
