#include <android/content/AttributionSourceState.h>
#include <binder/Binder.h>
#include <binder/Parcel.h>
#include <jni.h>
#include <utils/String8.h>
#include <utils/String16.h>
#include <memory>
#include <mutex>
#include <unordered_set>
#include <vector>

namespace {
using android::content::AttributionSourceState;
struct Token { jweak java; android::sp<android::BBinder> binder; };
std::mutex lock;
std::vector<Token> tokens;
std::unordered_set<android::Parcel*> parcels;
void error(JNIEnv* env, const char* text) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), text);
}
std::optional<std::string> string(JNIEnv* env, jstring value) {
    if (!value) return std::nullopt;
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (!chars) return std::nullopt;
    android::String16 utf16(reinterpret_cast<const char16_t*>(chars), env->GetStringLength(value));
    std::string result(android::String8(utf16).c_str());
    env->ReleaseStringChars(value, chars);
    return result;
}
android::sp<android::IBinder> token(JNIEnv* env, jobject value) {
    if (!value) return nullptr;
    if (!env->IsInstanceOf(value, env->FindClass("android/os/Binder"))) {
        error(env, "Native attribution currently requires local Binder identity; remote proxy bridging is not available");
        return nullptr;
    }
    for (auto it = tokens.begin(); it != tokens.end();) {
        if (env->IsSameObject(it->java, nullptr)) {
            env->DeleteWeakGlobalRef(it->java); it = tokens.erase(it);
        } else {
            if (env->IsSameObject(it->java, value)) return it->binder;
            ++it;
        }
    }
    jweak weak = env->NewWeakGlobalRef(value);
    if (!weak) return nullptr;
    auto binder = android::sp<android::BBinder>::make();
    tokens.push_back({weak, binder});
    return binder;
}
bool state(JNIEnv* env, jobject value, AttributionSourceState* out, unsigned depth = 0) {
    if (!value || depth > 32) { error(env, "Invalid or cyclic attribution chain"); return false; }
    if (env->PushLocalFrame(32) < 0) return false;
    jclass cls = env->GetObjectClass(value);
    jfieldID pid = env->GetFieldID(cls, "pid", "I"), uid = env->GetFieldID(cls, "uid", "I"),
        device = env->GetFieldID(cls, "deviceId", "I"),
        package = env->GetFieldID(cls, "packageName", "Ljava/lang/String;"),
        tag = env->GetFieldID(cls, "attributionTag", "Ljava/lang/String;"),
        binder = env->GetFieldID(cls, "token", "Landroid/os/IBinder;"),
        permissions = env->GetFieldID(cls, "renouncedPermissions", "[Ljava/lang/String;"),
        next = env->GetFieldID(cls, "next", "[Landroid/content/AttributionSourceState;");
    if (env->ExceptionCheck() || !pid || !uid || !device || !package || !tag || !binder || !permissions || !next) {
        env->PopLocalFrame(nullptr); return false;
    }
    out->pid = env->GetIntField(value, pid); out->uid = env->GetIntField(value, uid);
    out->deviceId = env->GetIntField(value, device);
    out->packageName = string(env, static_cast<jstring>(env->GetObjectField(value, package)));
    out->attributionTag = string(env, static_cast<jstring>(env->GetObjectField(value, tag)));
    out->token = token(env, env->GetObjectField(value, binder));
    auto revoked = static_cast<jobjectArray>(env->GetObjectField(value, permissions));
    if (revoked) {
        out->renouncedPermissions.emplace();
        for (jsize i = 0; i < env->GetArrayLength(revoked); i++) {
            auto text = static_cast<jstring>(env->GetObjectArrayElement(revoked, i));
            out->renouncedPermissions->push_back(string(env, text)); env->DeleteLocalRef(text);
        }
    }
    auto chain = static_cast<jobjectArray>(env->GetObjectField(value, next));
    if (chain) for (jsize i = 0; i < env->GetArrayLength(chain) && !env->ExceptionCheck(); i++) {
        jobject child = env->GetObjectArrayElement(chain, i);
        out->next.emplace_back();
        if (!state(env, child, &out->next.back(), depth + 1)) { env->PopLocalFrame(nullptr); return false; }
        env->DeleteLocalRef(child);
    }
    bool valid = !env->ExceptionCheck();
    env->PopLocalFrame(nullptr);
    return valid;
}
}
extern "C" jlong apklayer_attribution_create(JNIEnv* env, jclass, jobject source) {
    std::lock_guard guard(lock);
    AttributionSourceState converted;
    if (!state(env, source, &converted)) return 0;
    auto parcel = std::make_unique<android::Parcel>();
    if (converted.writeToParcel(parcel.get()) != android::OK) {
        error(env, "Cannot serialize native attribution Parcel"); return 0;
    }
    parcel->setDataPosition(0);
    auto result = parcel.release(); parcels.insert(result);
    return reinterpret_cast<jlong>(result);
}
extern "C" void apklayer_attribution_destroy(JNIEnv* env, jclass, jlong peer) {
    std::lock_guard guard(lock);
    auto parcel = reinterpret_cast<android::Parcel*>(peer);
    if (!parcels.erase(parcel)) { error(env, "Unknown native attribution Parcel"); return; }
    delete parcel;
}
extern "C" jboolean apklayer_attribution_matches(JNIEnv* env, jclass, jlong peer, jobject source) {
    std::lock_guard guard(lock);
    auto parcel = reinterpret_cast<android::Parcel*>(peer);
    if (!parcels.count(parcel)) { error(env, "Unknown native attribution Parcel"); return false; }
    AttributionSourceState expected, actual;
    if (!state(env, source, &expected)) return false;
    size_t position = parcel->dataPosition(); parcel->setDataPosition(0);
    auto status = actual.readFromParcel(parcel); parcel->setDataPosition(position);
    return status == android::OK && actual == expected;
}
extern "C" jint apklayer_attribution_active(JNIEnv*, jclass) {
    std::lock_guard guard(lock);
    return parcels.size();
}
