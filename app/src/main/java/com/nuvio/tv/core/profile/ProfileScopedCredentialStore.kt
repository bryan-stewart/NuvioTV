package com.nuvio.tv.core.profile

interface ProfileScopedCredentialStore {
    fun removeProfile(profileId: String)
    fun clearAllProfiles()
}
