package com.vokie.semantic

import org.junit.Assert.*
import org.junit.Test

class SemanticExtractorTest {
    private val extractor = SemanticExtractor()
    private fun metadata(text: String) = requireNotNull(extractor.extract(text))

    @Test fun `medical assistance is urgent`() {
        val value = metadata("I need a doctor. Please send help.")
        assertEquals(SemanticEventCategory.MEDICAL, value.eventCategory)
        assertEquals(SemanticUrgency.URGENT, value.urgency)
    }

    @Test fun `fire emergency is critical with active fire hazard`() {
        val value = metadata("There is a fire in the building. Emergency help needed.")
        assertEquals(SemanticEventCategory.FIRE, value.eventCategory)
        assertTrue(SemanticHazard.FIRE in value.hazardFlags)
        assertEquals(SemanticUrgency.CRITICAL, value.urgency)
    }

    @Test fun `negated fire never becomes active hazard`() {
        val value = extractor.extract("There is no fire here. Please send help.")
        assertNotNull(value)
        assertFalse(SemanticHazard.FIRE in value!!.hazardFlags)
        assertEquals(SemanticEventCategory.REQUEST_ASSISTANCE, value.eventCategory)
    }

    @Test fun `negated injury does not become medical event`() {
        val value = extractor.extract("No one is injured. There is no fire.")
        assertNull(value)
    }

    @Test fun `explicit victim count is extracted`() {
        assertEquals(3, metadata("three people are trapped inside").victimCount)
        assertEquals(1, metadata("one person is injured").victimCount)
    }

    @Test fun `vague victim count remains unspecified`() {
        assertNull(metadata("many people are injured").victimCount)
    }

    @Test fun `hindi english doctor request remains medical`() {
        val value = metadata("mujhe doctor chahiye please")
        assertEquals(SemanticEventCategory.MEDICAL, value.eventCategory)
        assertEquals(SemanticUrgency.URGENT, value.urgency)
    }

    @Test fun `tamil english doctor request remains medical`() {
        val value = metadata("enakku doctor venum")
        assertEquals(SemanticEventCategory.MEDICAL, value.eventCategory)
        assertEquals(SemanticUrgency.URGENT, value.urgency)
    }

    @Test fun `romanized fire phrase is detected only by explicit known term`() {
        val value = metadata("fire irukku")
        assertTrue(SemanticHazard.FIRE in value.hazardFlags)
        assertEquals(SemanticEventCategory.FIRE, value.eventCategory)
    }

    @Test fun `unknown and empty text stay unlabelled`() {
        assertNull(extractor.extract("hello there"))
        assertNull(extractor.extract("   "))
    }

    @Test fun `long transcript is bounded and deterministic`() {
        val text = "status update ".repeat(1_000) + "there is a fire"
        assertEquals(extractor.extract(text), extractor.extract(text))
        assertTrue(SemanticHazard.FIRE in metadata(text).hazardFlags)
    }

    @Test fun `wire format is compact versioned and rejects malformed input`() {
        val value = metadata("three people are trapped")
        val wire = value.toWireBytes()
        assertEquals(SemanticMetadata.WIRE_BYTES, wire.size)
        assertEquals(value, SemanticMetadata.fromWireBytes(wire))
        assertNull(SemanticMetadata.fromWireBytes(byteArrayOf(1, 2)))
    }
}
