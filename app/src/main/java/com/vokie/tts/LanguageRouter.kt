package com.vokie.tts

import com.vokie.domain.model.VokieLanguage

/** Model-neutral language route. It never substitutes a different language. */
data class TtsRoute(val language: TtsLanguage, val backendId: String, val availability: TtsAvailability)
enum class TtsAvailability { PRODUCTION_APPROVED, BENCHMARK_ONLY, MOCK_TEST_ONLY, BLOCKED }

class TtsModelRegistry(routes: Map<TtsLanguage, TtsRoute> = emptyMap()) {
    private val routes = routes.toMap()
    fun route(languageCode: String): TtsRoute? = TtsLanguage.fromMessageCode(languageCode)?.let(routes::get)
    fun route(language: TtsLanguage): TtsRoute? = routes[language]
    fun supportedLanguages(): Set<TtsLanguage> = routes.keys
}

/** Explicit route selection used by the receiver; missing assets become UNSUPPORTED_LANGUAGE. */
class LanguageRouter(private val registry: TtsModelRegistry) {
    fun resolve(languageCode: String): TtsRoute = registry.route(languageCode)
        ?: throw TtsException(TtsErrorCode.UNSUPPORTED_LANGUAGE, "No approved offline TTS backend is installed for $languageCode.")
}

fun emptyProductionRegistry(): TtsModelRegistry = TtsModelRegistry()
