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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.profile.ProfileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class HouseholdScopeSelectionViewModel @Inject constructor(
    private val profileManager: ProfileManager
) : ViewModel() {
    suspend fun choose(scope: String) {
        profileManager.selectHouseholdScope(scope)
    }
}

/**
 * Shown only to a household's own Manager, once per sign-in, right after
 * a household is resolved (picked or auto-selected) — a non-Manager never
 * sees this at all, their scope is forced to "self" server-side regardless
 * (see sync_pull_profiles's own comment). The choice is remembered on this
 * device only; changing it later means signing out and back in.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HouseholdScopeSelectionScreen(
    onContinue: (scope: String) -> Unit,
    viewModel: HouseholdScopeSelectionViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val wholeHouseholdFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        wholeHouseholdFocusRequester.requestFocus()
    }

    fun choose(scope: String) {
        coroutineScope.launch {
            viewModel.choose(scope)
            onContinue(scope)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = NuvioTheme.spacing.xxxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.household_scope_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = NuvioTheme.colors.TextPrimary
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
            Text(
                text = stringResource(R.string.household_scope_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(36.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HouseholdScopeCard(
                    title = stringResource(R.string.household_scope_whole_household),
                    subtitle = stringResource(R.string.household_scope_whole_household_subtitle),
                    icon = Icons.Default.Group,
                    onClick = { choose("household") },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(wholeHouseholdFocusRequester)
                )
                HouseholdScopeCard(
                    title = stringResource(R.string.household_scope_just_me),
                    subtitle = stringResource(R.string.household_scope_just_me_subtitle),
                    icon = Icons.Default.Person,
                    onClick = { choose("self") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HouseholdScopeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(210.dp),
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(NuvioTheme.spacing.xl),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NuvioTheme.colors.TextSecondary
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = NuvioTheme.colors.TextPrimary
                )
                Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary
                )
            }
        }
    }
}
