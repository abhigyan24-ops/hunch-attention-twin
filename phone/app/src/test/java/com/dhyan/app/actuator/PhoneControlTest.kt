package com.dhyan.app.actuator

import com.dhyan.app.data.HeldNotifDao
import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.twin.decision.ActuationStatus
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.ui.model.LiveContextHolder
import com.dhyan.app.ui.model.PolicySettingsHolder
import com.dhyan.app.ui.viewmodel.FakeHeldNotifDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PhoneControlTest {

    private lateinit var heldStore: InMemoryHeldStore
    private lateinit var clock: FakeClock
    private lateinit var control: PhoneControlImpl

    @Before
    fun setUp() {
        heldStore = InMemoryHeldStore()
        clock = FakeClock(1_000_000L)
        control = PhoneControlImpl(heldStore = heldStore, clock = clock)
        LiveContextHolder.reset()
        PolicySettingsHolder.setKillSwitch(false)
    }

    @Test
    fun testShowNowSingleItem() = runBlocking {
        val entity = createHeld("kh_single", ActuationStatus.HELD.name)
        heldStore.insert(entity)

        val res = control.showNow(heldId = "kh_single")
        assertTrue(res is ControlResult.Applied)

        val updated = heldStore.get("kh_single")
        assertNotNull(updated)
        assertEquals(ActuationStatus.RELEASED.name, updated!!.state)
        assertTrue(updated.userOverride)
    }

    @Test
    fun testShowNowMissingItem() = runBlocking {
        val res = control.showNow(heldId = "kh_unknown")
        assertTrue(res is ControlResult.Rejected)
        assertEquals("not_found", (res as ControlResult.Rejected).reason)
    }

    @Test
    fun testShowNowAllItems() = runBlocking {
        heldStore.insert(createHeld("kh_1", ActuationStatus.HELD.name))
        heldStore.insert(createHeld("kh_2", ActuationStatus.SNOOZED.name))

        val res = control.showNow(all = true)
        assertTrue(res is ControlResult.Applied)

        assertEquals(ActuationStatus.RELEASED.name, heldStore.get("kh_1")!!.state)
        assertEquals(ActuationStatus.RELEASED.name, heldStore.get("kh_2")!!.state)
    }

    @Test
    fun testSetKillSwitch() = runBlocking {
        assertFalse(PolicySettingsHolder.get().killSwitch)

        val resOn = control.setKillSwitch(true)
        assertTrue(resOn is ControlResult.Applied)
        assertTrue(PolicySettingsHolder.get().killSwitch)

        val resOff = control.setKillSwitch(false)
        assertTrue(resOff is ControlResult.Applied)
        assertFalse(PolicySettingsHolder.get().killSwitch)
    }

    @Test
    fun testDeclareSessionValidationAndApplication() = runBlocking {
        // Validation: out of bounds plannedMin
        val tooShort = control.declareSession("coding", 4.0, strict = true)
        assertTrue(tooShort is ControlResult.Rejected)

        val tooLong = control.declareSession("coding", 500.0, strict = true)
        assertTrue(tooLong is ControlResult.Rejected)

        // Valid session
        val valid = control.declareSession("coding", 120.0, strict = true)
        assertTrue(valid is ControlResult.Applied)

        val snapshot = LiveContextHolder.currentSnapshot()
        assertNotNull(snapshot)
        assertEquals(TaskSource.DECLARED, snapshot!!.taskSource)
        assertNotNull(snapshot.declaredSession)
        assertEquals(TaskType.CODING, snapshot.declaredSession!!.task)
        assertEquals(120.0, snapshot.declaredSession!!.plannedMin, 0.001)
        assertTrue(snapshot.declaredSession!!.strict)

        // End session
        val ended = control.endSession()
        assertTrue(ended is ControlResult.Applied)
        val endedSnapshot = LiveContextHolder.currentSnapshot()
        assertNotNull(endedSnapshot)
        assertNull(endedSnapshot!!.declaredSession)
        assertEquals(TaskSource.INFERRED, endedSnapshot.taskSource)
    }

    @Test
    fun testHeldItemsViewPrivacyAndFlow() = runBlocking {
        val fakeDao = FakeHeldNotifDao()
        val view: HeldItemsView = RoomHeldItemsView(fakeDao)

        fakeDao.insert(createHeld("kh_view1", ActuationStatus.HELD.name, appCat = "comm"))
        fakeDao.insert(createHeld("kh_view2", ActuationStatus.SNOOZED.name, appCat = "social"))

        val summaries = view.currentHeldItems()
        assertEquals(2, summaries.size)

        val item1 = summaries.first { it.heldId == "kh_view1" }
        assertEquals("comm", item1.appCat)
        assertEquals(ActuationStatus.HELD, item1.status)
        assertEquals(1000L, item1.heldAt)
        assertEquals(5000L, item1.expiresAt)

        // Privacy assertion: HeldItemSummary class does NOT have title, text, or sender fields
        val fields = HeldItemSummary::class.java.declaredFields.map { it.name }
        assertFalse(fields.contains("title"))
        assertFalse(fields.contains("text"))
        assertFalse(fields.contains("ciphertext"))
        assertFalse(fields.contains("sender"))
    }

    private fun createHeld(
        key: String,
        state: String,
        appCat: String = "comm"
    ) = HeldNotifEntity(
        keyHash = key,
        mechanism = "CANCEL_DIGEST",
        state = state,
        appCat = appCat,
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
