package com.vokie.tts

import com.vokie.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class TextToSpeechUseCase(
    private val engine: TtsEngine,
    private val modelManager: TtsModelManager,
    private val preferences: TtsPreferences,
    private val queue: TtsPlaybackQueue,
) {
    val status: StateFlow<TtsStatus> = engine.status
    val messageStates: StateFlow<Map<String, MessageTtsState>> = queue.messageStates
    val installedLanguages: StateFlow<Set<TtsLanguage>> = modelManager.installedLanguages
    val speed: Flow<Float> = preferences.speed

    fun start() = queue.start()
    fun refreshDownloadedLanguage(language: TtsLanguage) = modelManager.markDownloaded(language)

    suspend fun setSpeed(speed: Float) = preferences.setSpeed(speed)

    suspend fun enqueueReceived(message: Message) = queue.enqueue(message.toTtsQueueItem())
    suspend fun enqueueReceived(messageId: String, text: String, language: com.vokie.domain.model.VokieLanguage, type: com.vokie.domain.model.MessageType) {
        val ttsLanguage = TtsLanguage.fromMessageCode(language.code)
            ?: throw TtsException(TtsErrorCode.UNSUPPORTED_LANGUAGE, "Message language ${language.code} is not supported for speech.")
        queue.enqueue(TtsQueueItem(messageId, text, ttsLanguage, type))
    }
    suspend fun play(message: Message) = queue.enqueue(message.toTtsQueueItem())
    suspend fun stop(messageId: String? = null) = queue.stop(messageId)
    suspend fun acknowledgeSos(messageId: String) = queue.stop(messageId, acknowledgedSos = true)

    private fun Message.toTtsQueueItem(): TtsQueueItem {
        val ttsLanguage = TtsLanguage.fromMessageCode(language)
            ?: throw TtsException(TtsErrorCode.UNSUPPORTED_LANGUAGE, "Message language $language is not supported for speech.")
        return TtsQueueItem(id, text, ttsLanguage, messageType)
    }
}
