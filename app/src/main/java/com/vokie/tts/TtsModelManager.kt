package com.vokie.tts

import android.content.Context
import com.vokie.models.BundledModelStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Accesses verified model packs extracted automatically from this APK. No downloader/importer exists. */
class TtsModelManager(context: Context) {
    private val bundled = BundledModelStore(context)
    private val _installedLanguages = MutableStateFlow(scanInstalled())
    val installedLanguages: StateFlow<Set<TtsLanguage>> = _installedLanguages.asStateFlow()

    fun modelDirectory(language: TtsLanguage): File = bundled.ttsDirectory(language.iso6393)
    fun modelFile(language: TtsLanguage) = File(modelDirectory(language), "model.onnx")
    fun tokensFile(language: TtsLanguage) = File(modelDirectory(language), "tokens.txt")
    fun phonemizerDirectory() = bundled.ttsDirectory("espeak-ng-data")
    fun isInstalled(language: TtsLanguage): Boolean = try {
        val model = bundled.spec("tts/${language.iso6393}/model.onnx")
        val tokens = bundled.spec("tts/${language.iso6393}/tokens.txt")
        modelFile(language).length() == model.sizeBytes && tokensFile(language).length() == tokens.sizeBytes
    } catch (_: Throwable) { false }
    fun installedSizeBytes(language: TtsLanguage) = modelFile(language).length() + tokensFile(language).length()
    fun missingLanguages(): Set<TtsLanguage> = TtsLanguage.entries.filterNot(::isInstalled).toSet()
    fun availableOfficialPackages(): Set<TtsLanguage> = TtsLanguage.entries.toSet()
    fun refresh() { _installedLanguages.value = scanInstalled() }
    fun markDownloaded(language: TtsLanguage) { if (isInstalled(language)) _installedLanguages.value = scanInstalled() }
    fun releaseUnusedModel(activeLanguage: TtsLanguage?) { require(activeLanguage == null || activeLanguage in TtsLanguage.entries) }
    private fun scanInstalled() = TtsLanguage.entries.filter(::isInstalled).toSet()
}
