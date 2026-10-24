// android.media.MediaCrypto -- a handle to a DRM crypto session used to decrypt
// protected codec input (MediaCodec.configure / queueSecureInputBuffer).
// Mirrors AOSP frameworks/base media/java/android/media/MediaCrypto.java.
//
// Mac.apk has no DRM backend (AMediaDrm_* is absent), but the TYPE must resolve:
// MediaCodec.configure's published descriptor names MediaCrypto, so without this
// class a guest's configure(format, surface, crypto, flags) call would throw
// NoSuchMethodError on descriptor mismatch. isCryptoSchemeSupported answers
// "no scheme supported" honestly so an app probing for Widevine falls back.
package android.media;

import java.util.UUID;

// AOSP fidelity: the deprecation usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("deprecation")
public final class MediaCrypto {
    // AOSP's constructor calls native_setup, which asks the media server to
    // createPlugin(uuid). With no plugin for the scheme, initCheck() reports
    // NO_INIT and the JNI layer raises
    //   MediaCryptoException("Failed to instantiate crypto object: <err>")
    // (media/jni/android_media_MediaCrypto.cpp). That is the ordinary outcome
    // on any device that does not carry a CDM for the requested UUID, and it
    // is the answer that has to be given here: isCryptoSchemeSupported below
    // reports false for every scheme, so a constructor that quietly succeeded
    // would hand the caller a crypto object that decrypts nothing and take it
    // past the fallback branch it wrote for exactly this case.
    public MediaCrypto(UUID uuid, byte[] initData) throws MediaCryptoException {
        throw new MediaCryptoException(
            "Failed to instantiate crypto object: no crypto plugin for scheme " + uuid);
    }

    // No crypto scheme is backed; report unsupported so callers degrade.
    public static boolean isCryptoSchemeSupported(UUID uuid) { return false; }

    // AOSP: whether the underlying session needs a secure decoder. We have none.
    public boolean requiresSecureDecoderComponent(String mime) { return false; }

    public void setMediaDrmSession(byte[] sessionId) throws MediaCryptoException {}

    public void release() {}

    @Override protected void finalize() {}
}
