package com.example.feature.passwords.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSectionHeader
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.passwords.domain.PasswordStrength
import com.example.feature.passwords.model.ProtonPassStatus
import com.example.feature.passwords.provider.ProtonPassIntegration
import com.example.feature.passwords.viewmodel.PasswordsUiState
import com.example.feature.passwords.viewmodel.PasswordsViewModel
import kotlin.math.roundToInt

@Composable
fun PasswordGeneratorTab(
    uiState: PasswordsUiState,
    viewModel: PasswordsViewModel
) {
    val opts = uiState.generatorOptions
    val strength = uiState.generatedStrength

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(OrbitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
    ) {
        item {
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
                        Text("Generated Password", style = MaterialTheme.typography.titleMedium)
                        OrbitBadgeChip(text = "${strength.strength.label} · ${strength.entropyBits}b")
                    }
                    Text(
                        text = uiState.generatedPassword,
                        style = OrbitMonoStyle,
                        modifier = Modifier.testTag("generator_password_output")
                    )
                    LinearProgressIndicator(
                        progress = { strength.strength.scorePercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = when (strength.strength) {
                            PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
                            PasswordStrength.FAIR -> MaterialTheme.colorScheme.tertiary
                            PasswordStrength.GOOD -> MaterialTheme.colorScheme.primary
                            PasswordStrength.STRONG -> OrbitEmerald
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)) {
                        OrbitPrimaryButton(
                            text = "Copy",
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                viewModel.copySensitiveSecret("Generated password", uiState.generatedPassword)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generator_copy_button")
                        )
                        OrbitSecondaryButton(
                            text = "Regenerate",
                            icon = Icons.Rounded.Refresh,
                            onClick = viewModel::regeneratePassword,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generator_regenerate_button")
                        )
                    }
                }
            }
        }

        item {
            OrbitSurfaceCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Length", style = MaterialTheme.typography.titleSmall)
                        Text("${opts.length} characters", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = opts.length.toFloat(),
                        onValueChange = { value ->
                            viewModel.updateGeneratorOptions { it.copy(length = value.roundToInt()) }
                        },
                        valueRange = 8f..48f
                    )
                    GeneratorCheckRow("Uppercase (A-Z)", opts.includeUppercase) { checked ->
                        viewModel.updateGeneratorOptions { it.copy(includeUppercase = checked) }
                    }
                    GeneratorCheckRow("Lowercase (a-z)", opts.includeLowercase) { checked ->
                        viewModel.updateGeneratorOptions { it.copy(includeLowercase = checked) }
                    }
                    GeneratorCheckRow("Numbers (0-9)", opts.includeDigits) { checked ->
                        viewModel.updateGeneratorOptions { it.copy(includeDigits = checked) }
                    }
                    GeneratorCheckRow("Symbols (!@#\$...)", opts.includeSymbols) { checked ->
                        viewModel.updateGeneratorOptions { it.copy(includeSymbols = checked) }
                    }
                    GeneratorCheckRow("Exclude ambiguous (0, O, 1, l, I)", opts.excludeAmbiguous) { checked ->
                        viewModel.updateGeneratorOptions { it.copy(excludeAmbiguous = checked) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratorCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ProtonPassTab(
    status: ProtonPassStatus?,
    protonPassIntegration: ProtonPassIntegration,
    onRefreshStatus: () -> Unit,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(OrbitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
    ) {
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Proton Pass Integration", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = onRefreshStatus) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh status")
                        }
                    }

                    Text(
                        text = if (status?.isInstalled == true) {
                            "Proton Pass is installed on this device. Use Android Autofill Services to fill Proton Pass logins directly inside Orbit WebView forms."
                        } else {
                            "Proton Pass is not installed. You can continue using Orbit Local Vault offline, or install Proton Pass for cloud sync via Android Autofill."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrbitBadgeChip(
                            text = if (status?.isInstalled == true) "App Installed" else "Not Installed"
                        )
                        OrbitBadgeChip(
                            text = if (status?.hasSystemAutofillEnabled == true) "Autofill Enabled" else "Autofill Off"
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)) {
                        if (status?.isInstalled == true) {
                            OrbitPrimaryButton(
                                text = "Open Proton Pass",
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                onClick = {
                                    val intent = protonPassIntegration.buildOpenProtonPassIntent()
                                    if (intent != null) {
                                        runCatching { context.startActivity(intent) }
                                            .onFailure { onError("Unable to open Proton Pass") }
                                    } else {
                                        onError("Proton Pass launch intent unavailable")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            OrbitPrimaryButton(
                                text = "Get Proton Pass",
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                onClick = {
                                    runCatching {
                                        context.startActivity(protonPassIntegration.buildInstallProtonPassIntent())
                                    }.onFailure {
                                        onError("No app store found to install Proton Pass")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OrbitSecondaryButton(
                            text = "Autofill Settings",
                            icon = Icons.Rounded.Settings,
                            onClick = {
                                runCatching {
                                    context.startActivity(protonPassIntegration.buildAutofillSettingsIntent())
                                }.onFailure {
                                    onError("System Autofill settings not available on this device")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            OrbitSectionHeader(title = "Capability Separation (Zero-Scraping)")
        }

        item {
            OrbitSurfaceCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Strict Provider Isolation", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = "Orbit never accesses Proton Pass private storage, session cookies, or internal APIs, and never merges external items into your Local Vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    CapabilityRow("Local Vault", "Full CRUD, Offline Search, Argon2id + AES-GCM, Health Audit, Portable Backup")
                    CapabilityRow("Proton Pass", "Android Autofill Framework Hand-off Only (delegated by OS)")
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(provider: String, capabilities: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(provider, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(capabilities, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
