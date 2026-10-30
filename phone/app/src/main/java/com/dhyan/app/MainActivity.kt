package com.dhyan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.ui.DhyanMainScreen
import com.dhyan.app.ui.model.LiveContextHolder
import com.dhyan.app.ui.pet.DhyanPetOverlay
import com.dhyan.app.ui.theme.DhyanTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val snapshot by LiveContextHolder.snapshot.collectAsState()
            val session = snapshot?.declaredSession
            // The existing fused session includes local phone and fresh laptop declarations.
            // This UI-only expiry check never creates a session or records focus/meditation.
            val taskActive by produceState(initialValue = false, key1 = session) {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val currentSession = LiveContextHolder.currentSnapshot()?.declaredSession
                    value = isPetTaskActive(currentSession, now)
                    val endMs = currentSession?.let { it.startedTs.toDouble() + it.plannedMin * 60_000.0 }
                    if (endMs == null || !endMs.isFinite() || now.toDouble() >= endMs) break
                    delay(1000L)
                }
            }
            DhyanTheme {
                DhyanPetOverlay(isTaskActive = taskActive) {
                    DhyanMainScreen()
                }
            }
        }
    }
}

/** Only an explicitly declared, started and unexpired task activates the companion. */
internal fun isPetTaskActive(session: DeclaredSession?, nowMs: Long): Boolean {
    if (session == null || nowMs < 0L || session.startedTs < 0L ||
        nowMs < session.startedTs || !session.plannedMin.isFinite() || session.plannedMin <= 0.0
    ) return false
    val durationMs = session.plannedMin * 60_000.0
    return durationMs.isFinite() && (nowMs - session.startedTs).toDouble() < durationMs
}
