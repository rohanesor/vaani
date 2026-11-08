package com.vokie.location

import com.vokie.domain.model.TransportType
import com.vokie.proximity.*
import org.junit.Assert.*
import org.junit.Test

class LocationProximityEngineTest {
    private val now = 10_000L
    private fun point(lat: Double, lon: Double, accuracy: Float = 5f, time: Long = now) = LocationMetadata(lat, lon, accuracy, time, 1, LocationAvailability.AVAILABLE)
    @Test fun geographicDirectionsAndHeadingAreDeterministic() {
        val e = LocationGuidanceEngine(nearbyMeters = .1)
        assertEquals(CardinalDirection.N, e.guide(point(1.0, 0.0), point(0.0, 0.0), 0.0, now).cardinalDirection)
        assertEquals(CardinalDirection.S, e.guide(point(-1.0, 0.0), point(0.0, 0.0), 0.0, now).cardinalDirection)
        assertEquals(CardinalDirection.E, e.guide(point(0.0, 1.0), point(0.0, 0.0), 0.0, now).cardinalDirection)
        assertEquals(CardinalDirection.W, e.guide(point(0.0, -1.0), point(0.0, 0.0), 0.0, now).cardinalDirection)
        assertEquals(RelativeDirection.AHEAD, e.relativeDirection(0.0)); assertEquals(RelativeDirection.RIGHT, e.relativeDirection(90.0)); assertEquals(RelativeDirection.BEHIND, e.relativeDirection(180.0)); assertEquals(RelativeDirection.LEFT, e.relativeDirection(270.0))
        assertEquals(2.0, e.normalize(1.0 - 359.0), .001)
    }
    @Test fun nearbyStaleAccuracyAndInvalidFixesAreSafe() {
        val e = LocationGuidanceEngine(staleAfterMs = 100, nearbyMeters = 10.0)
        assertNull(e.guide(point(0.0,0.0), point(0.0,0.0), 0.0, now).cardinalDirection)
        assertEquals(LocationFreshness.STALE, e.guide(point(1.0,0.0,time=1), point(0.0,0.0), null, now).freshness)
        assertEquals(LocationConfidence.LOW, e.guide(point(1.0,0.0,100f), point(0.0,0.0,100f), null, now).confidence)
        assertFails { LocationMetadata(91.0,0.0,1f,now,1,LocationAvailability.AVAILABLE) }; assertFails { LocationMetadata(Double.NaN,0.0,1f,now,1,LocationAvailability.AVAILABLE) }; assertFails { LocationMetadata(0.0,0.0,-1f,now,1,LocationAvailability.AVAILABLE) }
    }
    @Test fun rssiFilteringTracksTrendWithoutDistance() {
        fun t(rssi: Int, time: Long) = ProximityTelemetry("a", TransportType.BLUETOOTH, rssi, time)
        val f = RssiFilter(windowSize=1, unstableRangeDb=20); f.add(t(-80,1)); assertEquals(RssiTrend.STRENGTHENING, f.add(t(-75,2)).trend); assertEquals(RssiTrend.STRENGTHENING, f.add(t(-70,3)).trend)
        val w = RssiFilter(windowSize=1, unstableRangeDb=20); w.add(t(-60,1)); assertEquals(RssiTrend.WEAKENING, w.add(t(-67,2)).trend); assertEquals(RssiTrend.WEAKENING, w.add(t(-73,3)).trend)
        val noisy = RssiFilter(windowSize=3, unstableRangeDb=10); noisy.add(t(-60,1)); noisy.add(t(-80,2)); assertEquals(RssiTrend.UNSTABLE, noisy.add(t(-65,3)).trend)
        assertEquals(RssiFreshness.STALE, noisy.current(40_000).freshness)
    }
    @Test fun proximityRequiresGpsAndRssiAgreement() {
        val location = LocationGuidanceEngine(nearbyMeters=.1).guide(point(1.0,0.0),point(0.0,0.0),0.0,now)
        val engine = ProximityGuidanceEngine(distanceDeltaMeters=1.0)
        fun signal(trend: RssiTrend) = FilteredRssi(-70.0,trend,3,RssiFreshness.CURRENT)
        assertEquals(ProximityGuidanceState.GETTING_CLOSER, engine.guide(location, location.distanceMeters!! + 10, signal(RssiTrend.STRENGTHENING)).state)
        assertEquals(ProximityGuidanceState.GETTING_FARTHER, engine.guide(location, location.distanceMeters!! - 10, signal(RssiTrend.WEAKENING)).state)
        assertEquals(ProximityGuidanceState.SIGNAL_UNRELIABLE, engine.guide(location, location.distanceMeters!! + 10, signal(RssiTrend.WEAKENING)).state)
    }
    private fun assertFails(block: () -> Unit) { try { block(); fail("expected failure") } catch (_: IllegalArgumentException) {} }
}
