package com.example.feature.passwords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitEmptyState
import com.example.core.ui.components.OrbitFilterField
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.passwords.domain.DomainMatcher
import com.example.feature.passwords.model.Credential
import com.example.feature.passwords.viewmodel.PasswordsUiState
import com.example.feature.passwords.viewmodel.PasswordsViewModel

@Composable
fun CredentialsListTab(
    uiState: PasswordsUiState,
    viewModel: PasswordsViewModel,
    showProtonBanner: Boolean,
    onAddClick: () -> Unit,
    onManageFolders: () -> Unit,
    onOpenProtonTab: () -> Unit
) {
    val creds = uiState.filteredCredentials

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrbitFilterField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                placeholder = "Search site, title or username",
                testTag = "passwords_search_field"
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    OrbitSegmentPill(
                        label = "All (${uiState.credentials.size})",
                        selected = uiState.selectedFolderId == null && !uiState.favoritesOnly,
                        onClick = {
                            viewModel.selectFolder(null)
                            if (uiState.favoritesOnly) viewModel.toggleFavoritesOnly()
                        }
                    )
                }
                item {
                    OrbitSegmentPill(
                        label = "Favorites",
                        selected = uiState.favoritesOnly,
                        onClick = viewModel::toggleFavoritesOnly,
                        leadingIcon = Icons.Rounded.Star
                    )
                }
                items(uiState.folders, key = { it.id }) { folder ->
                    OrbitSegmentPill(
                        label = folder.name,
                        selected = uiState.selectedFolderId == folder.id,
                        onClick = {
                            viewModel.selectFolder(if (uiState.selectedFolderId == folder.id) null else folder.id)
                        }
                    )
                }
                item {
                    IconButton(onClick = onManageFolders) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Manage folders")
                    }
                }
            }
        }

        if (creds.isEmpty()) {
            OrbitEmptyState(
                title = if (uiState.credentials.isEmpty()) "Local Vault is empty" else "No matching logins",
                subtitle = if (uiState.credentials.isEmpty()) {
                    "Add passwords manually, import from CSV, or save directly when logging into websites."
                } else {
                    "Try clearing your search or folder filter."
                },
                actionLabel = if (uiState.credentials.isEmpty()) "Add Password" else null,
                onActionClick = if (uiState.credentials.isEmpty()) onAddClick else null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = OrbitSpacing.md,
                    end = OrbitSpacing.md,
                    bottom = 92.dp,
                    top = OrbitSpacing.xs
                ),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
            ) {
                if (showProtonBanner && uiState.protonStatus?.isInstalled == true) {
                    item(key = "proton_summary_banner") {
                        OrbitSurfaceCard(onClick = onOpenProtonTab) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(OrbitSpacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Proton Pass Installed", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Managed via Android Autofill Services · Tap to inspect status",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OrbitBadgeChip(text = "Proton Pass")
                            }
                        }
                    }
                }
                items(creds, key = { it.id }) { cred ->
                    CredentialRowCard(
                        credential = cred,
                        isBreached = uiState.breachCountsById.containsKey(cred.id),
                        onClick = { viewModel.selectCredential(cred) },
                        onToggleFavorite = { viewModel.toggleFavorite(cred) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialRowCard(
    credential: Credential,
    isBreached: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val domain = remember(credential.websiteUrl) {
        DomainMatcher.normalizeDomain(credential.websiteUrl).ifBlank { credential.title }
    }
    val initial = domain.firstOrNull()?.uppercaseChar()?.toString() ?: "O"

    OrbitSurfaceCard(
        onClick = onClick,
        modifier = Modifier.testTag("credential_row_${credential.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = credential.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isBreached) {
                        OrbitBadgeChip(text = "BREACHED")
                    }
                }
                Text(
                    text = credential.username?.takeIf { it.isNotBlank() } ?: domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OrbitBadgeChip(text = credential.source.label)
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (credential.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Toggle favorite",
                    tint = if (credential.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
