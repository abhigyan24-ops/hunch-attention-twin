package com.dhyan.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DistributionCanvasTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logNormalCurveRendersThroughItsRightEndpoint() {
        compose.setContent {
            LogNormalDistributionCanvas(
                mu = 2.0, sigma = 0.7, lineColor = Color.Blue, fillColor = Color.Cyan,
                modifier = Modifier.size(240.dp, 120.dp).testTag("distribution")
            )
        }
        // Forces actual drawing: the old 80-element array crashed at index 80.
        val image = compose.onNodeWithTag("distribution").captureToImage()
        assertTrue(image.width > 0)
        assertTrue(kotlin.math.abs(image.width - image.height * 2) <= 2)
    }
}
