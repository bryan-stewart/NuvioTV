package com.nuvio.tv.data.remote.supabase

import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class SupabasePlugin(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val url: String,
    val name: String? = null,
    val enabled: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("profile_id") val profileId: String = "",
    @SerialName("repo_type") val repoType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseAddon(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val url: String,
    val name: String? = null,
    val enabled: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("profile_id") val profileId: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SyncCodeResult(
    val code: String
)

@Serializable
data class ClaimSyncResult(
    @SerialName("result_owner_id") val ownerId: String? = null,
    val success: Boolean,
    val message: String
)

@Serializable
data class SupabaseLinkedDevice(
    val id: String? = null,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("device_user_id") val deviceUserId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("linked_at") val linkedAt: String? = null
)

@Serializable
data class TvLoginStartResult(
    val code: String,
    @SerialName("web_url") val webUrl: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int = 3
)

@Serializable
data class DeviceLoginStartResult(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int = 3,
    val legacy: Boolean = false
)

@Serializable
data class TvLoginPollResult(
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int? = null
)

@Serializable
data class TvLoginExchangeResult(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: UserInfo? = null
)

@Serializable
data class SupabaseWatchProgress(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("video_id") val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long,
    val duration: Long,
    @SerialName("last_watched") val lastWatched: Long,
    @SerialName("progress_key") val progressKey: String,
    @SerialName("profile_id") val profileId: String = ""
)

@Serializable
data class SupabaseWatchProgressEvent(
    @SerialName("event_id") val eventId: Long,
    val operation: String,
    @SerialName("progress_key") val progressKey: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("video_id") val videoId: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long,
    val duration: Long,
    @SerialName("last_watched") val lastWatched: Long
)

@Serializable
data class SupabaseWatchedItem(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("watched_at") val watchedAt: Long,
    @SerialName("profile_id") val profileId: String = ""
)

@Serializable
data class SupabaseWatchedItemEvent(
    @SerialName("event_id") val eventId: Long,
    val operation: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("watched_at") val watchedAt: Long
)

@Serializable
data class SupabaseProfile(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    // The real backend profile uuid — this is identity now, everywhere.
    @SerialName("profile_id") val profileId: String,
    // Slot-derived display order only (see memberships.slot's own
    // comment in the backend) — never used as identity for anything.
    @SerialName("profile_index") val profileIndex: Int = 0,
    @SerialName("is_manager") val isManager: Boolean = false,
    // Has its own login (a real auth.users row) vs. a no-login profile the
    // Manager created and fully controls — see sync_pull_profiles's own
    // comment on why the client needs this.
    @SerialName("is_account") val isAccount: Boolean = false,
    val name: String = "",
    @SerialName("avatar_color_hex") val avatarColorHex: String = "#1E88E5",
    @SerialName("uses_primary_addons") val usesPrimaryAddons: Boolean = false,
    @SerialName("uses_primary_plugins") val usesPrimaryPlugins: Boolean = false,
    @SerialName("avatar_id") val avatarId: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("profile_background_id") val profileBackgroundId: String? = null,
    @SerialName("profile_background_url") val profileBackgroundUrl: String? = null,
    // Read-only: set on the dashboard (by the household's Manager, or the
    // profile itself), never written from this client. Null means no
    // override — show this profile's own name instead.
    val nickname: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// pull_my_households's row shape — single-household assumption throughout
// this schema, same as sync_pull_profiles's own LIMIT 1.
@Serializable
data class SupabaseHousehold(
    @SerialName("household_id") val householdId: String,
    @SerialName("household_name") val householdName: String = "",
    @SerialName("is_manager") val isManager: Boolean = false
)

// create_household_profile returns a raw public.profiles row (many more
// columns than this) — only what ProfileManager.createProfile actually
// needs to hand back to its caller; everything else comes down again on
// the next sync_pull_profiles pull.
@Serializable
data class SupabaseCreatedProfile(
    val id: String,
    val name: String = "",
    @SerialName("avatar_color_hex") val avatarColorHex: String = "#1E88E5"
)

@Serializable
data class SupabaseProfileBackgroundCatalogItem(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("portrait_storage_path") val portraitStoragePath: String? = null,
    @SerialName("asset_version") val assetVersion: Int
)

@Serializable
data class SupabaseProfileLockState(
    @SerialName("profile_index") val profileIndex: Int,
    @SerialName("pin_enabled") val pinEnabled: Boolean = false,
    @SerialName("pin_locked_until") val pinLockedUntil: String? = null
)

@Serializable
data class SupabaseProfilePinVerifyResult(
    val unlocked: Boolean = false,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int = 0
)

@Serializable
data class SupabaseAvatarCatalogItem(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("storage_path") val storagePath: String,
    val category: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("bg_color") val bgColor: String? = null
)

@Serializable
data class SupabaseMemberAvatarCatalogItem(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("storage_path") val storagePath: String,
    val category: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("bg_color") val bgColor: String? = null,
    @SerialName("asset_version") val assetVersion: Int
)

@Serializable
data class SupabaseProfileSettingsBlob(
    @SerialName("profile_id") val profileId: String = "",
    @SerialName("settings_json") val settingsJson: JsonObject = buildJsonObject { },
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseCollectionBlob(
    @SerialName("profile_id") val profileId: String = "",
    @SerialName("collections_json") val collectionsJson: JsonElement = JsonArray(emptyList()),
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseHomeCatalogSettingsBlob(
    @SerialName("profile_id") val profileId: String = "",
    @SerialName("settings_json") val settingsJson: JsonObject = buildJsonObject { },
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseProviderCredential(
    val provider: String,
    @SerialName("credential_json") val credentialJson: JsonObject = buildJsonObject { },
    @SerialName("updated_at") val updatedAt: String? = null
)
