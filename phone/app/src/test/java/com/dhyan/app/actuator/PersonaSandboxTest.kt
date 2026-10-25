package com.dhyan.app.actuator

import com.dhyan.app.personas.PersonaCatalog
import com.dhyan.app.twin.SimOutcome
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import org.junit.Assert.*
import org.junit.Test

class PersonaSandboxTest {

    private fun makeSnapshot() = ContextSnapshot(
        ts = 1_000_000L,
        focusState = FocusState.DEEP_FOCUS,
        taskType = TaskType.CODING,
        taskSource = TaskSource.DECLARED,
        confidence = 0.9,
        hourBand = "afternoon",
        minutesInState = 45.0,
        fatigueBand = "low",
        recentInterrupts30m = 0,
        laptopState = LaptopState.FLOWING,
        phoneState = PhoneState.SCREEN_OFF,
        secondScreenOverlap = true,
        declaredSession = null
    )

    private fun makeNotif(appCat: String = "social") = NotificationInput(
        appCat = appCat,
        notifCat = "post",
        isVip = false,
        urgencyScore = 0.3,
        repeatCount5m = 0
    )

    @Test
    fun `simulate is deterministic with same seed`() {
        val sandbox = PersonaSandbox(clock = { 1_000_000L })
        val s = makeSnapshot()
        val n = makeNotif()

        val r1 = sandbox.simulate(s, n, seed = 42L)
        val r2 = sandbox.simulate(s, n, seed = 42L)

        assertTrue(r1 is SimOutcome.Ready)
        assertTrue(r2 is SimOutcome.Ready)

        val res1 = (r1 as SimOutcome.Ready).result
        val res2 = (r2 as SimOutcome.Ready).result

        assertEquals(res1.show.mean, res2.show.mean, 1e-9)
        assertEquals(res1.suppress.mean, res2.suppress.mean, 1e-9)
        assertEquals(res1.deltaLMean, res2.deltaLMean, 1e-9)
    }

    @Test
    fun `fastForward updates sandbox pickup distribution`() {
        val sandbox = PersonaSandbox(clock = { 1_000_000L })
        val s = makeSnapshot()
        val n = makeNotif("social")

        val (alphaBefore, betaBefore) = sandbox.resolvePickupBeta(s, n)

        val rahul = PersonaCatalog.byName("Rahul")
        sandbox.fastForward(rahul, days = 14)

        val (alphaAfter, betaAfter) = sandbox.resolvePickupBeta(s, n)

        // After 14 days of training, alpha and beta should have accumulated observations
        assertTrue("Alpha should increase with training", alphaAfter > alphaBefore)
        assertTrue("Beta should increase with training", betaAfter > betaBefore)
    }

    @Test
    fun `sandbox isolation - two independent sandboxes do not leak state`() {
        val s1 = PersonaSandbox(clock = { 1_000_000L })
        val s2 = PersonaSandbox(clock = { 1_000_000L })
        val snap = makeSnapshot()
        val notif = makeNotif("social")

        val rahul = PersonaCatalog.byName("Rahul")
        s1.fastForward(rahul, days = 14)

        val (a1, _) = s1.resolvePickupBeta(snap, notif)
        val (a2, _) = s2.resolvePickupBeta(snap, notif)

        assertTrue("Trained sandbox has higher alpha: a1=$a1 vs a2=$a2", a1 > a2)
        assertTrue("Untrained sandbox retains cold prior alpha: a2=$a2", a2 > 0.0)
    }

    @Test
    fun `no hardcoded pitch numbers in simulation output`() {
        val sandbox = PersonaSandbox(clock = { 1_000_000L })
        val snap = makeSnapshot()
        val notif = makeNotif()

        val outcome = sandbox.simulate(snap, notif, seed = 12345L)
        assertTrue(outcome is SimOutcome.Ready)
        val res = (outcome as SimOutcome.Ready).result

        // Ensure result is within plausible range and not a hardcoded pitch constant
        assertTrue(res.show.mean > 0.0)
        assertTrue(res.suppress.mean >= 0.0)
        assertNotEquals(24.0, res.show.mean, 1e-6)
    }
}
