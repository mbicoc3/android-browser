package com.example.feature.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.network.DohProvider
import com.example.core.network.PrivateDnsMode
import com.example.core.network.PrivateDnsStatus
import com.example.core.ui.components.OrbitActionPill
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.settings.viewmodel.DohTestState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DnsPrivacySection(
    selectedDohProvider: DohProvider,
    dohTestState: DohTestState,
    privateDnsStatus: PrivateDnsStatus,
    onSelectDohProvider: (DohProvider) -> Unit,
    onTestDoh: (DohProvider) -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)) {
        OrbitSurfaceCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(OrbitSpacing.md),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Free DNS-over-HTTPS (App DoH)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Encrypts suggestions, filter updates, downloads & breach checks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OrbitBadgeChip(text = "FREE")
                }

                DohProvider.entries.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDohProvider == provider,
                            onClick = { onSelectDohProvider(provider) },
                            modifier = Modifier.testTag("settings_doh_${provider.id}")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = provider.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OrbitSecondaryButton(
                    text = "Verify Live DoH Resolution",
                    onClick = { onTestDoh(selectedDohProvider) },
                    isLoading = dohTestState.isTesting,
                    icon = Icons.Rounded.NetworkCheck,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_test_doh_button")
                )

                dohTestState.resultSummary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dohTestState.isSuccess) OrbitEmerald else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        OrbitSurfaceCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(OrbitSpacing.md),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (privateDnsStatus.isEncrypted) OrbitEmerald else MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Phone Private DNS (WebView)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            val statusText = when (privateDnsStatus.mode) {
                                PrivateDnsMode.STRICT_HOSTNAME ->
                                    "Protected via ${privateDnsStatus.serverName.orEmpty()}"
                                PrivateDnsMode.AUTOMATIC ->
                                    "Automatic (opportunistic DoT enabled)"
                                PrivateDnsMode.OFF ->
                                    "Currently Off · Enable in Android Settings"
                                PrivateDnsMode.UNSUPPORTED ->
                                    "Requires Android 9+ for Private DNS"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (privateDnsStatus.isEncrypted) {
                                    OrbitEmerald
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    OrbitBadgeChip(
                        text = when (privateDnsStatus.mode) {
                            PrivateDnsMode.STRICT_HOSTNAME -> "DoT ON"
                            PrivateDnsMode.AUTOMATIC -> "AUTO"
                            PrivateDnsMode.OFF -> "OFF"
                            PrivateDnsMode.UNSUPPORTED -> "OS < 9"
                        }
                    )
                }

                Text(
                    text = "Android WebView uses the phone's system DNS stack, so enabling free Private DNS (DNS-over-TLS) encrypts all webpage browsing DNS queries system-wide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    text = "Tap any free Private DNS hostname to copy, then paste under Settings → Network & Internet → Private DNS:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val recommendedHosts = listOf(
                    "Cloudflare" to DohProvider.CLOUDFLARE.privateDnsHostname,
                    "Quad9" to DohProvider.QUAD9.privateDnsHostname,
                    "AdGuard" to DohProvider.ADGUARD.privateDnsHostname
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recommendedHosts.forEach { (label, hostname) ->
                        OrbitActionPill(
                            label = "$label: $hostname",
                            leadingIcon = Icons.Rounded.ContentCopy,
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText(label, hostname))
                                onNotify("Copied $hostname ($label)")
                            },
                            modifier = Modifier.testTag("copy_private_dns_${label.lowercase()}")
                        )
                    }
                }

                OrbitSecondaryButton(
                    text = "Open Android Private DNS Settings",
                    onClick = onOpenPrivateDnsSettings,
                    icon = Icons.Rounded.Settings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_open_private_dns_button")
                )
            }
        }
    }
}
