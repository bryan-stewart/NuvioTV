package com.nuvio.tv.domain.model

data class UserProfile(
    val id: String,
    val name: String,
    val avatarColorHex: String,
    val isManager: Boolean = false,
    // Has its own login vs. a no-login profile the Manager fully controls —
    // see sync_pull_profiles's own comment on why. Determines which actions
    // belong in this profile's own Profile Options menu.
    val isAccount: Boolean = false,
    val order: Int = 0,
    val usesPrimaryAddons: Boolean = false,
    val usesPrimaryPlugins: Boolean = false,
    val avatarId: String? = null,
    val avatarUrl: String? = null,
    val profileBackgroundId: String? = null,
    val profileBackgroundUrl: String? = null,
    // Set on the dashboard only (Manager, or the profile itself) — this
    // client never writes it. Null means no override; show name instead.
    val nickname: String? = null
) {
    // Renamed from isPrimary: real profiles have no notion of "id 1" —
    // the Manager is whoever the backend says is the Manager, never
    // inferred from position.
    val isPrimary: Boolean get() = isManager

    // What to actually show wherever this profile's name would otherwise
    // appear — the dashboard's own "nickname or name" convention.
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: name
}
