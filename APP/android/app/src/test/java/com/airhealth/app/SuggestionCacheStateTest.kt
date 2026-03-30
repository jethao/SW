package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SuggestionCacheStateTest {
    @Test
    fun refreshCreatesSuggestionAndTracksRevision() {
        val templates = suggestionTemplatesFor(
            feature = FeatureKind.ORAL_HEALTH,
            goal = null,
        )

        val created = SuggestionCacheState().refresh(
            feature = FeatureKind.ORAL_HEALTH,
            entryPoint = SuggestionEntryPoint.FEATURE_HUB,
            templates = templates,
            cachedAtEpochMillis = 1_000L,
        )

        val first = created.suggestionFor(FeatureKind.ORAL_HEALTH)
        requireNotNull(first)
        assertEquals(SuggestionEntryPoint.FEATURE_HUB, first.entryPoint)
        assertEquals(1, first.refreshRevision)

        val refreshed = created.refresh(
            feature = FeatureKind.ORAL_HEALTH,
            entryPoint = SuggestionEntryPoint.FEATURE_HUB,
            templates = templates,
            cachedAtEpochMillis = 2_000L,
        )
        val second = refreshed.suggestionFor(FeatureKind.ORAL_HEALTH)
        requireNotNull(second)
        assertEquals(2, second.refreshRevision)
        assertNotNull(second.headline)
        assertNotNull(second.body)
    }

    @Test
    fun templatesCanIncorporateGoalContext() {
        val goal = FeatureGoal(
            feature = FeatureKind.FAT_BURNING,
            summary = "Recover after training",
            targetLabel = "5 guided sessions",
            cadenceLabel = "Weekly cadence",
            updatedAtEpochMillis = 1_000L,
            revision = 1,
        )

        val templates = suggestionTemplatesFor(
            feature = FeatureKind.FAT_BURNING,
            goal = goal,
        )

        val refreshed = SuggestionCacheState().refresh(
            feature = FeatureKind.FAT_BURNING,
            entryPoint = SuggestionEntryPoint.RESULT_CONTEXT,
            templates = templates,
            cachedAtEpochMillis = 3_000L,
        )

        val suggestion = refreshed.suggestionFor(FeatureKind.FAT_BURNING)
        requireNotNull(suggestion)
        assertEquals(SuggestionEntryPoint.RESULT_CONTEXT, suggestion.entryPoint)
        assertEquals(1, suggestion.refreshRevision)
        assertEquals(true, suggestion.body.contains("5 guided sessions"))
    }
}
