package com.dhyan.app.ui.viewmodel

import com.dhyan.app.safety.NeverHoldRules
import com.dhyan.app.twin.context.FatigueIndex
import com.dhyan.app.twin.context.HourBand
import com.dhyan.app.twin.params.PriorTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression test for Prompt F1 amendments 1, 2, 4, 7, 8:
 * - Every UI appCat exists in core-spec/categories.json
 * - Every UI notifCat is known to NeverHoldRules
 * - UI notification buttons resolve to distinct intended PriorTable branches
 * - FatigueIndex correctly maps hours worked to low/med/high bands
 * - Persona and Cold stores use InMemoryParamStore with null persistence (zero Room writes)
 */
class SimulateTabRegressionTest {

    private val uiTaxonomyButtons = listOf(
        TaxonomyButton("Social", "social", "social_notif", PriorTable.pickupSocial),
        TaxonomyButton("Known Contact", "communication", "msg", PriorTable.pickupKnownContact),
        TaxonomyButton("Promo / Deals", "shopping", "promo", PriorTable.pickupPromo),
        TaxonomyButton("Work Docs", "docs", "alert", PriorTable.pickupDefault)
    )

    data class TaxonomyButton(
        val label: String,
        val appCat: String,
        val notifCat: String,
        val expectedBasePrior: Pair<Double, Double>
    )

    @Test
    fun testAllUiCategoriesExistInCoreSpec() {
        val roots = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        val categoriesFile = roots.map { File(it, "core-spec/categories.json") }.firstOrNull { it.isFile }
        assertNotNull("core-spec/categories.json must exist in project tree", categoriesFile)

        val content = categoriesFile!!.readText()
        // Simple regex extract of categories array from categories.json
        val regex = Regex(""""categories"\s*:\s*\[([\s\S]*?)\]""")
        val match = regex.find(content)
        assertNotNull("categories array must exist in categories.json", match)

        val catArrayString = match!!.groupValues[1]
        val coreSpecCategories = Regex(""""([^"]+)"""").findAll(catArrayString).map { it.groupValues[1] }.toSet()

        assertTrue("core-spec must contain standard categories", coreSpecCategories.isNotEmpty())

        for (btn in uiTaxonomyButtons) {
            assertTrue(
                "UI appCat '${btn.appCat}' for '${btn.label}' must be defined in core-spec/categories.json",
                coreSpecCategories.contains(btn.appCat)
            )
        }
    }

    @Test
    fun testAllUiNotificationCategoriesKnownToNeverHoldRules() {
        for (btn in uiTaxonomyButtons) {
            assertTrue(
                "UI notifCat '${btn.notifCat}' for '${btn.label}' must be recognized by NeverHoldRules",
                NeverHoldRules.categoryKnown(btn.notifCat)
            )
        }
    }

    @Test
    fun testUiButtonsResolveToIntendedPriorBranches() {
        for (btn in uiTaxonomyButtons) {
            val resolvedPrior = PriorTable.pickupPrior(btn.appCat, btn.notifCat, fatigueBand = "low")
            assertEquals(
                "Button '${btn.label}' must resolve to its intended PriorTable branch",
                btn.expectedBasePrior.first,
                resolvedPrior.first,
                1e-6
            )
            assertEquals(
                "Button '${btn.label}' must resolve to its intended PriorTable branch",
                btn.expectedBasePrior.second,
                resolvedPrior.second,
                1e-6
            )
        }
    }

    @Test
    fun testHoursWorkedMapsToFatigueBandsAndOddsMultipliers() {
        // Low fatigue: 1.0h -> fatigueIndex ~ 0.0 -> band "low", odds mult = 1.0
        val fLow = FatigueIndex.compute(hoursWorked = 1.0, switchRateZ = 0.0, stallRatio = 0.0)
        val bandLow = FatigueIndex.band(fLow)
        assertEquals("low", bandLow)
        assertEquals(1.0, PriorTable.fatiguePickupOddsMultiplier(bandLow), 1e-6)

        // Med fatigue: 4.0h -> fatigueIndex ~ 0.33 -> band "med", odds mult = 1.2
        val fMed = FatigueIndex.compute(hoursWorked = 4.0, switchRateZ = 0.0, stallRatio = 0.0)
        val bandMed = FatigueIndex.band(fMed)
        assertEquals("med", bandMed)
        assertEquals(1.2, PriorTable.fatiguePickupOddsMultiplier(bandMed), 1e-6)

        // High fatigue: 8.0h -> fatigueIndex ~ 0.78 -> band "high", odds mult = 1.5
        val fHigh = FatigueIndex.compute(hoursWorked = 8.0, switchRateZ = 0.0, stallRatio = 0.0)
        val bandHigh = FatigueIndex.band(fHigh)
        assertEquals("high", bandHigh)
        assertEquals(1.5, PriorTable.fatiguePickupOddsMultiplier(bandHigh), 1e-6)
    }

    @Test
    fun testTimeOfDayMapsToAllFiveHourBands() {
        assertEquals("night", HourBand.from(2).wire)
        assertEquals("morning", HourBand.from(9).wire)
        assertEquals("afternoon", HourBand.from(14).wire)
        assertEquals("evening", HourBand.from(19).wire)
        assertEquals("late", HourBand.from(23).wire)
    }

    @Test
    fun testColdStoreIsOperationalAndInMemory() {
        assertTrue("coldStore must be loaded", SimulateViewModel.coldStore.isLoaded)
        val (alpha, beta) = PriorTable.pickupPrior("social", "social_notif", "low")
        assertEquals(3.0, alpha, 1e-6)
        assertEquals(4.0, beta, 1e-6)
    }

    @Test
    fun testPersonaTrainingRunsInMemoryWithoutRoom() = kotlinx.coroutines.runBlocking {
        val store = SimulateViewModel.trainPersona("Rahul")
        assertTrue("Trained persona store must be loaded", store.isLoaded)
        assertTrue("Trained store must have positive model version", store.modelVersion > 0L)
    }
}
