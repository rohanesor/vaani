package com.vokie.semantic

import com.vokie.communication.PacketV2
import com.vokie.data.VokieMessageId
import com.vokie.domain.model.Message
import java.io.File
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM engineering measurement; not an Android CPU/memory benchmark. */
class SemanticPerformanceTest {
    @Test fun writesRepresentativeSizeAndTimingReport() {
        val samples = listOf(
            "Help me.",
            "I need water and a doctor.",
            "मुझे emergency help चाहिए",
            "எனக்கு phone வேலை செய்யவில்லை help வேண்டும்",
            "There is no fire here. Please send help.",
        )
        val extractor = SemanticExtractor()
        repeat(100) { samples.forEach(extractor::extract) } // warm-up
        val extractionNs = measureNanoTime { repeat(1_000) { samples.forEach(extractor::extract) } }
        val metadata = requireNotNull(extractor.extract(samples[1]))
        val serializationNs = measureNanoTime { repeat(10_000) { metadata.toWireBytes() } }
        val rows = samples.map { text ->
            val plain = Message(VokieMessageId.generate(), "sender", 1_000, text, "EN")
            val enriched = plain.copy(semanticMetadata = extractor.extract(text))
            val plainBytes = PacketV2.fromMessage(plain).sumOf { it.size }
            val enrichedBytes = PacketV2.fromMessage(enriched).sumOf { it.size }
            "{\"chars\":${text.length},\"plainBytes\":$plainBytes,\"enrichedBytes\":$enrichedBytes,\"metadataPresent\":${enriched.semanticMetadata != null}}"
        }
        val report = "{\"sampleSize\":${samples.size},\"extractionCalls\":5000,\"extractionNanos\":$extractionNs,\"wireSerializationCalls\":10000,\"wireSerializationNanos\":$serializationNs,\"metadataWireBytes\":${SemanticMetadata.WIRE_BYTES},\"messages\":[${rows.joinToString(",")}],\"notes\":\"JVM micro-measurement; not Android CPU, memory, or end-to-end latency evidence.\"}"
        var root: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (root != null && !File(root, "app").isDirectory) root = root.parentFile
        val output = File(requireNotNull(root), "app/build/reports/semantic/n5-performance.json")
        requireNotNull(output.parentFile).mkdirs(); output.writeText(report)
        assertTrue(output.isFile)
    }
}
