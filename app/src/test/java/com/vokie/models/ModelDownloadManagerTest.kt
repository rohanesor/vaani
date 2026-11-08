package com.vokie.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadManagerTest {

    @Test
    fun testDownloadPercentCalculatesAccurateBoundedPercentage() {
        assertEquals(0, downloadPercent(0, 0))
        assertEquals(0, downloadPercent(0, 100))
        assertEquals(50, downloadPercent(50, 100))
        assertEquals(99, downloadPercent(99, 100))
        assertEquals(99, downloadPercent(100, 100))
        assertEquals(99, downloadPercent(120, 100))
        assertEquals(0, downloadPercent(50, -10))
    }

    @Test
    fun testValidResumeRangeValidatesCorrectContentRangeHeader() {
        assertTrue(validResumeRange("bytes 500-999/1000", 500, 1000))
        assertTrue(validResumeRange("bytes 500-750/1000", 500, 1000))
    }

    @Test
    fun testValidResumeRangeRejectsInvalidOrMismatchedRanges() {
        // Mismatched start offset
        assertFalse(validResumeRange("bytes 0-999/1000", 500, 1000))
        assertFalse(validResumeRange("bytes 600-999/1000", 500, 1000))

        // Mismatched total size
        assertFalse(validResumeRange("bytes 500-999/2000", 500, 1000))

        // End byte at or beyond total file size
        assertFalse(validResumeRange("bytes 500-1000/1000", 500, 1000))
        assertFalse(validResumeRange("bytes 500-1500/1000", 500, 1000))

        // Inverted range
        assertFalse(validResumeRange("bytes 800-500/1000", 500, 1000))

        // Malformed headers
        assertFalse(validResumeRange(null, 500, 1000))
        assertFalse(validResumeRange("", 500, 1000))
        assertFalse(validResumeRange("invalid-header", 500, 1000))
        assertFalse(validResumeRange("bytes abc-def/1000", 500, 1000))
    }
}
