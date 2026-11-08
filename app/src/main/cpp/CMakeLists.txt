cmake_minimum_required(VERSION 3.22.1)
project(vokie_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# arm64-v8a's AArch64 baseline includes ASIMD/NEON. Do not require optional
# dot-product or FP16 instructions across the whole APK.
#
# AGP's Windows Debug native configuration appends only -g to third-party
# targets, ignoring CMAKE_<LANG>_FLAGS_DEBUG set here. Apply optimization to the
# actual upstream compute targets after they are created below.
# whisper.cpp has several large translation units; cap native compile concurrency for low-memory CI/dev hosts.
set_property(GLOBAL PROPERTY JOB_POOLS vokie_compile_pool=2)
set(CMAKE_JOB_POOL_COMPILE vokie_compile_pool)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/whisper.cpp whisper-build EXCLUDE_FROM_ALL)

# whisper.cpp/ggml are the inference hot path. Keep their Debug APK objects
# optimized; retaining symbols does not require compiling the kernels at -O0.
foreach(target ggml-base ggml-cpu ggml whisper)
    target_compile_options(${target} PRIVATE $<$<CONFIG:Debug>:-O3>)
endforeach()

add_library(vokie_whisper SHARED vokie_whisper_jni.cpp)
target_link_libraries(vokie_whisper PRIVATE whisper log android)
target_compile_options(vokie_whisper PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden)
target_compile_definitions(vokie_whisper PRIVATE $<$<CONFIG:Debug>:VOKIE_DEBUG_WHISPER_BENCHMARKS>)
# Android 16 KB page-size compatibility (API 35+ / 16K-page devices).
# Keep PT_LOAD segments aligned for both legacy 4K and modern 16K systems.
target_link_options(vokie_whisper PRIVATE
    -Wl,--gc-sections
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

# CT2 4.8.2 is built reproducibly by scripts/build-ct2-android-arm64.sh.
# Generated archives stay outside Git; configure explicit roots in the developer build environment.
set(CT2_SOURCE_DIR "D:/vibe/ct2-4.8.2-source" CACHE PATH "CTranslate2 4.8.2 source checkout")
set(CT2_BUILD_DIR "D:/vibe/ct2-android-build" CACHE PATH "CTranslate2 Android arm64 build directory")
set(SPM_SOURCE_DIR "D:/vibe/sentencepiece-source" CACHE PATH "SentencePiece 0.2.1 source checkout")
set(SPM_BUILD_DIR "D:/vibe/sentencepiece-android-build" CACHE PATH "SentencePiece Android arm64 build directory")
if(EXISTS "${CT2_BUILD_DIR}/libctranslate2.a" AND EXISTS "${SPM_BUILD_DIR}/src/libsentencepiece.a")
    file(GLOB CT2_RUY_ARCHIVES "${CT2_BUILD_DIR}/third_party/ruy/ruy/*.a")
    set(CT2_CPUINFO_ARCHIVE "${CT2_BUILD_DIR}/third_party/ruy/third_party/cpuinfo/libcpuinfo.a")
    set(CT2_CLOG_ARCHIVE "${CT2_BUILD_DIR}/third_party/ruy/third_party/cpuinfo/deps/clog/libclog.a")
    if(NOT EXISTS "${CT2_CPUINFO_ARCHIVE}" OR NOT EXISTS "${CT2_CLOG_ARCHIVE}")
        message(FATAL_ERROR "CT2 Ruy cpuinfo archives are missing; rebuild the pinned CT2 dependency tree")
    endif()
    add_library(vokie_ct2 SHARED vokie_ct2_jni.cpp)
    target_include_directories(vokie_ct2 PRIVATE "${CT2_SOURCE_DIR}/include" "${CT2_SOURCE_DIR}/third_party" "${SPM_SOURCE_DIR}/src" "${SPM_SOURCE_DIR}")
    target_link_libraries(vokie_ct2 PRIVATE "${CT2_BUILD_DIR}/libctranslate2.a" "${SPM_BUILD_DIR}/src/libsentencepiece.a" ${CT2_RUY_ARCHIVES} "${CT2_CPUINFO_ARCHIVE}" "${CT2_CLOG_ARCHIVE}" log android atomic)
    target_compile_options(vokie_ct2 PRIVATE -O3 -fvisibility=hidden)
    target_link_options(vokie_ct2 PRIVATE -Wl,--gc-sections)
else()
    message(WARNING "CT2 native archives unavailable; vokie_ct2 will not be built. Run scripts/build-ct2-android-arm64.sh.")
endif()
