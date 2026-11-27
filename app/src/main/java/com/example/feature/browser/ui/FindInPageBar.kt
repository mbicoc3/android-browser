package com.example.feature.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ui.components.OrbitControlTokens
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.components.rememberOrbitSurfacePalette
import com.example.core.ui.theme.OrbitSpacing

@Composable
fun FindInPageBar(
    query: String,
    matchIndex: Int,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberOrbitSurfacePalette()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.canvas,
        tonalElevation = 0.dp,
        border = BorderStroke(OrbitControlTokens.HairlineWidth, palette.hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.sm, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "Find on page...",
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("find_in_page_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$matchIndex/$totalMatches",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("find_in_page_close")
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}
