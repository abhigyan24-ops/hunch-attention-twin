package com.dhyan.app.ui.viewmodel

import com.dhyan.app.data.DecisionEntity
import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.data.VipDao
import com.dhyan.app.data.VipEntity
import com.dhyan.app.ingest.AppCategorizer
import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.ActuationStatus
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.ui.model.PolicySettingsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeVipDao : VipDao {
    val items = mutableListOf<VipEntity>()

    override suspend fun insert(vip: VipEntity) {
        items.removeAll { it.senderHash == vip.senderHash }
        items.add(vip)
    }

    override suspend fun delete(hash: String) {
        items.removeAll { it.senderHash == hash }
    }

    override suspend fun allHashes(): List<String> = items.map { it.senderHash }
}

class SettingsAndInsightsViewModelTest {

    private lateinit var vipDao: FakeVipDao
    private lateinit var decisionDao: FakeDecisionDao
    private lateinit var heldNotifDao: FakeHeldNotifDao

    @Before
    fun setUp() {
        vipDao = FakeVipDao()
        decisionDao = FakeDecisionDao()
        heldNotifDao = FakeHeldNotifDao()
        PolicySettingsHolder.setMode(PolicyMode.OFF)
    }

    @Test
    fun testSettingsPolicyModeTransitionRequiresConfirmationForLive() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val vm = SettingsViewModel(vipDao = vipDao, coroutineScope = testScope)

        assertEquals(PolicyMode.OFF, vm.uiState.value.settings.mode)

        // Switch to DRY_RUN does not require confirmation
        vm.requestModeChange(PolicyMode.DRY_RUN)
        assertEquals(PolicyMode.DRY_RUN, vm.uiState.value.settings.mode)
        assertFalse(vm.uiState.value.showLiveConfirmDialog)

        // Switch to LIVE requires confirmation modal
        vm.requestModeChange(PolicyMode.LIVE)
        assertTrue(vm.uiState.value.showLiveConfirmDialog)
        assertEquals(PolicyMode.LIVE, vm.uiState.value.pendingMode)
        // Remains DRY_RUN until confirmed
        assertEquals(PolicyMode.DRY_RUN, vm.uiState.value.settings.mode)

        // Dismiss keeps previous mode
        vm.dismissLiveDialog()
        assertFalse(vm.uiState.value.showLiveConfirmDialog)
        assertEquals(PolicyMode.DRY_RUN, vm.uiState.value.settings.mode)

        // Request LIVE again and confirm
        vm.requestModeChange(PolicyMode.LIVE)
        vm.confirmLiveMode()
        assertFalse(vm.uiState.value.showLiveConfirmDialog)
        assertEquals(PolicyMode.LIVE, vm.uiState.value.settings.mode)
    }

    @Test
    fun testSettingsTogglesAndParameters() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val vm = SettingsViewModel(vipDao = vipDao, coroutineScope = testScope)

        vm.setCautionMultiplier(1.5)
        assertEquals(1.5, vm.uiState.value.settings.kappaDPerHour, 0.0001)

        vm.setExplorationEnabled(false)
        assertFalse(vm.uiState.value.settings.explorationEnabled)

        vm.setDigestIntervalMin(45)
        assertEquals(45, vm.uiState.value.settings.digestIntervalMin)

        val pkg = "com.google.android.gm"
        assertFalse(vm.uiState.value.settings.allowListPackages.contains(pkg))
        vm.toggleAllowListPackage(pkg)
        assertTrue(vm.uiState.value.settings.allowListPackages.contains(pkg))
        vm.toggleAllowListPackage(pkg)
        assertFalse(vm.uiState.value.settings.allowListPackages.contains(pkg))
    }

    @Test
    fun testVipSenderPrivacyHashing() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val salt = "test_salt_secret"
        val vm = SettingsViewModel(vipDao = vipDao, salt = salt, coroutineScope = testScope)

        val rawName = "Alice Smith"
        vm.addVipSender(rawName)

        val expectedHash = AppCategorizer.senderHash(rawName, salt)
        assertEquals(1, vipDao.items.size)
        val stored = vipDao.items[0]

        // CRITICAL PRIVACY CONTRACT: The stored hash must match the salted hash, be 16-hex chars,
        // and must NOT contain the raw name anywhere.
        assertEquals(expectedHash, stored.senderHash)
        assertEquals(16, stored.senderHash.length)
        assertFalse(stored.senderHash.contains("Alice"))
        assertFalse(stored.senderHash.contains("Smith"))
        assertTrue(vm.uiState.value.vipHashes.contains(expectedHash))

        // Remove sender
        vm.removeVipSender(expectedHash)
        assertTrue(vipDao.items.isEmpty())
        assertFalse(vm.uiState.value.vipHashes.contains(expectedHash))
    }

    @Test
    fun testInsightsViewModelCounts() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)

        decisionDao.insert(createDecision("k1", Action.ALLOW.name, null))
        decisionDao.insert(createDecision("k2", Action.ALLOW.name, null))
        decisionDao.insert(createDecision("k3", Action.DIGEST.name, ActuationStatus.HELD.name))
        decisionDao.insert(createDecision("k4", Action.DELAY.name, ActuationStatus.SNOOZED.name))
        decisionDao.insert(createDecision("k5", Action.SILENT.name, ActuationStatus.HELD.name))

        heldNotifDao.insert(createHeld("h1", ActuationStatus.HELD.name))
        heldNotifDao.insert(createHeld("h2", ActuationStatus.HELD.name))
        heldNotifDao.insert(createHeld("h3", ActuationStatus.SNOOZED.name))
        heldNotifDao.insert(createHeld("h4", ActuationStatus.RELEASED.name))
        heldNotifDao.insert(createHeld("h5", ActuationStatus.FAILED_OPEN.name))
        heldNotifDao.insert(createHeld("h6", ActuationStatus.ACTUATION_UNKNOWN.name))

        val insightsVm = InsightsViewModel(
            decisionDao = decisionDao,
            heldNotifDao = heldNotifDao,
            coroutineScope = testScope
        )

        insightsVm.refresh()
        val state = insightsVm.uiState.value

        assertEquals(2, state.allowCount)
        assertEquals(1, state.delayCount)
        assertEquals(1, state.digestCount)
        assertEquals(1, state.silentCount)
        assertEquals(5, state.totalDecisions)

        assertEquals(2, state.heldCount)
        assertEquals(1, state.snoozedCount)
        assertEquals(1, state.releasedCount)
        assertEquals(1, state.failedOpenCount)
        assertEquals(1, state.unknownCount)
    }

    private fun createDecision(key: String, action: String, status: String?) = DecisionEntity(
        ts = 1000L,
        keyHash = key,
        postedTsMs = 1000L,
        action = action,
        reasonCode = "test",
        safetyReason = null,
        pLossShow = 0.1,
        pLossSuppress = 0.5,
        deltaLMean = 0.4,
        deltaLP10 = 0.2,
        deltaLP90 = 0.6,
        evidenceStrength = 0.8,
        propensity = 0.5,
        pShow = 0.5,
        exploration = false,
        failedOpen = false,
        importance = 0.5,
        modelVersion = 1L,
        policyVersion = "1.0",
        mode = "LIVE",
        actuationStatus = status
    )

    private fun createHeld(key: String, state: String) = HeldNotifEntity(
        keyHash = key,
        mechanism = "CANCEL_DIGEST",
        state = state,
        appCat = "comm",
        notifCat = "msg",
        senderHash = null,
        postedTsMs = 1000L,
        heldSinceMs = 1000L,
        holdUntilMs = 2000L,
        expiresAtMs = 5000L,
        ciphertext = "cipher",
        iv = "iv",
        platformId = 1,
        pkgHash = "pkg",
        decisionAction = "DIGEST",
        propensity = 0.5
    )
}
