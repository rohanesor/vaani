package com.vokie.location

import org.junit.Assert.*
import org.junit.Test

class TelemetryAdapterTest {
    @Test fun locationMappingPreservesRealFixFieldsAndRejectsInvalidFix() {
        val metadata = locationMetadataFromRaw(12.0, 77.0, 8f, 1234L, 4)
        assertEquals(LocationAvailability.AVAILABLE, metadata.availability); assertEquals(8f, metadata.accuracyMeters); assertEquals(1234L, metadata.timestamp); assertEquals(4L, metadata.locationSequence)
        val invalid = locationMetadataFromRaw(Double.NaN, 77.0, 1f, 1L, 1)
        assertEquals(LocationAvailability.INVALID_FIX, invalid.availability)
    }
    @Test fun circularFilterCrossesZeroWithoutLargeJump() {
        val f = CircularHeadingFilter(1.0)
        assertEquals(359.0, f.add(359.0), .001); assertEquals(1.0, f.add(1.0), .001)
        assertEquals(0.0, f.add(0.0), .001); assertEquals(90.0, f.add(90.0), .001); assertEquals(180.0, f.add(180.0), .001); assertEquals(270.0, f.add(270.0), .001)
    }
}
