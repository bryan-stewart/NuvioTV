package com.nuvio.tv.ui.screens

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.remote.supabase.SupabaseHousehold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class HouseholdSelectionViewModel @Inject constructor(
    private val profileManager: ProfileManager
) : ViewModel() {
    suspend fun loadHouseholds(): List<SupabaseHousehold> = profileManager.pullMyHouseholds()

    suspend fun choose(householdId: String) {
        profileManager.selectHousehold(householdId)
    }
}

/**
 * Shown once per sign-in, before any profile sync — a login that belongs to
 * more than one household must pick which one this device signs into. A
 * login with only a single household never sees this at all; the caller
 * auto-selects it directly instead of showing a one-item picker.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HouseholdSelectionScreen(
    households: List<SupabaseHousehold>,
    onSelected: (SupabaseHousehold) -> Unit,
    viewModel: HouseholdSelectionViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }

    fun choose(household: SupabaseHousehold) {
        coroutineScope.launch {
            viewModel.choose(household.householdId)
            onSelected(household)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = NuvioTheme.spacing.xxxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.household_picker_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = NuvioTheme.colors.TextPrimary
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
            Text(
                text = stringResource(R.string.household_picker_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(36.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(households, key = { it.householdId }) { household ->
                    HouseholdCard(
                        household = household,
                        onClick = { choose(household) },
                        modifier = if (household == households.first()) {
                            Modifier.focusRequester(firstFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HouseholdCard(
    household: SupabaseHousehold,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                shape = RoundedCornerShape(NuvioTheme.radii.md)
            ),
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(NuvioTheme.radii.md)
            )
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Card(
            onClick = onClick,
            colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
            scale = CardDefaults.scale(focusedScale = 1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NuvioTheme.spacing.xl, vertical = NuvioTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = household.householdName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = NuvioTheme.colors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (household.isManager) {
                    Text(
                        text = stringResource(R.string.household_picker_you_manage),
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
            }
        }
    }
}
