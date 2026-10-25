package com.dhyan.app.ui.viewmodel

import com.dhyan.app.actuator.FakeClock
import com.dhyan.app.data.DecisionDao
import com.dhyan.app.data.DecisionEntity
import com.dhyan.app.data.FeedbackDao
import com.dhyan.app.data.FeedbackEntity
import com.dhyan.app.data.HeldNotifDao
import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.ui.model.PolicySettingsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeHeldNotifDao : HeldNotifDao {
    val items = mutableListOf<HeldNotifEntity>()
    private val activeFlow = MutableStateFlow<List<HeldNotifEntity>>(emptyList())
    private val historyFlow = MutableStateFlow<List<HeldNotifEntity>>(emptyList())

    override suspend fun insert(notif: HeldNotifEntity) {
        items.removeAll { it.keyHash == notif.keyHash }
        items.add(notif)
        updateFlows()
    }
    override suspend fun update(notif: HeldNotifEntity) {
        items.removeAll { it.keyHash == notif.keyHash }
        items.add(notif)
        updateFlows()
    }
    override suspend fun get(keyHash: String): HeldNotifEntity? = items.find { it.keyHash == keyHash }
    override suspend fun dueForRelease(nowMs: Long): List<HeldNotifEntity> =
        items.filter { it.state in listOf("HELD", "SNOOZED") && it.holdUntilMs in 1..nowMs }
    override suspend fun allActive(): List<HeldNotifEntity> = items.filter { it.state in listOf("HELD", "SNOOZED") }
    override fun observeActive(): Flow<List<HeldNotifEntity>> = activeFlow
    override fun observeHistory(limit: Int): Flow<List<HeldNotifEntity>> = historyFlow
    override suspend fun stuckPending(olderThanMs: Long): List<HeldNotifEntity> =
        items.filter { it.state == "PENDING" && it.heldSinceMs < olderThanMs }
    override suspend fun setState(keyHash: String, state: String) {
        items.find { it.keyHash == keyHash }?.let {
            items.remove(it)
            items.add(it.copy(state = state))
            updateFlows()
        }
    }
    override suspend fun setStateWithOverride(keyHash: String, state: String) {
        items.find { it.keyHash == keyHash }?.let {
            items.remove(it)
            items.add(it.copy(state = state, userOverride = true))
            updateFlows()
        }
    }
    override suspend fun deleteExpired(nowMs: Long) {
        items.removeAll { it.expiresAtMs < nowMs && it.state !in listOf("PENDING", "HELD", "SNOOZED") }
        updateFlows()
    }
    override suspend fun countByState(state: String): Int = items.count { it.state == state }

    private fun updateFlows() {
        activeFlow.value = items.filter { it.state in listOf("HELD", "SNOOZED") }
        historyFlow.value = items.filter { it.state !in listOf("PENDING", "HELD", "SNOOZED") }
    }
}

class FakeDecisionDao : DecisionDao {
    val decisions = mutableListOf<DecisionEntity>()
    private val recentFlow = MutableStateFlow<List<DecisionEntity>>(emptyList())

    override suspend fun insert(decision: DecisionEntity): Long {
        decisions.add(decision)
        recentFlow.value = decisions.reversed()
        return decisions.size.toLong()
    }
    override suspend fun recent(limit: Int): List<DecisionEntity> = decisions.takeLast(limit).reversed()
    override fun observeRecent(limit: Int): Flow<List<DecisionEntity>> = recentFlow
    override suspend fun since(sinceMs: Long): List<DecisionEntity> = decisions.filter { it.ts >= sinceMs }
    override suspend fun updateActuationStatus(keyHash: String, postedTsMs: Long, status: String) {
        val idx = decisions.indexOfFirst { it.keyHash == keyHash && it.postedTsMs == postedTsMs }
        if (idx >= 0) {
            decisions[idx] = decisions[idx].copy(actuationStatus = status)
            recentFlow.value = decisions.reversed()
        }
    }
    override suspend fun countByAction(action: String): Int = decisions.count { it.action == action }
    override suspend fun countByStatus(status: String): Int = decisions.count { it.actuationStatus == status }
    override suspend fun deleteOlderThan(beforeMs: Long) {
        decisions.removeAll { it.ts < beforeMs }
        recentFlow.value = decisions.reversed()
    }
}

class FakeFeedbackDao : FeedbackDao {
    val recorded = mutableListOf<FeedbackEntity>()
    override suspend fun insert(feedback: FeedbackEntity): Long {
        recorded.add(feedback)
        return recorded.size.toLong()
    }
    override suspend fun forDecision(decisionId: Long): List<FeedbackEntity> =
        recorded.filter { it.decisionId == decisionId }
}

class LiveDigestViewModelTest {

    private lateinit var heldDao: FakeHeldNotifDao
    private lateinit var decisionDao: FakeDecisionDao
    private lateinit var feedbackDao: FakeFeedbackDao
    private lateinit var clock: FakeClock
    private lateinit var viewModel: LiveDigestViewModel

    @Before
    fun setup() {
        heldDao = FakeHeldNotifDao()
        decisionDao = FakeDecisionDao()
        feedbackDao = FakeFeedbackDao()
        clock = FakeClock(1_000_000L)
        PolicySettingsHolder.setMode(PolicyMode.OFF)
        PolicySettingsHolder.setKillSwitch(false)
        viewModel = LiveDigestViewModel(
            heldNotifDao = heldDao,
            decisionDao = decisionDao,
            feedbackDao = feedbackDao,
            clock = clock,
            isFakeProvider = true,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun `initial state defaults to OFF mode and fake provider`() {
        val state = viewModel.uiState.value
        assertEquals(PolicyMode.OFF, state.mode)
        assertFalse(state.killSwitchActive)
        assertTrue(state.isFakeProvider)
        assertTrue(state.heldQueue.isEmpty())
        assertTrue(state.decisionFeed.isEmpty())
    }

    @Test
    fun `showNow updates state to RELEASED with user override`() = runBlocking {
        val entity = HeldNotifEntity(
            keyHash = "kh-1", mechanism = "CANCEL_DIGEST", state = "HELD",
            appCat = "social", notifCat = "post", senderHash = null,
            postedTsMs = 900_000L, heldSinceMs = 900_000L, holdUntilMs = 1_200_000L,
            expiresAtMs = 5_000_000L, ciphertext = null, iv = null,
            platformId = 1, pkgHash = "pkg", decisionAction = "DIGEST", propensity = 0.5
        )
        heldDao.insert(entity)

        viewModel.showNow("kh-1")

        val updated = heldDao.get("kh-1")
        assertNotNull(updated)
        assertEquals("RELEASED", updated!!.state)
        assertTrue(updated.userOverride)
    }

    @Test
    fun `markImportant records feedback and releases notification`() = runBlocking {
        val entity = HeldNotifEntity(
            keyHash = "kh-imp", mechanism = "CANCEL_DIGEST", state = "HELD",
            appCat = "work", notifCat = "msg", senderHash = null,
            postedTsMs = 900_000L, heldSinceMs = 900_000L, holdUntilMs = 1_200_000L,
            expiresAtMs = 5_000_000L, ciphertext = null, iv = null,
            platformId = 2, pkgHash = "pkg", decisionAction = "DELAY", propensity = 0.8
        )
        heldDao.insert(entity)

        viewModel.markImportant("kh-imp", decisionId = 42L)

        assertEquals(1, feedbackDao.recorded.size)
        assertEquals("override_allow", feedbackDao.recorded[0].type)
        assertEquals(42L, feedbackDao.recorded[0].decisionId)
        val updated = heldDao.get("kh-imp")
        assertEquals("RELEASED", updated!!.state)
    }

    @Test
    fun `markNoise records feedback without releasing`() = runBlocking {
        viewModel.markNoise("kh-noise", decisionId = 99L)
        assertEquals(1, feedbackDao.recorded.size)
        assertEquals("thumbs_down", feedbackDao.recorded[0].type)
        assertEquals(99L, feedbackDao.recorded[0].decisionId)
    }

    @Test
    fun `toggleKillSwitch alters policy settings holder`() {
        assertFalse(PolicySettingsHolder.get().killSwitch)
        viewModel.toggleKillSwitch()
        assertTrue(PolicySettingsHolder.get().killSwitch)
        viewModel.toggleKillSwitch()
        assertFalse(PolicySettingsHolder.get().killSwitch)
    }

    @Test
    fun `setMode updates policy mode`() {
        viewModel.setMode(PolicyMode.LIVE)
        assertEquals(PolicyMode.LIVE, PolicySettingsHolder.get().mode)
        viewModel.setMode(PolicyMode.DRY_RUN)
        assertEquals(PolicyMode.DRY_RUN, PolicySettingsHolder.get().mode)
    }
}
