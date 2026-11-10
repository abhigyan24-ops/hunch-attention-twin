package com.dhyan.app.ui.pet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dhyan.app.ui.theme.DhyanTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real pointer injection against the real overlay; semantic clicks cannot prove pass-through. */
class PetOverlayComposeTest {
    @get:Rule val compose = createComposeRule()

    @Before fun pauseAnimationClock() {
        // The pet intentionally has an ongoing frame loop. Tests advance it explicitly.
        compose.mainClock.autoAdvance = false
    }

    private fun frames(count: Int = 4) {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun pet(emotion: String? = null): SemanticsNodeInteraction =
        compose.onNodeWithContentDescription(
            if (emotion == null) "Pip," else "Pip, $emotion",
            substring = true
        )

    @Test fun initialPassLeavesUnderlyingButtonTouchUnconsumedAndClickable() {
        val clicked = AtomicInteger()
        val consumedAtInitial = AtomicReference<Boolean?>(null)
        compose.setContent {
            DhyanTheme {
                DhyanPetOverlay {
                    Box(Modifier.fillMaxSize()) {
                        Button(onClick = { clicked.incrementAndGet() },
                            modifier = Modifier.testTag("underlying-button").pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let {
                                            consumedAtInitial.set(it.isConsumed)
                                        }
                                    }
                                }
                            }) { Text("Underlying button") }
                    }
                }
            }
        }
        frames()
        compose.onNodeWithTag("underlying-button").performTouchInput { click() }
        frames()
        compose.runOnIdle {
            assertEquals(false, consumedAtInitial.get())
            assertEquals(1, clicked.get())
        }
    }

    @Test fun underlyingLazyColumnStillReceivesScrollGestures() {
        lateinit var list: LazyListState
        compose.setContent {
            DhyanTheme {
                list = rememberLazyListState()
                DhyanPetOverlay {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("underlying-list")) {
                        items(100) { index ->
                            Text("Synthetic row $index", Modifier.fillMaxWidth().height(72.dp))
                        }
                    }
                }
            }
        }
        frames()
        // Center-column swipe is outside the pet's initial right-edge hitbox.
        compose.onNodeWithTag("underlying-list").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(600L)
        frames()
        compose.runOnIdle {
            assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
        }
    }

    @Test fun gentleHeadRubTriggersLoveWithoutDraggingThePet() {
        compose.setContent { DhyanTheme { DhyanPetOverlay { Box(Modifier.fillMaxSize()) } } }
        frames()
        val before = pet().fetchSemanticsNode().boundsInRoot
        val stroke = with(compose.density) { 12.dp.toPx() }
        pet().performTouchInput {
            val start = Offset(center.x, height * .35f)
            down(start)
            moveTo(start + Offset(stroke, 0f), delayMillis = 100L)
            moveTo(start - Offset(stroke, 0f), delayMillis = 100L)
            moveTo(start + Offset(stroke, 0f), delayMillis = 100L)
            moveTo(start - Offset(stroke, 0f), delayMillis = 100L)
            up()
        }
        frames()
        pet("love").assertExists()
        val after = pet().fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, 1f)
        assertEquals(before.top, after.top, 1f)
    }

    @Test fun idleOverlaySleepsAfterEighteenSeconds() {
        compose.setContent { DhyanTheme { DhyanPetOverlay { Box(Modifier.fillMaxSize()) } } }
        frames()
        compose.mainClock.advanceTimeBy(17_000L)
        frames()
        pet("sleepy").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1100L)
        frames()
        pet("sleepy").assertExists()
    }

    @Test fun touchingAnUnderlyingButtonWakesPetAndStillClicks() {
        val clicked = AtomicInteger()
        compose.setContent {
            DhyanTheme {
                DhyanPetOverlay {
                    Box(Modifier.fillMaxSize()) {
                        Button(onClick = { clicked.incrementAndGet() },
                            modifier = Modifier.testTag("wake-button")) { Text("Wake test") }
                    }
                }
            }
        }
        frames()
        compose.mainClock.advanceTimeBy(18_100L)
        frames()
        pet("sleepy").assertExists()
        compose.onNodeWithTag("wake-button").performTouchInput { click() }
        frames()
        pet("sleepy").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, clicked.get()) }
    }

    @Test fun activeTaskPreventsIdleSleepUntilTaskEnds() {
        val active = mutableStateOf(true)
        compose.setContent {
            DhyanTheme { DhyanPetOverlay(active.value) { Box(Modifier.fillMaxSize()) } }
        }
        frames()
        compose.mainClock.advanceTimeBy(20_000L)
        frames()
        pet("focused").assertExists()
        pet("sleepy").assertDoesNotExist()
        compose.runOnIdle { active.value = false }
        frames()
        pet("sleepy").assertExists()
    }

    @Test fun petHitboxConsumesItsOwnTapInsteadOfClickingThrough() {
        val clicked = AtomicInteger()
        compose.setContent {
            DhyanTheme {
                DhyanPetOverlay {
                    Button(onClick = { clicked.incrementAndGet() },
                        modifier = Modifier.fillMaxSize()) { Text("Behind the pet") }
                }
            }
        }
        frames()
        pet().performTouchInput { click() }
        frames()
        pet("happy").assertExists()
        compose.runOnIdle { assertEquals(0, clicked.get()) }
    }

    @Test fun holdAndReleaseMinimizesAndOrbTapRestores() {
        compose.setContent { DhyanTheme { DhyanPetOverlay { Box(Modifier.fillMaxSize()) } } }
        frames()
        pet().performTouchInput {
            down(center)
            advanceEventTime(650L)
            up()
        }
        frames()
        pet().assertDoesNotExist()
        compose.onNodeWithContentDescription("Restore Pip").assertExists()
            .performTouchInput { click() }
        frames()
        compose.onNodeWithContentDescription("Restore Pip").assertDoesNotExist()
        pet().assertExists()
    }
}
