package com.nuvio.tv.data.simkl

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface SimklSyncStorage {
    suspend fun load(profileId: String): String?
    suspend fun save(profileId: String, payload: String)
    suspend fun remove(profileId: String)
}

@Singleton
class AndroidSimklSyncStorage @Inject constructor(
    private val factory: ProfileDataStoreFactory
) : SimklSyncStorage {
    override suspend fun load(profileId: String): String? =
        factory.get(profileId, FEATURE_NAME).data.first()[SNAPSHOT_KEY]

    override suspend fun save(profileId: String, payload: String) {
        factory.get(profileId, FEATURE_NAME).edit { preferences ->
            preferences[SNAPSHOT_KEY] = payload
        }
    }

    override suspend fun remove(profileId: String) {
        factory.get(profileId, FEATURE_NAME).edit { preferences ->
            preferences.remove(SNAPSHOT_KEY)
        }
    }

    private companion object {
        const val FEATURE_NAME = "simkl_sync"
        val SNAPSHOT_KEY = stringPreferencesKey("snapshot")
    }
}
