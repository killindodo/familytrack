package com.killindodo.familytrack.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun locationFixHoldsValues() {
        val fix = LocationFix(12.9716, 77.5946, 8.0, 0.0, 87, 1000L)
        assertEquals(87, fix.batteryPct)
        assertEquals("SOS", AlertType.SOS.name)
    }
}
