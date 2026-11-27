package com.example.feature.shield.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.common.FormatUtils
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.shield.model.FilterListItem
import com.example.feature.shield.model.FilterRuleItem
import com.example.feature.shield.model.RuleEvaluationPreview
import com.example.feature.shield.model.ShieldEngineStats

@Composable
fun ShieldEngineSummaryCard(
    stats: ShieldEngineStats,
    isSyncing: Boolean,
    onSyncNow: () -> Unit
) {
    OrbitSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stats.backendLabel,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (stats.isNativeRustActive) {
                                "Native Rust adblock-rust JNI active"
                            } else {
                                "Pure Kotlin compiled matcher · adblock-rust JNI ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OrbitBadgeChip(text = if (stats.isNativeRustActive) "RUST" else "ABP")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
            ) {
                StatPill(label = "Network Rules", value = stats.networkBlockRuleCount.toString(), modifier = Modifier.weight(1f))
                StatPill(label = "Exceptions", value = stats.exceptionRuleCount.toString(), modifier = Modifier.weight(1f))
                StatPill(label = "Cosmetic CSS", value = stats.cosmeticRuleCount.toString(), modifier = Modifier.weight(1f))
            }

            OrbitPrimaryButton(
                text = if (isSyncing) "Syncing EasyPrivacy & AdGuard..." else "Update Filter Lists Now",
                onClick = onSyncNow,
                isLoading = isSyncing,
                icon = Icons.Rounded.Sync,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shield_sync_lists_button")
            )
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FilterListCard(
    item: FilterListItem,
    onToggle: (Boolean) -> Unit
) {
    OrbitSurfaceCard(onClick = { onToggle(!item.enabled) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    OrbitBadgeChip(text = item.category)
                }
                val updated = if (item.lastUpdatedAt > 0L) FormatUtils.formatDateBucket(item.lastUpdatedAt) else "Built-in"
                Text(
                    text = "${item.ruleCount} network · ${item.cosmeticRuleCount} cosmetic · $updated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("filter_list_switch_${item.id}")
            )
        }
    }
}

@Composable
fun CustomRuleCard(
    rule: FilterRuleItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    OrbitSurfaceCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrbitBadgeChip(text = rule.ruleType.label)
                    Text(
                        text = rule.rawRule,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Scope: ${rule.domainFilter}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("custom_rule_switch_${rule.id}")
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("custom_rule_delete_${rule.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete rule"
                )
            }
        }
    }
}

@Composable
fun RuleTesterCard(
    preview: RuleEvaluationPreview?,
    onEvaluate: (String, String?) -> Unit
) {
    var testUrl by remember { mutableStateOf("") }
    var docHost by remember { mutableStateOf("") }

    OrbitSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
        ) {
            Text(text = "Rule Evaluator Preview", style = MaterialTheme.typography.titleMedium)
            OrbitTextField(
                value = testUrl,
                onValueChange = { testUrl = it },
                label = "Request URL",
                placeholder = "https://...",
                modifier = Modifier.testTag("shield_test_url_input")
            )
            OrbitTextField(
                value = docHost,
                onValueChange = { docHost = it },
                label = "Page Host (optional)",
                placeholder = "domain.com",
                modifier = Modifier.testTag("shield_test_host_input")
            )
            OrbitSecondaryButton(
                text = "Test Request Against Shield",
                onClick = { onEvaluate(testUrl, docHost) },
                enabled = testUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shield_evaluate_rule_button")
            )
            if (preview != null) {
                Text(
                    text = if (preview.blocked) {
                        "BLOCKED by ${preview.listSource ?: "Shield"} (${preview.matchedRule ?: "rule"})"
                    } else {
                        "ALLOWED${preview.matchedRule?.let { " (exception: $it)" } ?: " (no blocking rule matched)"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (preview.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AddCustomRuleDialog(
    onDismiss: () -> Unit,
    onAddRule: (String) -> Unit
) {
    var ruleText by remember { mutableStateOf("") }
    OrbitDialog(
        onDismissRequest = onDismiss,
        title = "Add AdBlock / Cosmetic Rule",
        subtitle = "Adblock Plus network & cosmetic filter rule",
        icon = Icons.Rounded.Shield,
        actions = {
            OrbitSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = "Add Rule",
                onClick = {
                    onAddRule(ruleText)
                    onDismiss()
                },
                enabled = ruleText.trim().length >= 3,
                icon = Icons.Rounded.Add,
                modifier = Modifier
                    .weight(1f)
                    .testTag("custom_rule_confirm_button")
            )
        }
    ) {
        Text(
            text = "Supports ABP syntax:\n• ||tracker.example.com^\n• @@||allowed.example.com^\n• ##.ad-banner or site.com##.promo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OrbitTextField(
            value = ruleText,
            onValueChange = { ruleText = it },
            label = "Rule",
            placeholder = "||ads.example.com^",
            modifier = Modifier.testTag("custom_rule_input")
        )
    }
}
