package com.nuvio.tv.ui.screens.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.ProfileSyncService
import com.nuvio.tv.core.sync.SetProfilePinResult
import com.nuvio.tv.data.local.ProfileLockStateDataStore
import com.nuvio.tv.data.remote.supabase.AvatarCatalogItem
import com.nuvio.tv.data.remote.supabase.AvatarRepository
import com.nuvio.tv.data.remote.supabase.ProfileBackgroundCatalogItem
import com.nuvio.tv.data.remote.supabase.ProfileBackgroundRepository
import com.nuvio.tv.data.remote.supabase.SupabaseProfilePinVerifyResult
import com.nuvio.tv.data.repository.MemberAccessRepository
import com.nuvio.tv.domain.model.CosmeticEntitlement
import com.nuvio.tv.domain.model.ServerConfiguration
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CreateProfileResult {
    data class Created(val profile: UserProfile) : CreateProfileResult

    data object Failed : CreateProfileResult
}

@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    private val profileManager: ProfileManager,
    private val profileSyncService: ProfileSyncService,
    private val avatarRepository: AvatarRepository,
    private val profileBackgroundRepository: ProfileBackgroundRepository,
    memberAccessRepository: MemberAccessRepository,
    private val profileLockStateDataStore: ProfileLockStateDataStore,
    private val serverConfiguration: ServerConfiguration
) : ViewModel() {
    val activeProfileId: StateFlow<String> = profileManager.activeProfileId
    val profiles: StateFlow<List<UserProfile>> = profileManager.profiles

    // Where to send a Manager to remove an account-holding member — deleting
    // someone's login isn't a thing this device can do on their behalf, so
    // the delete dialog for an is_account profile points here instead of
    // deleting anything itself. The dashboard's household settings page
    // lives on the same backend host the client already knows.
    val manageHouseholdUrl: String
        get() = "${serverConfiguration.backendUrl.trimEnd('/')}/settings"

    val canAddProfile: Boolean
        get() = profileManager.canCreateProfile

    private val _avatarCatalog = MutableStateFlow<List<AvatarCatalogItem>>(emptyList())
    val avatarCatalog: StateFlow<List<AvatarCatalogItem>> = _avatarCatalog.asStateFlow()

    // profile.avatarUrl is now the household's own avatar assignment (see
    // sync_pull_profiles's own comment): a plain http(s) URL for a catalog
    // pick, but a bare path in the private "household-avatars" bucket for a
    // custom upload, which needs an authenticated download before it's
    // usable as an image URL. Resolved here (keyed by profile id, since the
    // path itself carries no catalog id to key by) rather than inline in
    // the composable, so it survives recomposition and stays in step with
    // profiles without re-downloading on every frame.
    private val _householdAvatarUrisById = MutableStateFlow<Map<String, String>>(emptyMap())
    val householdAvatarUrisById: StateFlow<Map<String, String>> = _householdAvatarUrisById.asStateFlow()

    val hasProfileAvatarAccess: StateFlow<Boolean> = memberAccessRepository.access
        .map { access -> access.entitlements.includes(CosmeticEntitlement.PROFILE_AVATARS) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasProfileBackgroundAccess: StateFlow<Boolean> = memberAccessRepository.access
        .map { access -> access.entitlements.includes(CosmeticEntitlement.PROFILE_BACKGROUNDS) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val profileBackgroundCatalog: StateFlow<List<ProfileBackgroundCatalogItem>> = combine(
        profileBackgroundRepository.catalog,
        hasProfileBackgroundAccess
    ) { catalog, hasAccess -> if (hasAccess) catalog else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    val profilePinEnabled: StateFlow<Map<String, Boolean>> = profileLockStateDataStore.pinEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _isPinOperationInProgress = MutableStateFlow(false)
    val isPinOperationInProgress: StateFlow<Boolean> = _isPinOperationInProgress.asStateFlow()

    init {
        refreshProfilePinStates()
        viewModelScope.launch {
            hasProfileAvatarAccess.collectLatest { hasAccess ->
                loadAvatarCatalog(hasAccess)
            }
        }
        viewModelScope.launch {
            hasProfileBackgroundAccess.collectLatest { hasAccess ->
                if (hasAccess) {
                    profileBackgroundRepository.ensureLoaded()
                } else {
                    profileBackgroundRepository.invalidateCache()
                }
            }
        }
        viewModelScope.launch {
            profiles.collectLatest { list -> resolveHouseholdAvatars(list) }
        }
    }

    private suspend fun resolveHouseholdAvatars(list: List<UserProfile>) {
        val storagePathsById = list.mapNotNull { profile ->
            val url = profile.avatarUrl?.takeIf { it.isNotBlank() }
            if (url != null && !url.startsWith("http://") && !url.startsWith("https://")) {
                profile.id to url
            } else {
                null
            }
        }
        if (storagePathsById.isEmpty()) {
            if (_householdAvatarUrisById.value.isNotEmpty()) _householdAvatarUrisById.value = emptyMap()
            return
        }
        val resolved = buildMap {
            storagePathsById.forEach { (profileId, path) ->
                try {
                    avatarRepository.getHouseholdAvatarImageUri(path)?.let { uri -> put(profileId, uri) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("ProfileSelectionVM", "Failed to resolve household avatar for $profileId", error)
                }
            }
        }
        _householdAvatarUrisById.value = resolved
    }

    fun loadAvatarCatalog() {
        viewModelScope.launch {
            loadAvatarCatalog(hasProfileAvatarAccess.value)
        }
    }

    private suspend fun loadAvatarCatalog(hasMemberAccess: Boolean) {
        try {
            _avatarCatalog.value = avatarRepository.getAvatarCatalog(hasMemberAccess)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("ProfileSelectionVM", "Failed to load avatar catalog", error)
        }
    }

    fun getAvatarImageUrl(avatarId: String?): String? {
        if (avatarId == null) return null
        return avatarRepository.getAvatarImageUrl(avatarId, _avatarCatalog.value)
    }

    fun loadProfileBackground(id: String) {
        if (hasProfileBackgroundAccess.value) profileBackgroundRepository.loadSelectedAndPreload(id)
    }

    fun preloadProfileBackgrounds() {
        if (hasProfileBackgroundAccess.value) profileBackgroundRepository.preloadImages()
    }

    fun selectProfile(id: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            profileManager.setActiveProfile(id)
            onComplete()
        }
    }

    fun createProfile(
        name: String,
        avatarColorHex: String,
        avatarId: String? = null,
        onComplete: (CreateProfileResult) -> Unit = {}
    ) {
        if (_isCreating.value) return
        viewModelScope.launch {
            _isCreating.value = true
            val result = try {
                val profile = profileManager.createProfile(
                    name = name,
                    avatarColorHex = avatarColorHex,
                    avatarId = avatarId
                )
                if (profile != null) {
                    profileSyncService.pushToRemote()
                    refreshProfilePinStates()
                    CreateProfileResult.Created(profile)
                } else {
                    CreateProfileResult.Failed
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ProfileSelectionVM", "Failed to create profile", error)
                CreateProfileResult.Failed
            } finally {
                _isCreating.value = false
            }
            onComplete(result)
        }
    }

    fun updateProfile(profile: UserProfile) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            profileManager.updateProfile(profile)
            profileSyncService.pushToRemote()
            refreshProfilePinStates()
            _isSaving.value = false
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileManager.deleteProfile(id)
            profileSyncService.deleteProfileData(id)
            profileSyncService.pushToRemote()
            refreshProfilePinStates()
        }
    }

    fun refreshProfilePinStates() {
        viewModelScope.launch {
            var attempt = 0
            while (attempt < 4) {
                val result = profileSyncService.pullProfileLockStates()
                if (result.isSuccess) {
                    profileLockStateDataStore.replaceAll(result.getOrNull().orEmpty())
                    return@launch
                }
                Log.e(
                    "ProfileSelectionVM",
                    "Failed to refresh profile PIN states (attempt=$attempt)",
                    result.exceptionOrNull()
                )
                attempt++
                delay(2000L * attempt)
            }
        }
    }

    fun isProfilePinEnabled(profileId: String): Boolean {
        return profilePinEnabled.value[profileId] == true
    }

    fun setProfilePin(
        profileId: String,
        pin: String,
        currentPin: String? = null,
        onComplete: (SetProfilePinResult) -> Unit
    ) {
        if (_isPinOperationInProgress.value) return
        viewModelScope.launch {
            _isPinOperationInProgress.value = true
            val result = profileSyncService.setProfilePin(profileId, pin, currentPin)
            // Server reporting CurrentPinRequired means a PIN exists remotely —
            // reconcile local cache so we never forget it again.
            if (result is SetProfilePinResult.Success || result is SetProfilePinResult.CurrentPinRequired) {
                profileLockStateDataStore.setPinEnabled(profileId, true)
            }
            _isPinOperationInProgress.value = false
            onComplete(result)
        }
    }

    fun clearProfilePin(profileId: String, currentPin: String? = null, onComplete: (Boolean) -> Unit) {
        if (_isPinOperationInProgress.value) return
        viewModelScope.launch {
            _isPinOperationInProgress.value = true
            val success = profileSyncService.clearProfilePin(profileId, currentPin).isSuccess
            if (success) {
                profileLockStateDataStore.setPinEnabled(profileId, false)
            }
            _isPinOperationInProgress.value = false
            onComplete(success)
        }
    }

    fun verifyProfilePin(profileId: String, pin: String, onComplete: (Result<SupabaseProfilePinVerifyResult>) -> Unit) {
        if (_isPinOperationInProgress.value) return
        viewModelScope.launch {
            _isPinOperationInProgress.value = true
            val result = profileSyncService.verifyProfilePin(profileId, pin)
            _isPinOperationInProgress.value = false
            onComplete(result)
        }
    }
}
