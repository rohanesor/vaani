#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <string>
#include <thread>
#ifdef VOKIE_DEBUG_WHISPER_BENCHMARKS
#include <sys/system_properties.h>
#endif
#include "ggml-cpu.h"
#include "whisper.h"

namespace {
constexpr const char * TAG = "VOKIE][STT";
// The app owns one inference context, so one process-local abort flag is sufficient.
std::atomic<bool> g_abort_requested{false};

bool whisper_abort_callback(void *) {
    return g_abort_requested.load(std::memory_order_relaxed);
}

int64_t monotonic_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

#ifdef VOKIE_DEBUG_WHISPER_BENCHMARKS
int debug_property_int(const char * name, int fallback) {
    char value[PROP_VALUE_MAX] = {};
    if (__system_property_get(name, value) <= 0) return fallback;
    char * end = nullptr;
    const long parsed = std::strtol(value, &end, 10);
    return end != value && *end == '\0' ? static_cast<int>(parsed) : fallback;
}
#endif

struct InferenceTrace {
    int encoder_begin_count = 0;
    int64_t first_encoder_begin_ms = 0;
    int64_t second_encoder_begin_ms = 0;
};

bool trace_encoder_begin(whisper_context *, whisper_state *, void * user_data) {
    auto * trace = static_cast<InferenceTrace *>(user_data);
    const int64_t now = monotonic_ms();
    if (trace->encoder_begin_count == 0) trace->first_encoder_begin_ms = now;
    if (trace->encoder_begin_count == 1) trace->second_encoder_begin_ms = now;
    ++trace->encoder_begin_count;
    return true;
}

void throw_java(JNIEnv * env, const char * type, const char * message) {
    jclass clazz = env->FindClass(type);
    if (clazz != nullptr) env->ThrowNew(clazz, message);
}

whisper_context * context_from(jlong handle) {
    return reinterpret_cast<whisper_context *>(handle);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_vokie_stt_WhisperNative_nativeInit(JNIEnv * env, jobject, jstring model_path) {
    if (model_path == nullptr) {
        throw_java(env, "java/lang/IllegalArgumentException", "Model path is required");
        return 0;
    }
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context * context = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(model_path, path);
    if (context == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "whisper.cpp could not load the model");
        return 0;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "runtimeConfig backend=cpu gpu=false neon=%d armFma=%d fp16Va=%d dotprod=%d model=%s ftype=%d audioCtx=%d textCtx=%d multilingual=%d system=%s",
        ggml_cpu_has_neon(), ggml_cpu_has_arm_fma(), ggml_cpu_has_fp16_va(), ggml_cpu_has_dotprod(),
        whisper_model_type_readable(context), whisper_model_ftype(context), whisper_model_n_audio_ctx(context),
        whisper_model_n_text_ctx(context), whisper_is_multilingual(context), whisper_print_system_info());
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vokie_stt_WhisperNative_nativeTranscribe(
        JNIEnv * env,
        jobject,
        jlong handle,
        jfloatArray samples,
        jstring language,
        jint requested_threads) {
    whisper_context * context = context_from(handle);
    if (context == nullptr || samples == nullptr || language == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "Whisper context, audio, and language are required");
        return nullptr;
    }

    const char * language_code = env->GetStringUTFChars(language, nullptr);
    if (language_code == nullptr) return nullptr;
    if (std::string(language_code) != "auto" && whisper_lang_id(language_code) < 0) {
        env->ReleaseStringUTFChars(language, language_code);
        throw_java(env, "java/lang/IllegalArgumentException", "Unsupported Whisper language");
        return nullptr;
    }

    const jsize sample_count = env->GetArrayLength(samples);
    jfloat * audio = env->GetFloatArrayElements(samples, nullptr);
    if (audio == nullptr) {
        env->ReleaseStringUTFChars(language, language_code);
        return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = language_code;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.suppress_nst = true;
    g_abort_requested.store(false, std::memory_order_relaxed);
    params.abort_callback = whisper_abort_callback;
    params.abort_callback_user_data = nullptr;
    const unsigned int available = std::max(1u, std::thread::hardware_concurrency());
    int benchmark_threads = requested_threads;
    bool benchmark_no_timestamps = false;
    int benchmark_audio_ctx = 0;
    // audio_ctx = 0 means use full model context (1500 for whisper-tiny).
    // Dynamic audio_ctx was attempted but causes decoder degeneration
    // (repetitive garbage output) on whisper-tiny even with a 128-frame
    // floor. Reverted to full context until a model upgrade enables it.
    benchmark_audio_ctx = 0;
#ifdef VOKIE_DEBUG_WHISPER_BENCHMARKS
    benchmark_threads = debug_property_int("debug.vokie.whisper_threads", requested_threads);
    benchmark_no_timestamps = debug_property_int("debug.vokie.whisper_no_timestamps", 0) == 1;
    int override_ctx = debug_property_int("debug.vokie.whisper_audio_ctx", 0);
    if (override_ctx > 0) benchmark_audio_ctx = override_ctx;
#endif
    if (benchmark_audio_ctx > whisper_n_audio_ctx(context)) benchmark_audio_ctx = whisper_n_audio_ctx(context);
    params.audio_ctx = benchmark_audio_ctx;
    params.n_threads = std::max(1, std::min(static_cast<int>(available), benchmark_threads));
    if (benchmark_no_timestamps) params.no_timestamps = true;
    InferenceTrace trace;
    params.encoder_begin_callback = trace_encoder_begin;
    params.encoder_begin_callback_user_data = &trace;
    const int64_t native_start_ms = monotonic_ms();
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "inferenceConfig threadsRequested=%d benchmarkThreads=%d hardwareConcurrency=%u threadsUsed=%d language=%s strategy=greedy translate=false noContext=%s noTimestamps=%s maxTokens=%d audioCtxRequested=%d audioCtxEffective=%d offsetMs=%d durationMs=%d singleSegment=%s tokenTimestamps=%s suppressBlank=%s suppressNst=%s temperature=%.1f temperatureInc=%.1f",
        requested_threads, benchmark_threads, available, params.n_threads, language_code,
        params.no_context ? "true" : "false",
        params.no_timestamps ? "true" : "false", params.max_tokens, params.audio_ctx,
        params.audio_ctx > 0 ? params.audio_ctx : whisper_n_audio_ctx(context), params.offset_ms, params.duration_ms,
        params.single_segment ? "true" : "false", params.token_timestamps ? "true" : "false",
        params.suppress_blank ? "true" : "false", params.suppress_nst ? "true" : "false",
        params.temperature, params.temperature_inc);

    whisper_reset_timings(context);
    const int result = whisper_full(context, params, audio, sample_count);
    const int64_t native_end_ms = monotonic_ms();
    whisper_timings * timings = whisper_get_timings(context);
    const int64_t language_id_ms = trace.second_encoder_begin_ms > 0
        ? trace.second_encoder_begin_ms - native_start_ms : -1;
    const int64_t transcription_ms = trace.second_encoder_begin_ms > 0
        ? native_end_ms - trace.second_encoder_begin_ms : -1;
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "inferenceTiming nativeFullMs=%lld encoderBegins=%d languageIdMs=%lld transcriptionMs=%lld encodeAvgMs=%.2f decodeAvgMs=%.2f sampleAvgMs=%.2f batchDecodeAvgMs=%.2f promptAvgMs=%.2f",
        static_cast<long long>(native_end_ms - native_start_ms), trace.encoder_begin_count,
        static_cast<long long>(language_id_ms), static_cast<long long>(transcription_ms),
        timings->encode_ms, timings->decode_ms, timings->sample_ms, timings->batchd_ms, timings->prompt_ms);
    whisper_print_timings(context);
    delete timings;
    env->ReleaseFloatArrayElements(samples, audio, JNI_ABORT);
    env->ReleaseStringUTFChars(language, language_code);
    if (result != 0) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "whisper_full failed with code %d", result);
        throw_java(env, "java/lang/IllegalStateException", "whisper.cpp inference failed");
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(context);
    for (int i = 0; i < segments; ++i) {
        const char * segment = whisper_full_get_segment_text(context, i);
        if (segment != nullptr) text.append(segment);
    }
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_vokie_stt_WhisperNative_nativeAbort(JNIEnv *, jobject) {
    g_abort_requested.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vokie_stt_WhisperNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    whisper_context * context = context_from(handle);
    if (context != nullptr) whisper_free(context);
}
