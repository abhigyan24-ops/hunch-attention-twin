package com.dhyan.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class HunchFlowWidgetProviderTest {
    @Test fun rendersActiveShieldAndPeakWindow() {
        assertEquals(
            WidgetDisplayModel("95m Flow Today", "Shield ON • 3 held", "Peak Window: 10:30 AM"),
            HunchFlowWidgetProvider.formatWidgetState(true, 95, 3, "10:30 AM"),
        )
    }

    @Test fun rendersIdleAndHeldAlertsSeparately() {
        assertEquals(
            WidgetDisplayModel("0m Flow Today", "Shield off", "Peak window unavailable"),
            HunchFlowWidgetProvider.formatWidgetState(false, 0, 0, ""),
        )
        assertEquals("Shield off • 2 held", HunchFlowWidgetProvider.formatWidgetState(false, 0, 2, "").shield)
    }
}
