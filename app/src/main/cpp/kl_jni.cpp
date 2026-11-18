// The app's side of the engine: dev.kolib.app.ai.LlamaNative. Text crosses as UTF-8 bytes, so any character survives.
#include <jni.h>

#include <string>
#include <vector>

#include "kl_llm.h"

namespace {

std::string g_load_error;

std::string bytes(JNIEnv * env, jbyteArray a) {
    if (!a) return "";
    const jsize n = env->GetArrayLength(a);
    std::string s((size_t) n, '\0');
    if (n > 0) env->GetByteArrayRegion(a, 0, n, (jbyte *) &s[0]);
    return s;
}

jbyteArray bytes(JNIEnv * env, const std::string & s) {
    jbyteArray a = env->NewByteArray((jsize) s.size());
    if (a && !s.empty()) env->SetByteArrayRegion(a, 0, (jsize) s.size(), (const jbyte *) s.data());
    return a;
}

std::string chars(JNIEnv * env, jstring s) {
    if (!s) return "";
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

kl::Engine * engine(jlong handle) { return reinterpret_cast<kl::Engine *>(handle); }

std::vector<kl::Message> conversation(JNIEnv * env, jobjectArray roles, jobjectArray contents) {
    std::vector<kl::Message> messages;
    const jsize n = env->GetArrayLength(roles);
    for (jsize i = 0; i < n; i++) {
        auto role = (jstring) env->GetObjectArrayElement(roles, i);
        auto content = (jbyteArray) env->GetObjectArrayElement(contents, i);
        messages.push_back({chars(env, role), bytes(env, content)});
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(content);
    }
    return messages;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL Java_dev_kolib_app_ai_LlamaNative_init(JNIEnv * env, jobject, jstring dir) {
    kl::init(chars(env, dir));
}

JNIEXPORT jlong JNICALL Java_dev_kolib_app_ai_LlamaNative_load(JNIEnv * env, jobject, jstring path, jint n_ctx, jint n_threads, jboolean copy_weights) {
    g_load_error.clear();
    kl::Engine * e = kl::load(chars(env, path), n_ctx, n_threads, copy_weights == JNI_TRUE, g_load_error);
    return reinterpret_cast<jlong>(e);
}

JNIEXPORT jlong JNICALL Java_dev_kolib_app_ai_LlamaNative_copiedSize(JNIEnv * env, jobject, jstring path) {
    return (jlong) kl::copied_size(chars(env, path));
}

JNIEXPORT jbyteArray JNICALL Java_dev_kolib_app_ai_LlamaNative_loadError(JNIEnv * env, jobject) {
    return bytes(env, g_load_error);
}

JNIEXPORT void JNICALL Java_dev_kolib_app_ai_LlamaNative_unload(JNIEnv *, jobject, jlong handle) {
    kl::unload(engine(handle));
}

JNIEXPORT void JNICALL Java_dev_kolib_app_ai_LlamaNative_stop(JNIEnv *, jobject, jlong handle) {
    if (handle) engine(handle)->stop.store(true);
}

JNIEXPORT void JNICALL Java_dev_kolib_app_ai_LlamaNative_yieldAhead(JNIEnv *, jobject, jlong handle) {
    if (handle) engine(handle)->yield.store(true);
}

JNIEXPORT jint JNICALL Java_dev_kolib_app_ai_LlamaNative_lastRead(JNIEnv *, jobject, jlong handle) {
    return handle ? (jint) engine(handle)->last_read : 0;
}

JNIEXPORT jint JNICALL Java_dev_kolib_app_ai_LlamaNative_countTokens(JNIEnv * env, jobject, jlong handle, jbyteArray text) {
    return kl::count_tokens(engine(handle), bytes(env, text));
}

JNIEXPORT jbyteArray JNICALL Java_dev_kolib_app_ai_LlamaNative_describe(JNIEnv * env, jobject, jlong handle) {
    return bytes(env, kl::describe(engine(handle)));
}

JNIEXPORT jbyteArray JNICALL Java_dev_kolib_app_ai_LlamaNative_error(JNIEnv * env, jobject, jlong handle) {
    return bytes(env, handle ? engine(handle)->error : std::string());
}

JNIEXPORT jint JNICALL Java_dev_kolib_app_ai_LlamaNative_prefill(
        JNIEnv * env, jobject, jlong handle, jobjectArray roles, jobjectArray contents) {
    if (!handle || !roles || !contents) return kl::FAILED;
    std::vector<kl::Message> messages = conversation(env, roles, contents);
    // The question's place, at the end.
    messages.push_back({"user", kl::ASK_MARK});
    return (jint) kl::prefill(engine(handle), messages);
}

JNIEXPORT jint JNICALL Java_dev_kolib_app_ai_LlamaNative_generate(
        JNIEnv * env, jobject, jlong handle, jobjectArray roles, jobjectArray contents,
        jint max_tokens, jfloat temperature, jobject sink) {
    if (!handle || !roles || !contents || !sink) return kl::FAILED;
    std::vector<kl::Message> messages = conversation(env, roles, contents);
    jclass cls = env->GetObjectClass(sink);
    jmethodID progress = env->GetMethodID(cls, "progress", "(F)Z");
    jmethodID text = env->GetMethodID(cls, "text", "([B)Z");
    if (!progress || !text) return kl::FAILED;
    auto on_progress = [&](float p) {
        jboolean go = env->CallBooleanMethod(sink, progress, (jfloat) p);
        if (env->ExceptionCheck()) return false;
        return go == JNI_TRUE;
    };
    auto on_text = [&](const std::string & s) {
        jbyteArray a = bytes(env, s);
        jboolean go = env->CallBooleanMethod(sink, text, a);
        env->DeleteLocalRef(a);
        if (env->ExceptionCheck()) return false;
        return go == JNI_TRUE;
    };
    return (jint) kl::generate(engine(handle), messages, max_tokens, temperature, on_progress, on_text);
}

}  // extern "C"
