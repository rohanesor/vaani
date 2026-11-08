package com.vokie.location

import kotlin.math.abs

enum class DistanceBucket { VERY_CLOSE, NEARBY, CLOSE, NEAR, FAR, FARTHER_AWAY, UNKNOWN }
enum class DistanceTrend { GETTING_CLOSER, GETTING_FARTHER, STABLE, UNRELIABLE }

/** Bounded median display filter. It only smooths presentation; geographic bearing remains raw. */
class DistanceSmoother(private val windowSize: Int = 5) {
    private val samples = ArrayDeque<Double>()
    init { require(windowSize in 1..15) }

    fun add(distanceMeters: Double?): Double? {
        if (distanceMeters == null || !distanceMeters.isFinite() || distanceMeters < 0) return null
        samples.addLast(distanceMeters)
        while (samples.size > windowSize) samples.removeFirst()
        return samples.sorted()[samples.size / 2]
    }

    fun clear() = samples.clear()
}

class DistanceTrendClassifier(
    private val sampleCount: Int = 5,
    private val minimumChangeMeters: Double = 3.0,
) {
    private val samples = ArrayDeque<Double>()
    init { require(sampleCount in 2..15); require(minimumChangeMeters >= 0) }

    fun add(distanceMeters: Double?, freshness: LocationFreshness): DistanceTrend {
        if (freshness != LocationFreshness.CURRENT || distanceMeters == null || !distanceMeters.isFinite() || distanceMeters < 0) return DistanceTrend.UNRELIABLE
        samples.addLast(distanceMeters)
        while (samples.size > sampleCount) samples.removeFirst()
        if (samples.size < 2) return DistanceTrend.STABLE
        val change = samples.last() - samples.first()
        return when {
            change <= -minimumChangeMeters -> DistanceTrend.GETTING_CLOSER
            change >= minimumChangeMeters -> DistanceTrend.GETTING_FARTHER
            else -> DistanceTrend.STABLE
        }
    }

    fun clear() = samples.clear()
}

object DistancePresentation {
    fun bucket(distanceMeters: Double?): DistanceBucket = when {
        distanceMeters == null || !distanceMeters.isFinite() || distanceMeters < 0 -> DistanceBucket.UNKNOWN
        distanceMeters < 5 -> DistanceBucket.VERY_CLOSE
        distanceMeters < 15 -> DistanceBucket.NEARBY
        distanceMeters < 50 -> DistanceBucket.CLOSE
        distanceMeters < 100 -> DistanceBucket.NEAR
        distanceMeters <= 500 -> DistanceBucket.FAR
        else -> DistanceBucket.FARTHER_AWAY
    }

    /** A position error at least as large as the distance must never imply precise range. */
    fun isUncertain(distanceMeters: Double?, combinedAccuracyMeters: Float?): Boolean =
        distanceMeters == null || combinedAccuracyMeters == null || combinedAccuracyMeters <= 0f || distanceMeters <= combinedAccuracyMeters

    fun label(bucket: DistanceBucket): String = bucket.name.replace('_', ' ')
}
