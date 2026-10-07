package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSurfaceStatePolicyTest {
    @Test fun restoreMiniDoesNotDependOnCurrentVisibility() {
        assertEquals(PlayerSurfaceState.MINI, PlayerSurfaceStatePolicy.restore("MINI"))
        assertEquals(PlayerSurfaceState.EXPANDED, PlayerSurfaceStatePolicy.restore("EXPANDED"))
        assertEquals(PlayerSurfaceState.HIDDEN, PlayerSurfaceStatePolicy.restore("garbage"))
    }

    @Test fun rapidSurfaceTransitionsRemainDeterministic() {
        var state = PlayerSurfaceState.HIDDEN
        repeat(20_000) { index ->
            val action = when (index % 5) {
                0 -> PlayerSurfaceAction.EXPAND
                1 -> PlayerSurfaceAction.MINIMIZE
                2 -> PlayerSurfaceAction.EXPAND
                3 -> PlayerSurfaceAction.HIDE
                else -> PlayerSurfaceAction.MINIMIZE
            }
            state = PlayerSurfaceStatePolicy.next(state, action)
            assertTrue(state in PlayerSurfaceState.values())
        }
        assertEquals(PlayerSurfaceState.HIDDEN, state)
    }
}
