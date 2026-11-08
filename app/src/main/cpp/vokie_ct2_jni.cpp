#include <jni.h>
#include <android/log.h>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <chrono>
#include <ctranslate2/translator.h>
#include <sentencepiece_processor.h>

namespace {
constexpr char TAG[] = "VOKIE][CT2";
struct Session { std::unique_ptr<ctranslate2::Translator> translator; sentencepiece::SentencePieceProcessor tokenizer; std::mutex mutex; };
void fail(JNIEnv* env, const char* message) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message); }
const char* lang(const std::string& code) {
  // SIH 10-language NLLB-200 language tags
  if (code == "EN") return "eng_Latn";
  if (code == "HI") return "hin_Deva";
  if (code == "GU") return "guj_Gujr";
  if (code == "MR") return "mar_Deva";
  if (code == "KN") return "kan_Knda";
  if (code == "ML") return "mal_Mlym";
  if (code == "TA") return "tam_Taml";
  if (code == "TE") return "tel_Telu";
  if (code == "OR") return "ory_Orya";
  if (code == "BN") return "ben_Beng";
  return nullptr;
}
}
extern "C" JNIEXPORT jlong JNICALL Java_com_vokie_translation_Ctranslate2Native_nativeLoadModel(JNIEnv* env, jobject, jstring modelDir) {
  try {
    if (!modelDir) { fail(env, "CT2 model directory is required"); return 0; }
    const char* path = env->GetStringUTFChars(modelDir, nullptr);
    if (!path) return 0;
    auto session = std::make_unique<Session>();
    const std::string root(path); env->ReleaseStringUTFChars(modelDir, path);
    const auto status = session->tokenizer.Load(root + "/sentencepiece.bpe.model");
    if (!status.ok()) throw std::runtime_error(status.ToString());
    session->translator = std::make_unique<ctranslate2::Translator>(root, ctranslate2::Device::CPU, ctranslate2::ComputeType::DEFAULT, std::vector<int>{0});
    __android_log_print(ANDROID_LOG_INFO, TAG, "CT2 model loaded");
    return reinterpret_cast<jlong>(session.release());
  } catch (const std::exception& e) { fail(env, e.what()); return 0; }
}
extern "C" JNIEXPORT jstring JNICALL Java_com_vokie_translation_Ctranslate2Native_nativeTranslate(JNIEnv* env, jobject, jlong handle, jstring source, jstring target, jstring text) {
  try {
    auto* session = reinterpret_cast<Session*>(handle);
    if (!session || !source || !target || !text) { fail(env, "CT2 session, languages, and text are required"); return nullptr; }
    const char *s = env->GetStringUTFChars(source, nullptr), *t = env->GetStringUTFChars(target, nullptr), *input = env->GetStringUTFChars(text, nullptr);
    if (!s || !t || !input) return nullptr;
    const char *src = lang(s), *tgt = lang(t); std::string value(input);
    env->ReleaseStringUTFChars(source,s); env->ReleaseStringUTFChars(target,t); env->ReleaseStringUTFChars(text,input);
    if (!src || !tgt) throw std::invalid_argument("Unsupported NLLB language");
    const auto request_started = std::chrono::steady_clock::now();
    std::vector<std::string> pieces;
    auto encode_started = std::chrono::steady_clock::now();
    auto status = session->tokenizer.Encode(value, &pieces); if (!status.ok()) throw std::runtime_error(status.ToString());
    pieces.insert(pieces.begin(), src); pieces.emplace_back("</s>");
    const auto encode_ms = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - encode_started).count() / 1000.0;
    ctranslate2::TranslationOptions options;
    // Beam 1 (greedy) for short rescue sentences; beam 4 only for longer text.
    options.beam_size = (pieces.size() <= 20) ? 1 : 4;
    options.max_decoding_length = 256;
    auto infer_started = std::chrono::steady_clock::now();
    ctranslate2::TranslationResult result;
    { std::lock_guard<std::mutex> lock(session->mutex); result = session->translator->translate_batch({pieces}, {{tgt}}, options).at(0); }
    const auto infer_ms = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - infer_started).count() / 1000.0;
    auto output = result.hypotheses.at(0); if (!output.empty() && output.front() == tgt) output.erase(output.begin());
    auto decode_started = std::chrono::steady_clock::now();
    std::string decoded; status = session->tokenizer.Decode(output, &decoded); if (!status.ok()) throw std::runtime_error(status.ToString());
    const auto decode_ms = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - decode_started).count() / 1000.0;
    const auto total_ms = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - request_started).count() / 1000.0;
    __android_log_print(ANDROID_LOG_INFO, TAG, "CT2_PROFILE chars=%zu pieces=%zu beam=%d tokenizer_encode_ms=%.3f inference_combined_ms=%.3f tokenizer_decode_ms=%.3f total_native_ms=%.3f", value.size(), pieces.size(), options.beam_size, encode_ms, infer_ms, decode_ms, total_ms);
    return env->NewStringUTF(decoded.c_str());
  } catch (const std::exception& e) { fail(env, e.what()); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL Java_com_vokie_translation_Ctranslate2Native_nativeUnloadModel(JNIEnv*, jobject, jlong handle) { delete reinterpret_cast<Session*>(handle); }
