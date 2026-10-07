package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRendererRehydrationPolicyTest {
    @Test fun foregroundVisiblePlayerRecoversInPlace() {
        assertEquals(
            PlayerRendererRecoveryAction.REHYDRATE_NOW,
            PlayerRendererRehydrationPolicy.resolve(
                PlayerRendererRecoveryContext(false, false, true, false, true, true, true)
            )
        )
    }

    @Test fun backgroundRecoveryIsDeferred() {
        assertEquals(
            PlayerRendererRecoveryAction.DEFER_UNTIL_FOREGROUND,
            PlayerRendererRehydrationPolicy.resolve(
                PlayerRendererRecoveryContext(false, false, false, false, true, true, true)
            )
        )
    }

    @Test fun hiddenIdlePlayerIsLazy() {
        assertEquals(
            PlayerRendererRecoveryAction.DEFER_UNTIL_NEEDED,
            PlayerRendererRehydrationPolicy.resolve(
                PlayerRendererRecoveryContext(false, false, true, false, true, false, false)
            )
        )
    }

    @Test fun crashLoopRequiresManualRetry() {
        assertEquals(
            PlayerRendererRecoveryAction.WAIT_FOR_MANUAL_RETRY,
            PlayerRendererRehydrationPolicy.resolve(
                PlayerRendererRecoveryContext(true, false, true, false, true, true, true)
            )
        )
    }
    @Test fun visiblePipRecoversEvenWhenActivityPaused() {
        assertEquals(
            PlayerRendererRecoveryAction.REHYDRATE_NOW,
            PlayerRendererRehydrationPolicy.resolve(
                PlayerRendererRecoveryContext(false, false, false, true, true, true, true)
            )
        )
    }

}
