package com.nuvio.tv.core.profile

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.data.remote.supabase.SupabaseCreatedProfile
import com.nuvio.tv.data.remote.supabase.SupabaseHousehold
import com.nuvio.tv.domain.model.ServerConfiguration
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "ProfileManager"
private val edgeFunctionJsonMediaType = "application/json".toMediaType()

@Singleton
class ProfileManager @Inject constructor(
    private val profileDataStore: ProfileDataStore,
    private val factory: ProfileDataStoreFactory,
    private val credentialStores: Set<@JvmSuppressWildcards ProfileScopedCredentialStore>,
    private val postgrest: Postgrest,
    private val auth: Auth,
    private val httpClient: OkHttpClient,
    @param:Named("customServerAuth") private val customServerHttpClient: OkHttpClient,
    private val authManager: AuthManager,
    private val serverConfiguration: ServerConfiguration,
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

    // Resolved once per sign-in, before the very first sync_pull_profiles
    // call — see ProfileDataStore's own comment on why this lives there
    // (device-local, cleared only by a sign-out). "" means "not yet
    // resolved" for either; a caller must not call sync_pull_profiles
    // while householdId is still blank.
    val householdId: StateFlow<String> = profileDataStore.householdId
        .stateIn(scope, SharingStarted.Eagerly, "")

    val householdScope: StateFlow<String> = profileDataStore.householdScope
        .stateIn(scope, SharingStarted.Eagerly, "")

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

    // The household picker's own data source — every household the caller
    // belongs to, Manager or not. Callable as soon as there's a session;
    // deliberately doesn't touch householdId/householdScope itself, so a
    // caller can show a picker built from this list without committing to
    // anything until the person actually chooses.
    suspend fun pullMyHouseholds(): List<SupabaseHousehold> = withContext(Dispatchers.IO) {
        withJwtRefreshRetry {
            postgrest.rpc("pull_my_households").decodeList<SupabaseHousehold>()
        }
    }

    suspend fun selectHousehold(householdId: String) {
        profileDataStore.setHouseholdId(householdId)
    }

    // "household" or "self" — the backend re-derives and enforces the real
    // authority regardless of what's stored here (see sync_pull_profiles's
    // own comment: a non-Manager's stored choice, even if somehow set to
    // "household", is silently forced back to "self" server-side). This is
    // just what the picker remembers asking, not a security boundary in
    // its own right.
    suspend fun selectHouseholdScope(scope: String) {
        profileDataStore.setHouseholdScope(scope)
    }

    private suspend fun <T> withJwtRefreshRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (!authManager.refreshSessionIfJwtExpired(e)) throw e
            block()
        }
    }

    // Creates a real no-login household profile (create_household_profile)
    // then immediately provisions it its own Streams (AIOStreams) and
    // Catalogs (AIOMetadata) accounts — a no-login profile is meant to have
    // everything a normal profile has except a login, so it can't be left
    // waiting on someone to configure those by hand. Both provision calls
    // reuse the exact same aiostreams-sync/aiometadata-sync "provision"
    // action a self-service login profile would call for itself; both
    // already support a Manager acting on a managed profile's behalf, and
    // both auto-create a fresh sub-account with no credentials needed from
    // anyone. Best-effort: the profile row is the thing that must exist for
    // this to count as a success — a provisioning failure just leaves that
    // one addon missing until sync_pull_addons's own per-call retry (or a
    // future manual re-provision) fills it in.
    //
    // update/deleteProfile aren't wired yet (update_my_profile /
    // remove_household_member+delete_profile) — separate follow-up, not
    // needed for this profile to work day-to-day.
    suspend fun createProfile(
        name: String,
        avatarColorHex: String,
        usesPrimaryAddons: Boolean = false,
        usesPrimaryPlugins: Boolean = false,
        avatarId: String? = null
    ): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val householdId = withJwtRefreshRetry {
                postgrest.rpc("pull_my_households").decodeList<SupabaseHousehold>()
            }.firstOrNull()?.householdId

            if (householdId == null) {
                Log.e(TAG, "createProfile: caller has no household")
                return@withContext null
            }

            val created = withJwtRefreshRetry {
                postgrest.rpc(
                    "create_household_profile",
                    buildJsonObject {
                        put("p_household_id", householdId)
                        put("p_name", name)
                        put("p_avatar_color_hex", avatarColorHex)
                    }
                ).decodeSingle<SupabaseCreatedProfile>()
            }

            provisionAddonAccounts(profileId = created.id, householdId = householdId)

            UserProfile(
                id = created.id,
                name = created.name,
                avatarColorHex = created.avatarColorHex,
                isManager = false,
                order = profiles.value.size + 1
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create profile", e)
            null
        }
    }

    // Same raw-OkHttp edge-function call AuthManager already uses for
    // tv-logins-exchange — no Supabase Functions SDK plugin needed for one
    // more endpoint, this is the established pattern for it.
    private suspend fun callEdgeFunction(name: String, body: String): Boolean = withContext(Dispatchers.IO) {
        val accessToken = auth.currentSessionOrNull()?.accessToken
        if (accessToken.isNullOrBlank()) {
            Log.w(TAG, "$name: no active session")
            return@withContext false
        }
        val client = if (serverConfiguration.isCustom) customServerHttpClient else httpClient
        val url = "${serverConfiguration.backendUrl.trimEnd('/')}/functions/v1/$name"
        val request = Request.Builder()
            .url(url)
            .header("apikey", serverConfiguration.publishableKey)
            .header("Authorization", "Bearer $accessToken")
            .post(body.toRequestBody(edgeFunctionJsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "$name failed: ${response.code} ${response.body?.string()}")
            }
            response.isSuccessful
        }
    }

    private suspend fun provisionAddonAccounts(profileId: String, householdId: String) {
        try {
            withJwtRefreshRetry {
                callEdgeFunction(
                    "aiostreams-sync",
                    buildJsonObject {
                        put("action", "provision")
                        put("profile_id", profileId)
                        put("household_id", householdId)
                    }.toString()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "aiostreams-sync provision failed", e)
        }

        try {
            withJwtRefreshRetry {
                callEdgeFunction(
                    "aiometadata-sync",
                    buildJsonObject {
                        put("action", "provision")
                        put("profile_id", profileId)
                    }.toString()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "aiometadata-sync provision failed", e)
        }
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
