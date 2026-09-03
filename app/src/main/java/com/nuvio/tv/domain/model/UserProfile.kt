package com.nuvio.tv.domain.model

data class UserProfile(
    val id: String,
    val name: String,
    val avatarColorHex: String,
    val isManager: Boolean = false,
    val order: Int = 0,
    val usesPrimaryAddons: Boolean = false,
    val usesPrimaryPlugins: Boolean = false,
    val avatarId: String? = null,
    val avatarUrl: String? = null,
    val profileBackgroundId: String? = null,
    val profileBackgroundUrl: String? = null
) {
    // Renamed from isPrimary: real profiles have no notion of "id 1" —
    // the Manager is whoever the backend says is the Manager, never
    // inferred from position.
    val isPrimary: Boolean get() = isManager
}
