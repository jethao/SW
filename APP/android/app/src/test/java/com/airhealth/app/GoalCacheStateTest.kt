package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalCacheStateTest {
    @Test
    fun upsertCreatesAndUpdatesGoalPerFeature() {
        val created = GoalCacheState().upsert(
            feature = FeatureKind.ORAL_HEALTH,
            summary = "Measure every morning",
            targetLabel = "7 morning checks",
            cadenceLabel = "Weekly cadence",
            updatedAtEpochMillis = 1_000L,
        )

        val initialGoal = created.goalFor(FeatureKind.ORAL_HEALTH)
        requireNotNull(initialGoal)
        assertEquals("Measure every morning", initialGoal.summary)
        assertEquals(1, initialGoal.revision)

        val updated = created.upsert(
            feature = FeatureKind.ORAL_HEALTH,
            summary = "Measure before breakfast",
            targetLabel = "10 morning checks",
            cadenceLabel = "10-day cadence",
            updatedAtEpochMillis = 2_000L,
        )

        val updatedGoal = updated.goalFor(FeatureKind.ORAL_HEALTH)
        requireNotNull(updatedGoal)
        assertEquals("Measure before breakfast", updatedGoal.summary)
        assertEquals("10 morning checks", updatedGoal.targetLabel)
        assertEquals(2, updatedGoal.revision)
    }

    @Test
    fun goalsStayIsolatedPerFeatureAndCanBeRemoved() {
        val cache = GoalCacheState()
            .upsert(
                feature = FeatureKind.ORAL_HEALTH,
                summary = "Oral goal",
                targetLabel = "7 checks",
                cadenceLabel = "Weekly cadence",
                updatedAtEpochMillis = 1_000L,
            )
            .upsert(
                feature = FeatureKind.FAT_BURNING,
                summary = "Fat goal",
                targetLabel = "5 sessions",
                cadenceLabel = "Weekly cadence",
                updatedAtEpochMillis = 2_000L,
            )

        assertEquals("Oral goal", cache.goalFor(FeatureKind.ORAL_HEALTH)?.summary)
        assertEquals("Fat goal", cache.goalFor(FeatureKind.FAT_BURNING)?.summary)

        val afterRemoval = cache.remove(FeatureKind.ORAL_HEALTH)
        assertNull(afterRemoval.goalFor(FeatureKind.ORAL_HEALTH))
        assertEquals("Fat goal", afterRemoval.goalFor(FeatureKind.FAT_BURNING)?.summary)
    }
}
