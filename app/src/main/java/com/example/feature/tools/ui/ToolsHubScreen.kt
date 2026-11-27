package com.example.feature.tools.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.registry.BrowserFeatureModule
import com.example.core.registry.BrowserToolRegistry
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitSectionHeader
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing

/**
 * Registry-driven directory of all modular browser features and tools.
 * Newly registered tools automatically appear here grouped by category.
 */
@Composable
fun ToolsHubScreen(
    onOpenRoute: (String) -> Unit,
    onTriggerInPageTool: (BrowserFeatureModule) -> Unit,
    onBack: () -> Unit
) {
    val groupedModules = remember { BrowserToolRegistry.getGroupedModules() }

    Scaffold(
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.tools_hub_title),
                subtitle = stringResource(R.string.tools_hub_subtitle),
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(OrbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
        ) {
            groupedModules.forEach { (category, modules) ->
                item(key = "cat_${category.name}") {
                    OrbitSectionHeader(title = category.title)
                }
                items(modules.filter { it.id != "tools_hub" }, key = { it.id }) { module ->
                    OrbitSurfaceCard(
                        onClick = {
                            if (module.route != null) {
                                onOpenRoute(module.route)
                            } else {
                                onTriggerInPageTool(module)
                            }
                        },
                        modifier = Modifier.testTag("hub_module_${module.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = OrbitSpacing.md, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = module.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = module.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = module.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            module.badgeText?.let {
                                OrbitBadgeChip(text = it)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
