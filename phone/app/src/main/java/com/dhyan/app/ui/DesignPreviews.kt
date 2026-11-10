package com.dhyan.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dhyan.app.ui.components.*
import com.dhyan.app.ui.brand.HunchBrand
import com.dhyan.app.ui.model.ActionVerdict
import com.dhyan.app.ui.model.AttentionState
import com.dhyan.app.ui.theme.DhyanTheme

// Presentation-only previews: no Room, network, trained store or live ViewModels.
@Preview(name = "Calm light", showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun LightDesignPreview() = DesignPreview(false)

@Preview(name = "Calm dark", showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun DarkDesignPreview() = DesignPreview(true)

@Preview(name = "Large text", showBackground = true, widthDp = 390, heightDp = 900, fontScale = 1.5f)
@Composable
private fun LargeTextDesignPreview() = DesignPreview(false)

@Composable
private fun DesignPreview(dark: Boolean) {
    DhyanTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(HunchBrand.PRODUCT_NAME, style = MaterialTheme.typography.titleLarge)
                Text("Make room\nfor focus.", style = MaterialTheme.typography.headlineLarge)
                Text("Synthetic preview · not live data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FocusRing(AttentionState.WORKING, 12)
                VerdictCard(ActionVerdict.ALLOW, "Example explanation for a synthetic preview.")
                ContextChip("Coding", true, {})
            }
        }
    }
}
