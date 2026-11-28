package com.example.feature.sandbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.common.UrlUtils
import com.example.core.ui.components.OrbitCheckboxRow
import com.example.core.ui.components.OrbitControlTokens
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSelectableOptionCard
import com.example.core.ui.components.OrbitSheetPanel
import com.example.core.ui.components.OrbitTextField
import com.example.feature.sandbox.model.SandboxModel
import com.example.feature.sandbox.model.SandboxPresets

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateEditSandboxDialog(
    initial: SandboxModel? = null,
    prefillSiteUrl: String = "",
    suggestedName: String = "",
    suggestedColorHex: Long = SandboxPresets.colors.first().colorHex,
    confirmLabel: String = if (initial == null) "Create" else "Save",
    onConfirm: (
        name: String,
        siteUrl: String,
        colorHex: Long,
        iconKey: String,
        keepLongTerm: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val cleanPrefillUrl = remember(initial, prefillSiteUrl) {
        initial?.startUrl ?: if (UrlUtils.isStartPage(prefillSiteUrl)) "" else prefillSiteUrl
    }
    var name by remember(initial, suggestedName) {
        mutableStateOf(initial?.name ?: suggestedName)
    }
    var siteUrl by remember(cleanPrefillUrl) {
        mutableStateOf(cleanPrefillUrl)
    }
    var selectedColorHex by remember(initial, suggestedColorHex) {
        mutableLongStateOf(initial?.colorHex ?: suggestedColorHex)
    }
    val selectedCategory = initial?.iconKey ?: "work"
    var keepLongTerm by remember(initial) {
        mutableStateOf(initial?.keepLongTerm ?: true)
    }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "New Sandbox" else "Edit Sandbox",
        actions = {
            OrbitSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = confirmLabel,
                onClick = {
                    onConfirm(
                        name.trim(),
                        siteUrl.trim(),
                        selectedColorHex,
                        selectedCategory,
                        keepLongTerm
                    )
                },
                enabled = name.isNotBlank() || siteUrl.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sandbox_save_button")
            )
        }
    ) { palette ->
        OrbitTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            modifier = Modifier.testTag("sandbox_name_input")
        )
        OrbitTextField(
            value = siteUrl,
            onValueChange = { siteUrl = it },
            label = "Website URL (optional)",
            modifier = Modifier.testTag("sandbox_url_input")
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SandboxPresets.colors.forEach { preset ->
                val selected = preset.colorHex == selectedColorHex
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Surface(
                        onClick = { selectedColorHex = preset.colorHex },
                        shape = CircleShape,
                        color = if (selected) preset.color.copy(alpha = 0.2f) else palette.tile,
                        border = BorderStroke(
                            width = if (selected) 1.4.dp else OrbitControlTokens.HairlineWidth,
                            color = if (selected) preset.color else palette.hairline
                        ),
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("sandbox_color_${preset.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(preset.color),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = preset.label,
                                        tint = Color.Black,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        OrbitSheetPanel {
            OrbitCheckboxRow(
                title = "Persistent session",
                checked = keepLongTerm,
                onCheckedChange = { keepLongTerm = it },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun SandboxPickerDialog(
    currentUrl: String,
    activeSandboxId: String?,
    sandboxes: List<SandboxModel>,
    isMultiProfileSupported: Boolean,
    onSelectSandbox: (SandboxModel) -> Unit,
    onOpenInDefaultProfile: () -> Unit,
    onCreateNewSandbox: () -> Unit,
    onManageSandboxes: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentHost = remember(currentUrl) { UrlUtils.extractHost(currentUrl).lowercase() }
    val sortedSandboxes = remember(sandboxes, currentHost) {
        sandboxes.sortedWith(
            compareByDescending<SandboxModel> {
                currentHost.isNotBlank() && it.pinnedDomain.equals(currentHost, ignoreCase = true)
            }.thenByDescending { it.lastUsedAt }
        )
    }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = "Sandboxes",
        subtitle = currentHost.takeIf { it.isNotBlank() },
        showCloseButton = true,
        actions = {
            OrbitSecondaryButton(
                text = "Manage",
                icon = Icons.Rounded.Settings,
                onClick = {
                    onDismiss()
                    onManageSandboxes()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("sandbox_picker_manage_button")
            )
            OrbitPrimaryButton(
                text = "New",
                icon = Icons.Rounded.Add,
                onClick = {
                    onDismiss()
                    onCreateNewSandbox()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("sandbox_picker_create_button")
            )
        }
    ) { palette ->
        if (!isMultiProfileSupported) {
            Text(
                text = "Update System WebView for full cookie isolation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (activeSandboxId != null) {
            OrbitSelectableOptionCard(
                title = "Default Profile",
                icon = Icons.Rounded.Public,
                selected = false,
                onClick = {
                    onDismiss()
                    onOpenInDefaultProfile()
                }
            )
            HorizontalDivider(color = palette.innerDivider)
        }

        if (sortedSandboxes.isEmpty()) {
            Text(
                text = "No sandboxes yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            sortedSandboxes.forEach { sandbox ->
                val isCurrent = sandbox.id == activeSandboxId
                val hostMatches = currentHost.isNotBlank() && sandbox.pinnedDomain.equals(currentHost, ignoreCase = true)
                val sub = buildString {
                    if (sandbox.pinnedDomain.isNotBlank()) append(sandbox.pinnedDomain)
                    if (sandbox.openTabCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${sandbox.openTabCount} open")
                    }
                }.takeIf { it.isNotBlank() }

                OrbitSelectableOptionCard(
                    title = sandbox.name,
                    subtitle = sub,
                    selected = isCurrent,
                    accentColor = sandbox.accentColor,
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(sandbox.accentColor)
                        )
                    },
                    trailingBadgeText = when {
                        isCurrent -> "Active"
                        hostMatches -> "Match"
                        else -> null
                    },
                    onClick = {
                        onDismiss()
                        onSelectSandbox(sandbox)
                    },
                    modifier = Modifier.testTag("sandbox_pick_${sandbox.id}")
                )
            }
        }
    }
}
