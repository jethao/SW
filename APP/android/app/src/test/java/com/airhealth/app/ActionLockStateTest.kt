package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionLockStateTest {
    @Test
    fun setupBlocksMeasureUntilTheCurrentFlowResolves() {
        val setupLockedState = ActionLockState().tryAcquire(
            feature = FeatureKind.ORAL_HEALTH,
            action = ManagedAction.SETUP,
        )

        val blockedMeasureState = setupLockedState.tryAcquire(
            feature = FeatureKind.ORAL_HEALTH,
            action = ManagedAction.MEASURE,
        )

        assertEquals(ManagedAction.SETUP, blockedMeasureState.activeAction)
        assertEquals(
            ActionLockReasonCode.CONFLICTING_ACTION_IN_PROGRESS,
            blockedMeasureState.blockedAttempt?.reasonCode,
        )

        val releasedState = blockedMeasureState.release().tryAcquire(
            feature = FeatureKind.ORAL_HEALTH,
            action = ManagedAction.MEASURE,
        )

        assertEquals(ManagedAction.MEASURE, releasedState.activeAction)
        assertNull(releasedState.blockedAttempt)
    }

    @Test
    fun entitlementBlockPreservesExistingLockContext() {
        val setupLockedState = ActionLockState().tryAcquire(
            feature = FeatureKind.FAT_BURNING,
            action = ManagedAction.SETUP,
        )

        val blockedState = setupLockedState.blockByEntitlement(
            feature = FeatureKind.FAT_BURNING,
            action = ManagedAction.MEASURE,
            reasonCode = ActionLockReasonCode.TEMPORARY_ACCESS_RESTRICTION,
        )

        assertEquals(ManagedAction.SETUP, blockedState.activeAction)
        assertEquals(
            ActionLockReasonCode.TEMPORARY_ACCESS_RESTRICTION,
            blockedState.blockedAttempt?.reasonCode,
        )
    }
}
