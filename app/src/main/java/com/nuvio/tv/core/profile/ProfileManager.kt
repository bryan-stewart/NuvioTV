package com.nuvio.tv.core.profile

import android.content.Context
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileManager @Inject constructor(
    private val profileDataStore: ProfileDataStore,
    private val factory: ProfileDataStoreFactory,
    private val credentialStores: Set<@JvmSuppressWildcards ProfileScopedCredentialStore>,
    @ApplicationContext private val context: Context
) {
    companion object {
        // Soft UI cap only now — the backend doesn't enforce a household
        // size limit, this is just how many tiles the profile-selection
        // screen was designed around.
        const val MAX_PROFILES = 6
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Real household members only, sourced from the backend (see
    // ProfileSyncService.pullFromRemote) — never locally authored. ""
    // means "not yet resolved" (no sync has completed since sign-in),
    // never a real profile.
    val activeProfileId: StateFlow<String> = profileDataStore.activeProfileId
        .stateIn(scope, SharingStarted.Eagerly, "")

    val activeProfileReady: StateFlow<Boolean> = profileDataStore.activeProfileId
        .map { it.isNotBlank() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val hasEverSelectedProfile: StateFlow<Boolean> = profileDataStore.hasEverSelectedProfile
        .stateIn(scope, SharingStarted.Eagerly, false)

    val rememberLastProfileEnabled: StateFlow<Boolean> = profileDataStore.rememberLastProfileEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    val confirmExitEnabled: StateFlow<Boolean> = profileDataStore.confirmExitEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    val profiles: StateFlow<List<UserProfile>> = profileDataStore.profilesList
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeProfile: UserProfile?
        get() = profiles.value.find { it.id == activeProfileId.value }

    // Was "id == 1" — a real household has no notion of a numbered
    // primary slot, so this now reflects who the backend says manages
    // the household.
    val isPrimaryProfileActive: Boolean
        get() = activeProfile?.isManager == true

    val canCreateProfile: Boolean
        get() = profiles.value.size < MAX_PROFILES

    suspend fun setActiveProfile(id: String) {
        val exists = profiles.value.any { it.id == id }
        if (exists) {
            profileDataStore.setActiveProfile(id)
        }
    }

    suspend fun setRememberLastProfileEnabled(enabled: Boolean) {
        profileDataStore.setRememberLastProfileEnabled(enabled)
    }

    suspend fun setConfirmExitEnabled(enabled: Boolean) {
        profileDataStore.setConfirmExitEnabled(enabled)
    }

    // Profile creation/update/delete now need a real round-trip to the
    // backend (create_household_profile / update_my_profile /
    // remove_household_member+delete_profile) instead of just mutating a
    // locally-authored list — not wired yet (see the phased plan). Stubbed
    // as clean no-ops for now rather than silently touching local state
    // the next sync would just overwrite anyway; callers already handle a
    // null/false result as a failure.
    suspend fun createProfile(
        name: String,
        avatarColorHex: String,
        usesPrimaryAddons: Boolean = false,
        usesPrimaryPlugins: Boolean = false,
        avatarId: String? = null
    ): UserProfile? {
        return null
    }

    suspend fun deleteProfile(id: String): Boolean {
        return false
    }

    suspend fun updateProfile(profile: UserProfile): Boolean {
        return false
    }

    // Unused until deleteProfile is wired for real (step 3) — kept ready
    // rather than rewritten from scratch later. Clears this profile's own
    // local cache files; never the caller's own data.
    private suspend fun deleteProfileDataAsync(profileId: String) = withContext(Dispatchers.IO) {
        if (profileId.isBlank()) return@withContext

        factory.clearProfile(profileId)
        credentialStores.forEach { store -> store.removeProfile(profileId) }

        val suffixWithExtension = "_p${profileId}.preferences_pb"
        val dataStoreDir = File(context.filesDir, "datastore")
        if (dataStoreDir.exists()) {
            dataStoreDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(suffixWithExtension)) {
                    file.delete()
                }
            }
        }

        val pluginCodeDir = File(context.filesDir, "plugin_code_p${profileId}")
        if (pluginCodeDir.exists()) {
            pluginCodeDir.deleteRecursively()
        }
    }
}
