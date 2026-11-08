package com.vokie.semantic

/**
 * Small offline rule extractor for explicit communication/emergency wording.
 * It deliberately returns UNKNOWN rather than guessing from general prose.
 */
class SemanticExtractor {
    fun extract(transcript: String): SemanticMetadata? {
        val text = transcript.trim()
        if (text.isEmpty()) return null
        val hazards = linkedSetOf<SemanticHazard>()
        if (active(text, "fire", "आग", "தீ")) hazards += SemanticHazard.FIRE
        if (active(text, "smoke", "धुआ", "புகை")) hazards += SemanticHazard.SMOKE
        if (active(text, "water", "पानी", "தண்ணீர்")) hazards += SemanticHazard.WATER
        if (active(text, "gas", "गैस", "வாயு")) hazards += SemanticHazard.GAS
        if (active(text, "electrical", "electric", "बिजली", "மின்சார")) hazards += SemanticHazard.ELECTRICAL
        if (active(text, "collapse", "structural", "building", "इमारत", "கட்டிடம்")) hazards += SemanticHazard.STRUCTURAL
        if (active(text, "vehicle", "car crash", "accident", "वाहन", "வாகன")) hazards += SemanticHazard.VEHICLE
        if (active(text, "crowd", "भीड़", "கூட்ட")) hazards += SemanticHazard.CROWD

        val medical = active(text, "doctor", "medical", "injured", "injury", "bleeding", "ambulance", "दर्द", "घायल", "डॉक्टर", "காயம்", "மருத்துவ", "மருத்துவர்")
        val trapped = active(text, "trapped", "stuck", "फंस", "सिक्क", "சிக்கி")
        val accident = active(text, "accident", "crash", "दुर्घटना", "விபத்து")
        val request = active(text, "help", "assistance", "please send", "मदद", "चाहिए", "வேண்டும்", "venum", "chahiye")
        val critical = active(text, "critical", "emergency", "urgent", "immediately", "life threatening", "आपात", "तुरंत", "அவசர")

        val category = when {
            SemanticHazard.FIRE in hazards -> SemanticEventCategory.FIRE
            medical -> SemanticEventCategory.MEDICAL
            trapped -> SemanticEventCategory.TRAPPED
            accident -> SemanticEventCategory.ACCIDENT
            SemanticHazard.WATER in hazards -> SemanticEventCategory.FLOOD
            request -> SemanticEventCategory.REQUEST_ASSISTANCE
            critical -> SemanticEventCategory.GENERAL_EMERGENCY
            else -> SemanticEventCategory.UNKNOWN
        }
        val urgency = when {
            critical || trapped || SemanticHazard.FIRE in hazards -> SemanticUrgency.CRITICAL
            medical || accident || request -> SemanticUrgency.URGENT
            else -> SemanticUrgency.UNKNOWN
        }
        val severity = when (urgency) {
            SemanticUrgency.CRITICAL -> SemanticSeverity.CRITICAL
            SemanticUrgency.URGENT -> SemanticSeverity.HIGH
            else -> SemanticSeverity.UNKNOWN
        }
        val victims = explicitVictimCount(text)
        val metadata = SemanticMetadata(
            eventCategory = category,
            urgency = urgency,
            severity = severity,
            hazardFlags = hazards,
            victimCount = victims,
            certainty = if (category != SemanticEventCategory.UNKNOWN || hazards.isNotEmpty() || victims != null) SemanticCertainty.EXPLICIT else SemanticCertainty.UNKNOWN,
        )
        return metadata.takeIf { it.isAvailable }
    }

    private fun active(text: String, vararg terms: String): Boolean = terms.any { term ->
        occurrences(text.lowercase(), term.lowercase()).any { index -> !negatedNear(text.lowercase(), index, term.length) }
    }

    private fun occurrences(text: String, term: String): Sequence<Int> = sequence {
        var start = 0
        while (true) {
            val index = text.indexOf(term, start)
            if (index < 0) break
            yield(index)
            start = index + term.length
        }
    }

    /** Conservative local window; not a full syntactic negation parser. */
    private fun negatedNear(text: String, index: Int, length: Int): Boolean {
        // Keep the window deliberately local so "no fire. send help" does not
        // accidentally negate the later assistance request.
        val before = text.substring(maxOf(0, index - 16), index)
        val after = text.substring(index + length, minOf(text.length, index + length + 16))
        val negators = listOf("no ", "not ", "without ", "none ", "नहीं", "नहि", "कोई नहीं", "இல்லை", "இல்ல")
        return negators.any { it in before || after.trimStart().startsWith(it) }
    }

    private fun explicitVictimCount(text: String): Int? {
        // Count is emitted only with an explicit nearby person/injury/trapped expression.
        if (active(text, "no one", "कोई नहीं", "யாரும் இல்லை")) return null
        val patterns = listOf(
            Regex("\\b(one|1) (person|people|victim).{0,18}(injured|trapped)|\\b(one|1) (person|people|victim)", RegexOption.IGNORE_CASE) to 1,
            Regex("\\b(two|2) (people|persons|victims).{0,18}(injured|trapped)|\\b(two|2) (people|persons|victims)", RegexOption.IGNORE_CASE) to 2,
            Regex("\\b(three|3) (people|persons|victims).{0,18}(injured|trapped)|\\b(three|3) (people|persons|victims)", RegexOption.IGNORE_CASE) to 3,
            Regex("एक (व्यक्ति|लोग).{0,18}(घायल|फंस)") to 1,
            Regex("दो (व्यक्ति|लोग).{0,18}(घायल|फंस)") to 2,
            Regex("तीन (व्यक्ति|लोग).{0,18}(घायल|फंस)") to 3,
            Regex("ஒரு (நபர்|பேர்).{0,18}(காய|சிக்க)") to 1,
            Regex("இரண்டு (நபர்|பேர்).{0,18}(காய|சிக்க)") to 2,
            Regex("மூன்று (நபர்|பேர்).{0,18}(காய|சிக்க)") to 3,
        )
        return patterns.firstOrNull { it.first.containsMatchIn(text) }?.second
    }
}
