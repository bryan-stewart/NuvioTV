package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.ProfileSyncService
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val profileManager: ProfileManager,
    private val profileSyncService: ProfileSyncService
) : ViewModel() {

    val profiles: StateFlow<List<UserProfile>> = profileManager.profiles

    // Gates whether Settings shows the Account/Profiles sections at all —
    // was "activeProfileId == 1" back when a local install always had a
    // numbered primary slot. A real household has no such slot, so this
    // reflects whoever the backend says manages the household — except
    // when this device only ever has one profile to begin with (a
    // non-Manager is always resolved to a single "self" profile; see
    // sync_pull_profiles's own comment), in which case there's no other
    // profile whose access this could leak into, so it's shown regardless
    // of manager status. Otherwise a non-Manager member profile — reachable
    // by switching profiles on a Manager's whole-household device — would
    // correctly stay locked out of Account/Profiles, but so would a
    // non-Manager's own single-profile device, with no way back to Sign Out.
    val isPrimaryProfileActive: StateFlow<Boolean> = combine(
        profileManager.activeProfileId,
        profileManager.profiles
    ) { activeId, profiles ->
        profiles.size <= 1 || profiles.firstOrNull { it.id == activeId }?.isManager == true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val canAddProfile: Boolean
        get() = profileManager.canCreateProfile

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    fun createProfile(
        name: String,
        avatarColorHex: String,
        usesPrimaryAddons: Boolean,
        usesPrimaryPlugins: Boolean,
        avatarId: String? = null
    ) {
        if (_isCreating.value) return
        viewModelScope.launch {
            _isCreating.value = true
            val newProfile = profileManager.createProfile(
                name = name,
                avatarColorHex = avatarColorHex,
                avatarId = avatarId
            )
            if (newProfile != null) {
                if (usesPrimaryAddons || usesPrimaryPlugins) {
                    profileManager.updateProfile(
                        newProfile.copy(
                            usesPrimaryAddons = usesPrimaryAddons,
                            usesPrimaryPlugins = usesPrimaryPlugins
                        )
                    )
                }
                profileSyncService.pushToRemote()
            }
            _isCreating.value = false
        }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            profileManager.updateProfile(profile)
            profileSyncService.pushToRemote()
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileManager.deleteProfile(id)
            profileSyncService.deleteProfileData(id)
            profileSyncService.pushToRemote()
        }
    }
}
