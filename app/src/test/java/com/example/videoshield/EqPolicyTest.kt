package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class EqPolicyTest {
    @Test fun mapsCurveToDeviceBandsUsingLogFrequencyAndClampsGain() {
        val curve = intArrayOf(6, 4, 0, -2, -3)
        assertEquals(6.0, EqPolicy.gainAt(60.0, curve), 0.0001)
        assertEquals(5.0, EqPolicy.gainAt(sqrt(60.0 * 230.0), curve), 0.0001)
        assertEquals(-3.0, EqPolicy.gainAt(20000.0, curve), 0.0001)
        assertEquals(12.0, EqPolicy.gainAt(20.0, intArrayOf(99, 0, 0, 0, 0)), 0.0001)
    }

    @Test fun providesHeadroomOnlyWhenBoosting() {
        assertEquals(1f, EqPolicy.headroom(intArrayOf(0, 0, 0, 0, 0)), 0.0001f)
        assertEquals(1f, EqPolicy.headroom(intArrayOf(-6, -4, -2, -1, -3)), 0.0001f)
        assertEquals(0.501187f, EqPolicy.headroom(intArrayOf(6, 4, 0, -2, -3)), 0.0001f)
    }
}
