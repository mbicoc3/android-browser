package com.example.feature.shield.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.OrbitSectionHeader
import com.example.core.ui.components.OrbitSettingSwitchRow
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.shield.viewmodel.ShieldViewModel

@Composable
fun ShieldScreen(
    viewModel: ShieldViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.statusBanner) {
        val message = state.statusBanner
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissBanner()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.shield_title),
                subtitle = stringResource(R.string.shield_subtitle),
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("shield_add_rule_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add custom filter rule")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = OrbitSpacing.md,
                end = OrbitSpacing.md,
                top = OrbitSpacing.md,
                bottom = OrbitSpacing.xl + OrbitSpacing.lg
            ),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
        ) {
            item {
                ShieldEngineSummaryCard(
                    stats = state.engineStats,
                    isSyncing = state.isSyncing,
                    onSyncNow = viewModel::syncListsNow
                )
            }

            item { OrbitSectionHeader(title = "Protection Modes") }

            item {
                OrbitSettingSwitchRow(
                    title = "Network Ad & Tracker Blocking",
                    subtitle = "Intercept EasyPrivacy, AdGuard & custom host requests",
                    icon = Icons.Rounded.Shield,
                    checked = state.trackerShieldEnabled,
                    onCheckedChange = viewModel::setTrackerShieldEnabled,
                    testTag = "shield_network_toggle"
                )
            }

            item {
                OrbitSettingSwitchRow(
                    title = "Cosmetic Element Filtering",
                    subtitle = "Hide empty ad containers & sponsored widgets via CSS injection",
                    icon = Icons.Rounded.AutoFixHigh,
                    checked = state.cosmeticFilteringEnabled,
                    onCheckedChange = viewModel::setCosmeticFilteringEnabled,
                    testTag = "shield_cosmetic_toggle"
                )
            }

            item { OrbitSectionHeader(title = "Open-Source Filter Lists") }

            items(state.filterLists, key = { it.id }) { list ->
                FilterListCard(
                    item = list,
                    onToggle = { enabled -> viewModel.toggleFilterList(list.id, enabled) }
                )
            }

            item { OrbitSectionHeader(title = "Custom Rules (${state.customRules.size})") }

            if (state.customRules.isEmpty()) {
                item {
                    OrbitSurfaceCard {
                        Text(
                            text = "No custom rules yet. Tap + to add block (||domain^), exception (@@||domain^), or cosmetic (##.selector) rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(OrbitSpacing.md)
                        )
                    }
                }
            } else {
                items(state.customRules, key = { it.id }) { rule ->
                    CustomRuleCard(
                        rule = rule,
                        onToggle = { enabled -> viewModel.toggleCustomRule(rule.id, enabled) },
                        onDelete = { viewModel.deleteCustomRule(rule.id) }
                    )
                }
            }

            item { OrbitSectionHeader(title = "Diagnostics") }

            item {
                RuleTesterCard(
                    preview = state.testPreview,
                    onEvaluate = viewModel::testUrl
                )
            }
        }
    }

    if (showAddDialog) {
        AddCustomRuleDialog(
            onDismiss = { showAddDialog = false },
            onAddRule = viewModel::addCustomRule
        )
    }
}
