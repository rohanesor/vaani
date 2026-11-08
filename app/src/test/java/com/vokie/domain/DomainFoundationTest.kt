package com.vokie.domain

import com.vokie.domain.model.VokieLanguage
import com.vokie.location.*
import com.vokie.proximity.*
import com.vokie.stt.UserLanguageProfile
import com.vokie.translation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DomainFoundationTest {
    @Test fun profileSupportsSameDifferentAndRejectsUnknownCodes() {
        assertEquals(UserLanguageProfile.same(VokieLanguage.TA), UserLanguageProfile.fromCodes("ta", "TA"))
        assertEquals(UserLanguageProfile(VokieLanguage.TA, VokieLanguage.HI), UserLanguageProfile.fromCodes("TA", "hi"))
        assertNull(UserLanguageProfile.fromCodes("XX", "EN")); assertNull(UserLanguageProfile.fromCodes("TA", "XX"))
    }

    @Test fun translationIsReceiverLocalPassThroughAndUnavailableWithoutEnglishFallback() = runBlocking {
        val unavailable = UnavailableTranslationEngine()
        val coordinator = ReceiverTranslationCoordinator(unavailable)
        assertEquals(TranslationStatus.PASSTHROUGH, coordinator.translate("a", "வணக்கம்", VokieLanguage.TA, VokieLanguage.TA).status)
        val taHi = coordinator.translate("b", "வணக்கம்", VokieLanguage.TA, VokieLanguage.HI)
        assertEquals(TranslationStatus.UNAVAILABLE, taHi.status); assertNull(taHi.translatedText); assertEquals(VokieLanguage.HI, taHi.targetLanguage)
        assertEquals(TranslationStatus.UNAVAILABLE, coordinator.translate("c", "नमस्ते", VokieLanguage.HI, VokieLanguage.TA).status)
    }

    @Test fun guidanceHandlesCardinalsWrapStaleAndAccuracy() {
        val e = LocationGuidanceEngine(staleAfterMs = 100, lowAccuracyMeters = 20f); val now = 1_000L
        fun point(lat: Double, lon: Double, accuracy: Float = 5f, time: Long = now) = LocationMetadata(lat, lon, accuracy, time, 1, LocationAvailability.AVAILABLE)
        assertEquals(0.0, e.guide(point(0.0,0.0),point(0.0,0.0),0.0,now).distanceMeters!!, .01)
        assertEquals(CardinalDirection.N, e.guide(point(1.0,0.0),point(0.0,0.0),0.0,now).cardinalDirection)
        assertEquals(CardinalDirection.S, e.guide(point(-1.0,0.0),point(0.0,0.0),0.0,now).cardinalDirection)
        assertEquals(CardinalDirection.E, e.guide(point(0.0,1.0),point(0.0,0.0),0.0,now).cardinalDirection)
        assertEquals(CardinalDirection.W, e.guide(point(0.0,-1.0),point(0.0,0.0),0.0,now).cardinalDirection)
        assertEquals(CardinalDirection.NE, e.guide(point(1.0,1.0),point(0.0,0.0),0.0,now).cardinalDirection)
        assertEquals(CardinalDirection.NW, e.guide(point(1.0,-1.0),point(0.0,0.0),0.0,now).cardinalDirection)
        assertEquals(CardinalDirection.SE, e.guide(point(-1.0,1.0),point(0.0,0.0),0.0,now).cardinalDirection)
        assertEquals(CardinalDirection.SW, e.guide(point(-1.0,-1.0),point(0.0,0.0),0.0,now).cardinalDirection)
        assertEquals(10.0, e.normalize(0.0 - 350.0), .001)
        assertEquals(LocationFreshness.STALE, e.guide(point(1.0,0.0,time=1),point(0.0,0.0),null,now).freshness)
        assertEquals(LocationConfidence.LOW, e.guide(point(1.0,0.0,50f),point(0.0,0.0),null,now).confidence)
    }

    @Test fun rssiTrendIsEvidenceNotDistance() {
        fun telemetry(rssi: Int, time: Long) = ProximityTelemetry("sender", com.vokie.domain.model.TransportType.BLUETOOTH, rssi, time)
        val stronger = RssiFilter(1); stronger.add(telemetry(-80, 1)); assertEquals(RssiTrend.STRENGTHENING, stronger.add(telemetry(-75, 2)).trend); assertEquals(RssiTrend.STRENGTHENING, stronger.add(telemetry(-70, 3)).trend)
        val weaker = RssiFilter(1); weaker.add(telemetry(-60, 1)); assertEquals(RssiTrend.WEAKENING, weaker.add(telemetry(-67, 2)).trend); assertEquals(RssiTrend.WEAKENING, weaker.add(telemetry(-73, 3)).trend)
    }
}
