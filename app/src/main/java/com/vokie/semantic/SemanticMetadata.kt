package com.vokie.semantic

/** Bounded, supplementary N5 semantic hint. Full message text remains authoritative. */
enum class SemanticEventCategory { UNKNOWN, MEDICAL, FIRE, ACCIDENT, TRAPPED, MISSING, THREAT, FLOOD, EARTHQUAKE, GENERAL_EMERGENCY, REQUEST_ASSISTANCE }
enum class SemanticUrgency { UNKNOWN, ROUTINE, URGENT, CRITICAL }
enum class SemanticSeverity { UNKNOWN, LOW, MODERATE, HIGH, CRITICAL }
enum class SemanticCertainty { UNKNOWN, EXPLICIT }
enum class SemanticHazard { FIRE, SMOKE, WATER, GAS, ELECTRICAL, STRUCTURAL, VEHICLE, CROWD }

data class SemanticMetadata(
    val version: Int = VERSION,
    val eventCategory: SemanticEventCategory = SemanticEventCategory.UNKNOWN,
    val urgency: SemanticUrgency = SemanticUrgency.UNKNOWN,
    val severity: SemanticSeverity = SemanticSeverity.UNKNOWN,
    val hazardFlags: Set<SemanticHazard> = emptySet(),
    /** Null means unspecified; only an explicitly stated count is represented. */
    val victimCount: Int? = null,
    val certainty: SemanticCertainty = SemanticCertainty.UNKNOWN,
) {
    init {
        require(version == VERSION)
        require(victimCount == null || victimCount in 1..254)
    }

    val isAvailable: Boolean get() = eventCategory != SemanticEventCategory.UNKNOWN ||
        urgency != SemanticUrgency.UNKNOWN || severity != SemanticSeverity.UNKNOWN ||
        hazardFlags.isNotEmpty() || victimCount != null

    /** Compact N5 wire format: exactly seven bytes, versioned and bounded. */
    fun toWireBytes(): ByteArray = byteArrayOf(
        version.toByte(), eventCategory.ordinal.toByte(), urgency.ordinal.toByte(), severity.ordinal.toByte(),
        hazardFlags.fold(0) { bits, value -> bits or (1 shl value.ordinal) }.toByte(),
        (victimCount ?: 0).toByte(), certainty.ordinal.toByte(),
    )

    /** Human-safe compact UI label; UNKNOWN-only metadata is intentionally hidden. */
    fun displayLabel(): String? = when {
        !isAvailable -> null
        eventCategory != SemanticEventCategory.UNKNOWN && urgency in setOf(SemanticUrgency.URGENT, SemanticUrgency.CRITICAL) -> "${urgency.name} · ${eventCategory.name.replace('_', ' ')}"
        eventCategory != SemanticEventCategory.UNKNOWN -> eventCategory.name.replace('_', ' ')
        urgency != SemanticUrgency.UNKNOWN -> urgency.name
        else -> null
    }

    companion object {
        const val VERSION = 1
        const val WIRE_BYTES = 7

        fun fromWireBytes(bytes: ByteArray): SemanticMetadata? = runCatching {
            require(bytes.size == WIRE_BYTES)
            require(bytes[0].toInt() and 0xFF == VERSION)
            fun <T : Enum<T>> enum(entries: Array<T>, byte: Byte): T = entries.getOrNull(byte.toInt() and 0xFF) ?: error("Invalid semantic enum")
            val category = enum(SemanticEventCategory.entries.toTypedArray(), bytes[1])
            val urgency = enum(SemanticUrgency.entries.toTypedArray(), bytes[2])
            val severity = enum(SemanticSeverity.entries.toTypedArray(), bytes[3])
            val hazards = SemanticHazard.entries.filter { (bytes[4].toInt() and (1 shl it.ordinal)) != 0 }.toSet()
            val victims = (bytes[5].toInt() and 0xFF).takeIf { it != 0 }
            val certainty = enum(SemanticCertainty.entries.toTypedArray(), bytes[6])
            SemanticMetadata(eventCategory = category, urgency = urgency, severity = severity, hazardFlags = hazards, victimCount = victims, certainty = certainty)
        }.getOrNull()
    }
}
