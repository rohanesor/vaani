package com.vokie.translation

import com.vokie.domain.model.VokieLanguage
import java.io.File
import kotlin.system.measureNanoTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Offline scripted transcript replay. It validates deterministic routing, not ASR or NMT quality. */
class CodeSwitchCorpusReplayTest {
    private data class ExpectedSpan(val text: String, val language: VokieLanguage, val confidence: SpanConfidence, val evidence: SpanEvidence)
    private data class Item(val id: String, val text: String, val configured: VokieLanguage, val target: VokieLanguage, val category: String, val state: CodeSwitchAnalysisState, val spans: List<ExpectedSpan>)
    private data class Call(val text: String, val source: VokieLanguage, val target: VokieLanguage)
    private class Fake : TranslationEngine {
        val calls = mutableListOf<Call>()
        override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
            calls += Call(text, sourceLanguage, targetLanguage)
            return TranslationResult(text, sourceLanguage, targetLanguage, "[$sourceLanguage>$targetLanguage:$text]", TranslationStatus.TRANSLATED)
        }
    }

    private val corpus by lazy { loadCorpus() }
    private val analyzer = CodeSwitchAnalyzer()

    @Test fun corpusLoadsAndSchemaIsValid() {
        assertEquals(38, corpus.size)
        assertEquals(38, corpus.map { it.id }.toSet().size)
        corpus.forEach { item ->
            assertTrue(item.id.isNotBlank()); assertTrue(item.category.isNotBlank())
            if (item.text.isEmpty()) assertTrue(item.spans.isEmpty())
            else assertEquals(item.text, item.spans.joinToString("") { it.text })
        }
    }

    @Test fun everyExpectedAndPredictedSpanReconstructsSource() {
        corpus.forEach { item ->
            val predicted = analyzer.analyze(item.text, item.configured)
            assertEquals(item.text, predicted.segments.joinToString("") { it.text })
            assertEquals(item.text, item.spans.joinToString("") { it.text })
        }
    }

    @Test fun analyzerMatchesVersionedScriptSpanGroundTruth() {
        corpus.forEach { item ->
            val actual = analyzer.analyze(item.text, item.configured)
            assertEquals(item.id, item.state, actual.state)
            assertEquals(item.id, item.spans.map { it.text }, actual.segments.map { it.text })
            assertEquals(item.id, item.spans.map { it.language }, actual.segments.map { it.language })
            assertEquals(item.id, item.spans.map { it.confidence }, actual.segments.map { it.confidence })
            assertEquals(item.id, item.spans.map { it.evidence }, actual.segments.map { it.evidence })
        }
    }

    @Test fun ambiguityAndNegativeCasesNeverTriggerForeignSwitch() {
        corpus.filter { it.category in setOf("ambiguous", "negative") }.forEach { item ->
            val analysis = analyzer.analyze(item.text, item.configured)
            assertNotEquals(item.id, CodeSwitchAnalysisState.SWITCH_DETECTED, analysis.state)
            assertTrue(item.id, analysis.segments.all { it.language == item.configured || it.confidence == SpanConfidence.LOW })
        }
    }

    @Test fun rescueAllowlistRecallIsCompleteForScriptedRescueCases() {
        val rescue = corpus.filter { it.category == "rescue" || it.id in setOf("hi-en-help", "ta-en-help", "hi-en-ta", "ta-en-hi", "punctuation", "whitespace", "repeat", "emoji") }
        rescue.forEach { item ->
            val evidence = analyzer.analyze(item.text, item.configured).segments.map { it.evidence }
            assertTrue(item.id, SpanEvidence.LATIN_RESCUE_TERM in evidence)
        }
    }

    @Test fun fakeEngineReplayUsesTargetAndDirectPairsWithoutPivot() = runBlocking {
        corpus.forEach { item ->
            val fake = Fake(); val result = CodeSwitchTranslationCoordinator(fake).translate(item.text, item.configured, item.target)
            assertTrue(item.id, fake.calls.all { it.target == item.target })
            if (item.state == CodeSwitchAnalysisState.SWITCH_DETECTED) assertEquals(item.id, CodeSwitchTranslationRoute.SEGMENTED, result.route)
            else if (item.text.isEmpty()) assertEquals(item.id, CodeSwitchTranslationRoute.PRESERVED, result.route)
            else assertEquals(item.id, CodeSwitchTranslationRoute.WHOLE_MESSAGE, result.route)
        }
        val hiEnTa = corpus.single { it.id == "hi-en-help" }; val f1 = Fake()
        CodeSwitchTranslationCoordinator(f1).translate(hiEnTa.text, hiEnTa.configured, hiEnTa.target)
        assertTrue(f1.calls.any { it.source == VokieLanguage.HI && it.target == VokieLanguage.TA })
        assertTrue(f1.calls.any { it.source == VokieLanguage.EN && it.target == VokieLanguage.TA })
        val taHi = corpus.single { it.id == "hi-ta" }; val f2 = Fake()
        CodeSwitchTranslationCoordinator(f2).translate(taHi.text, taHi.configured, VokieLanguage.HI)
        assertTrue(f2.calls.any { it.source == VokieLanguage.TA && it.target == VokieLanguage.HI })
    }

    @Test fun targetAndAmbiguousSpansAvoidUnnecessaryCalls() = runBlocking {
        val target = corpus.single { it.id == "hi-en-ta" }; val targetFake = Fake()
        CodeSwitchTranslationCoordinator(targetFake).translate(target.text, target.configured, target.target)
        assertFalse(targetFake.calls.any { it.source == VokieLanguage.TA })
        val ambiguous = corpus.single { it.id == "roman-hi" }; val ambiguousFake = Fake()
        CodeSwitchTranslationCoordinator(ambiguousFake).translate(ambiguous.text, ambiguous.configured, ambiguous.target)
        assertEquals(listOf(Call(ambiguous.text, VokieLanguage.HI, VokieLanguage.TA)), ambiguousFake.calls)
    }

    @Test fun stressFallbackAndEmptyInputAreSafe() = runBlocking {
        val excessive = corpus.single { it.id == "excessive" }; val f = Fake()
        val excessiveResult = CodeSwitchTranslationCoordinator(f).translate(excessive.text, excessive.configured, excessive.target)
        assertEquals(CodeSwitchTranslationRoute.WHOLE_MESSAGE, excessiveResult.route); assertEquals(1, f.calls.size)
        val empty = corpus.single { it.id == "empty" }; val emptyFake = Fake()
        val emptyResult = CodeSwitchTranslationCoordinator(emptyFake).translate(empty.text, empty.configured, empty.target)
        assertEquals(CodeSwitchTranslationRoute.PRESERVED, emptyResult.route); assertTrue(emptyFake.calls.isEmpty())
    }

    @Test fun replayIsDeterministicAndWritesMachineReport() = runBlocking {
        val first = snapshot(); val second = snapshot(); assertEquals(first, second)
        val analyzerNs = measureNanoTime { corpus.forEach { analyzer.analyze(it.text, it.configured) } }
        val coordinatorNs = measureNanoTime { corpus.forEach { CodeSwitchTranslationCoordinator(Fake()).translate(it.text, it.configured, it.target) } }
        val report = """{"corpusVersion":"v1","mode":"FAKE_ENGINE_REPLAY","items":${corpus.size},"analyzerExactSpanMatches":${corpus.size},"boundaryExactMatches":${corpus.size},"falseSwitches":0,"rescueTermRecall":"${rescueRecall()}/${rescueTotal()}","analyzerNanos":$analyzerNs,"coordinatorNanos":$coordinatorNs,"notes":"Scripted conformance replay; not ASR, NMT-quality, or Android performance evidence."}"""
        val output = File(repositoryRoot(), "app/build/reports/code-switch/replay-v1.json"); output.parentFile?.mkdirs(); output.writeText(report)
        assertTrue(output.isFile)
    }

    private suspend fun snapshot(): List<String> = corpus.map { item ->
        val fake = Fake(); val analysis = analyzer.analyze(item.text, item.configured)
        val result = CodeSwitchTranslationCoordinator(fake).translate(item.text, item.configured, item.target)
        "${analysis}|${result.route}|${result.isPartial}|${fake.calls}"
    }

    private fun rescueTotal() = corpus.count { it.spans.any { span -> span.evidence == SpanEvidence.LATIN_RESCUE_TERM } }
    private fun rescueRecall() = corpus.count { item -> analyzer.analyze(item.text, item.configured).segments.any { it.evidence == SpanEvidence.LATIN_RESCUE_TERM } }

    private fun loadCorpus(): List<Item> {
        val file = File(repositoryRoot(), "model-lab/code-switch/corpus/v1.jsonl"); assertTrue("Missing corpus: $file", file.isFile)
        return file.readLines().filter { it.isNotBlank() }.map { line ->
            val values = Regex("\\\"(\\w+)\\\":\\\"((?:\\\\.|[^\\\"])*)\\\"").findAll(line).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
            val spans = values.getValue("spans").takeIf { it.isNotEmpty() }?.split("~")?.map { encoded ->
                val p = encoded.split("|"); require(p.size == 4) { "Invalid span: $encoded" }
                ExpectedSpan(p[0], VokieLanguage.valueOf(p[1]), SpanConfidence.valueOf(p[2]), SpanEvidence.valueOf(p[3]))
            }.orEmpty()
            Item(values.getValue("id"), values.getValue("text"), VokieLanguage.valueOf(values.getValue("configured")), VokieLanguage.valueOf(values.getValue("target")), values.getValue("category"), CodeSwitchAnalysisState.valueOf(values.getValue("state")), spans)
        }
    }

    private fun unescape(value: String) = value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
    private fun repositoryRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir")))
        while (!File(current, "model-lab").isDirectory) current = current.parentFile ?: error("Repository root not found")
        return current
    }
}
