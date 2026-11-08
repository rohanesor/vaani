package com.vokie.location

import com.vokie.proximity.ProximityGuidanceEngine
import com.vokie.proximity.ProximityGuidanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistancePresentationTest {
    @Test fun `median smoothing is bounded and rejects invalid values`() {
        val smoother = DistanceSmoother(3)
        assertEquals(14.0, smoother.add(14.0)!!, 0.0)
        smoother.add(22.0)
        assertEquals(14.0, smoother.add(11.0)!!, 0.0)
        assertEquals(19.0, smoother.add(19.0)!!, 0.0)
        assertEquals(null, smoother.add(Double.NaN))
    }

    @Test fun `buckets and uncertainty avoid false precision`() {
        assertEquals(DistanceBucket.VERY_CLOSE, DistancePresentation.bucket(4.9))
        assertEquals(DistanceBucket.NEARBY, DistancePresentation.bucket(14.0))
        assertEquals(DistanceBucket.CLOSE, DistancePresentation.bucket(15.0))
        assertTrue(DistancePresentation.isUncertain(14.0, 70f))
    }

    @Test fun `trend tolerates non monotonic samples`() {
        val trend = DistanceTrendClassifier(sampleCount = 5, minimumChangeMeters = 3.0)
        listOf(20.0, 18.0, 19.0, 15.0, 12.0).forEach { trend.add(it, LocationFreshness.CURRENT) }
        assertEquals(DistanceTrend.GETTING_CLOSER, trend.add(12.0, LocationFreshness.CURRENT))
        assertEquals(DistanceTrend.UNRELIABLE, trend.add(null, LocationFreshness.STALE))
    }

    @Test fun `gps trend remains useful without rssi`() {
        val engine = ProximityGuidanceEngine(nearbyMeters = .1, distanceDeltaMeters = 3.0)
        val location = LocationGuidance(20.0, 0.0, null, CardinalDirection.N, RelativeDirection.UNKNOWN, LocationConfidence.HIGH, LocationFreshness.CURRENT)
        assertEquals(ProximityGuidanceState.GETTING_CLOSER, engine.guide(location, 25.0, null).state)
        assertEquals(ProximityGuidanceState.GETTING_FARTHER, engine.guide(location, 15.0, null).state)
    }
}
