package com.airhealth.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultDirectoryCacheStateTest {
    @Test
    fun refreshCreatesPerFeatureDirectoryAndIncrementsRevision() {
        val created = ConsultDirectoryCacheState().refresh(
            feature = FeatureKind.ORAL_HEALTH,
            localeTag = "en-US",
            cachedAtEpochMillis = 1_000L,
        )

        val firstDirectory = requireNotNull(created.directoryFor(FeatureKind.ORAL_HEALTH))
        assertEquals("en-US", firstDirectory.localeTag)
        assertEquals(1, firstDirectory.refreshRevision)
        assertEquals(2, firstDirectory.resources.size)
        assertEquals(FeatureKind.ORAL_HEALTH, firstDirectory.resources.first().feature)

        val refreshed = created.refresh(
            feature = FeatureKind.ORAL_HEALTH,
            localeTag = "en-US",
            cachedAtEpochMillis = 2_000L,
        )

        val secondDirectory = requireNotNull(refreshed.directoryFor(FeatureKind.ORAL_HEALTH))
        assertEquals(2, secondDirectory.refreshRevision)
        assertEquals(2_000L, secondDirectory.cachedAtEpochMillis)
    }

    @Test
    fun directoriesStayIsolatedAcrossFeatures() {
        val cache = ConsultDirectoryCacheState()
            .refresh(feature = FeatureKind.ORAL_HEALTH, localeTag = "en-US", cachedAtEpochMillis = 1_000L)
            .refresh(feature = FeatureKind.FAT_BURNING, localeTag = "en-US", cachedAtEpochMillis = 2_000L)

        val oralDirectory = requireNotNull(cache.directoryFor(FeatureKind.ORAL_HEALTH))
        val fatDirectory = requireNotNull(cache.directoryFor(FeatureKind.FAT_BURNING))

        assertTrue(oralDirectory.resources.all { it.feature == FeatureKind.ORAL_HEALTH })
        assertTrue(fatDirectory.resources.all { it.feature == FeatureKind.FAT_BURNING })
        assertEquals("AirHealth Oral Wellness Coach", oralDirectory.resources.first().title)
        assertEquals("Metabolic Performance Coach", fatDirectory.resources.first().title)
    }

    @Test
    fun resourcesCarryExternalHandoffMetadata() {
        val directory = requireNotNull(
            ConsultDirectoryCacheState()
                .refresh(feature = FeatureKind.ORAL_HEALTH, localeTag = "en-US", cachedAtEpochMillis = 1_000L)
                .directoryFor(FeatureKind.ORAL_HEALTH),
        )

        val resource = directory.resources.first()
        assertEquals("Open virtual consult", resource.launchLabel)
        assertTrue(resource.externalUrl.startsWith("https://"))
        assertTrue(resource.leaveAirHealthMessage.contains("AirHealth", ignoreCase = true))
    }
}
