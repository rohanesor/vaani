package com.vokie.location

import kotlinx.coroutines.flow.StateFlow

data class Heading(val degrees: Double?, val confidence: Float?, val source: HeadingSource)
enum class HeadingSource { ROTATION_VECTOR, MAGNETOMETER, GPS_COURSE, UNAVAILABLE }

/** Future implementations may fuse rotation-vector/magnetometer with GPS course while moving. */
interface HeadingProvider { val heading: StateFlow<Heading> }
