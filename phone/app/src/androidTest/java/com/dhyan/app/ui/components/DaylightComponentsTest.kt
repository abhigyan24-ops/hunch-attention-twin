package com.dhyan.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dhyan.app.ui.theme.DhyanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DaylightComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun capsuleNavigationKeepsAllThreeDestinationsAndSelection() {
        val selected = mutableIntStateOf(0)
        compose.setContent { DhyanTheme { FloatingCapsuleNavBar(selected.intValue, { selected.intValue = it }) } }
        compose.onNodeWithTag("nav-live").performTouchInput { click() }.assertIsSelected()
        compose.onNodeWithTag("nav-insights").performTouchInput { click() }.assertIsSelected()
        compose.onNodeWithTag("nav-simulate").performTouchInput { click() }.assertIsSelected()
    }

    @Test fun dialSupportsAccessibilityProgressAndRealPointerDrag() {
        val value = mutableFloatStateOf(2f)
        compose.setContent { DhyanTheme {
            SemicircularTickDial(value.floatValue, 0f..10f, value.floatValue.toString(), "hours worked today",
                Modifier.testTag("dial"), { value.floatValue = it })
        } }
        compose.onNodeWithTag("dial").performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        compose.runOnIdle { assertEquals(7f, value.floatValue, .001f) }
        compose.onNodeWithTag("dial").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .2f, height * .25f),
                androidx.compose.ui.geometry.Offset(width * .8f, height * .25f))
        }
        compose.runOnIdle { org.junit.Assert.assertTrue(value.floatValue in 6f..10f) }
    }

    @Test fun emptyAnalyticsDoesNotInventTrendsOrACompletionPercentage() {
        compose.setContent { DhyanTheme { Column {
            MiniSparklineCard("Released", "0", emptyList())
            RecordedShareRing(0, 0, "Allowed")
        } } }
        compose.onNodeWithText("No trend history").assertExists()
        compose.onNodeWithText("0%").assertDoesNotExist()
        compose.onNodeWithContentDescription("Allowed: no recorded share").assertExists()
    }
}
