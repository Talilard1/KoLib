# KoLib's on-device language models: llama.cpp (third_party/llama.cpp, MIT) and a thin layer over it.
# Built for 64-bit devices only; the app does without it on the rest.
cmake_minimum_required(VERSION 3.22)
project(kolib-llm LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp" CACHE PATH "llama.cpp's source")

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(GGML_SYSTEM_ARCH "x86")
else()
    message(FATAL_ERROR "KoLib's language models are built for arm64-v8a and x86_64 only, not ${ANDROID_ABI}")
endif()

# A build for each kind of processor, picked when the app runs: an old phone and a new tablet each get the
# fastest code they can run.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_SRC} llama)

add_library(kolib-llm SHARED kl_llm.cpp kl_jni.cpp)
target_include_directories(kolib-llm PRIVATE
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/vendor)
target_link_libraries(kolib-llm llama llama-common log)
