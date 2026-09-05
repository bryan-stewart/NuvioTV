package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.nuvio.tv.domain.model.UserProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "profile_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        android.util.Log.e("ProfileDataStore", "DataStore corrupted: ${ex.message} — resetting to empty")
        emptyPreferences()
    }
)

@Singleton
class ProfileDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    private val dataStore = context.profileDataStore

    private val profilesJsonKey = stringPreferencesKey("profiles_json")
    private val activeProfileIdKey = stringPreferencesKey("active_profile_id")
    private val hasEverSelectedProfileKey = booleanPreferencesKey("profile_has_ever_selected")
    private val rememberLastProfileEnabledKey = booleanPreferencesKey("remember_last_profile_enabled")
    private val confirmExitEnabledKey = booleanPreferencesKey("confirm_exit_enabled")
    private val householdIdKey = stringPreferencesKey("household_id")
    private val householdScopeKey = stringPreferencesKey("household_scope")

    private val profileListType = Types.newParameterizedType(List::class.java, ProfileJson::class.java)

    // Real profiles come only from the backend (see ProfileSyncService.pullFromRemote) —
    // this is purely a cache of the last successful pull, never a locally-authored list.
    // Empty until the very first sync completes; that's the honest state, not a fake
    // placeholder profile.
    val profilesList: Flow<List<UserProfile>> = dataStore.data.map { prefs ->
        parseProfiles(prefs[profilesJsonKey])
    }

    // "" means "not yet resolved" (no sync has completed yet) — never a real profile id.
    val activeProfileId: Flow<String> = dataStore.data.map { prefs ->
        prefs[activeProfileIdKey] ?: ""
    }

    val hasEverSelectedProfile: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[hasEverSelectedProfileKey] ?: false
    }

    val rememberLastProfileEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[rememberLastProfileEnabledKey] ?: false
    }

    val confirmExitEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[confirmExitEnabledKey] ?: false
    }

    // Which household this device brings profiles from, and whether it
    // shows the whole household or just the signed-in profile. Resolved
    // once per sign-in (a household picker if the login belongs to more
    // than one, then — only for that household's own Manager — a whole-
    // household-vs-just-me picker) and never revisited until a sign-out
    // clears it via clearAll(): deliberately device-local, since the same
    // login answers differently on the shared living-room TV than on
    // someone's own phone. "" means "not yet resolved" for either — never
    // a real household id, and never a valid scope value.
    val householdId: Flow<String> = dataStore.data.map { prefs ->
        prefs[householdIdKey] ?: ""
    }

    val householdScope: Flow<String> = dataStore.data.map { prefs ->
        prefs[householdScopeKey] ?: ""
    }

    suspend fun setActiveProfile(id: String) {
        dataStore.edit { prefs ->
            prefs[activeProfileIdKey] = id
            prefs[hasEverSelectedProfileKey] = true
        }
    }

    suspend fun setRememberLastProfileEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[rememberLastProfileEnabledKey] = enabled
        }
    }

    suspend fun setConfirmExitEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[confirmExitEnabledKey] = enabled
        }
    }

    suspend fun setHouseholdId(id: String) {
        dataStore.edit { prefs ->
            prefs[householdIdKey] = id
        }
    }

    suspend fun setHouseholdScope(scope: String) {
        dataStore.edit { prefs ->
            prefs[householdScopeKey] = scope
        }
    }

    suspend fun replaceAllProfiles(profiles: List<UserProfile>) {
        dataStore.edit { prefs ->
            val sorted = profiles.sortedBy { it.order }
            prefs[profilesJsonKey] = serializeProfiles(sorted)
            val activeId = prefs[activeProfileIdKey] ?: ""
            if (sorted.none { it.id == activeId }) {
                // Prefer the Manager as the new default focus; fall back to
                // whoever's first (already slot-ordered) if that's ever empty.
                val fallback = sorted.firstOrNull { it.isManager } ?: sorted.firstOrNull()
                prefs[activeProfileIdKey] = fallback?.id ?: ""
            }
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun parseProfiles(json: String?): List<UserProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val adapter = moshi.adapter<List<ProfileJson>>(profileListType)
            val parsed = adapter.fromJson(json) ?: return emptyList()
            parsed.map { it.toDomain() }.sortedBy { it.order }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeProfiles(profiles: List<UserProfile>): String {
        val adapter = moshi.adapter<List<ProfileJson>>(profileListType)
        return adapter.toJson(profiles.map { ProfileJson.fromDomain(it) })
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
internal data class ProfileJson(
    val id: String,
    val name: String,
    val avatarColorHex: String,
    val isManager: Boolean = false,
    val isAccount: Boolean = false,
    val order: Int = 0,
    val usesPrimaryAddons: Boolean = false,
    val usesPrimaryPlugins: Boolean = false,
    val avatarId: String? = null,
    val avatarUrl: String? = null,
    val profileBackgroundId: String? = null,
    val profileBackgroundUrl: String? = null
) {
    fun toDomain() = UserProfile(
        id = id,
        name = name,
        avatarColorHex = avatarColorHex,
        isManager = isManager,
        isAccount = isAccount,
        order = order,
        usesPrimaryAddons = usesPrimaryAddons,
        usesPrimaryPlugins = usesPrimaryPlugins,
        avatarId = avatarId,
        avatarUrl = avatarUrl,
        profileBackgroundId = profileBackgroundId,
        profileBackgroundUrl = profileBackgroundUrl
    )

    companion object {
        fun fromDomain(profile: UserProfile) = ProfileJson(
            id = profile.id,
            name = profile.name,
            avatarColorHex = profile.avatarColorHex,
            isManager = profile.isManager,
            isAccount = profile.isAccount,
            order = profile.order,
            usesPrimaryAddons = profile.usesPrimaryAddons,
            usesPrimaryPlugins = profile.usesPrimaryPlugins,
            avatarId = profile.avatarId,
            avatarUrl = profile.avatarUrl,
            profileBackgroundId = profile.profileBackgroundId,
            profileBackgroundUrl = profile.profileBackgroundUrl
        )
    }
}
