package com.vokie.stt

import com.vokie.domain.model.VokieLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSegmenterTest {
    private val segmenter = SentenceSegmenter()

    @Test fun emptyReturnsEmpty() = assertEquals(emptyList<String>(), segmenter.split(""))
    @Test fun whitespaceOnlyReturnsEmpty() = assertEquals(emptyList<String>(), segmenter.split("   \n  "))
    @Test fun singleSentenceWithoutTerminator() = assertEquals(listOf("No punctuation here"), segmenter.split("No punctuation here"))
    @Test fun twoEnglishSentences() = assertEquals(listOf("Hello.", "World."), segmenter.split("Hello. World."))
    @Test fun englishMixedTerminators() = assertEquals(listOf("Are you there?", "Yes!", "Ok."), segmenter.split("Are you there? Yes! Ok."))
    @Test fun hindiDandaSplits() = assertEquals(listOf("मुझे मदद चाहिए।", "जल्दी आओ।"), segmenter.split("मुझे मदद चाहिए। जल्दी आओ।", VokieLanguage.HI))
    @Test fun hindiDoubleDandaSplits() = assertEquals(listOf("श्लोक॥", "अगला।"), segmenter.split("श्लोक॥ अगला।", VokieLanguage.HI))
    @Test fun tamilPeriodSplits() = assertEquals(
        listOf("எனக்கு உதவி வேண்டும்.", "நான் இங்கே இருக்கிறேன்."),
        segmenter.split("எனக்கு உதவி வேண்டும். நான் இங்கே இருக்கிறேன்.", VokieLanguage.TA),
    )
    @Test fun trailingFragmentPreservedWithoutTerminator() = assertEquals(listOf("Done.", "Half"), segmenter.split("Done. Half"))
    @Test fun multipleSpacesBetweenSentencesAreTrimmed() = assertEquals(listOf("First.", "Second."), segmenter.split("First.   Second."))
    @Test fun mixedScriptCodeSwitchedInputSplits() = assertEquals(
        listOf("मुझे help चाहिए।", "Please come."),
        segmenter.split("मुझे help चाहिए। Please come.", VokieLanguage.HI),
    )
    @Test fun onlyTerminatorsProduceIndividualEntries() = assertEquals(listOf("!", "!", "!"), segmenter.split("!!!"))
    @Test fun longRunOfCharactersIsOneSentence() { val text = "a".repeat(1_000); assertEquals(listOf(text), segmenter.split(text)) }
}
