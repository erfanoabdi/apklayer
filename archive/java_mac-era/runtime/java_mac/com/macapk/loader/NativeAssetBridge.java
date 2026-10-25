// NativeAssetBridge -- glue between the translated dylib's
// AAssetManager_* calls and Mac.apk's Java AssetManager.
//
// Cocos2d-x (and most other native game engines) read sprite atlases,
// shaders, audio, and config from the APK via the NDK's AAssetManager
// API. On real Android the system passes the activity's AssetManager
// across the JNI boundary; on Mac.apk the translated dylib calls the
// shim's AAssetManager_open(...), which calls back here via
// JNI::FindClass + CallStaticObjectMethod, which delegates to the
// Application's AssetManager (already wired to read APK ZIP entries).
//
// Returning a byte[] copies the asset bytes once into a Java array.
// The shim then memcpy's them into a C-side buffer the engine reads
// from. That's a doubled copy on the boot path; if it ever shows up
// hot we can switch to a direct ByteBuffer with GetDirectBufferAddress
// for zero-copy.
package com.macapk.loader;

import android.app.Application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class NativeAssetBridge {
    private static final String TAG = "Mac.apk.AssetBridge";

    private NativeAssetBridge() {}

    /**
     * Read an asset by relative path inside the APK's assets/ tree
     * (e.g. "atlas.txt", "shaders/sprite_vsh.glsl"). Returns the
     * bytes, or null if the asset can't be opened.
     *
     * Called from the bionic shim's AAssetManager_open via JNI:
     *   FindClass("com/macapk/loader/NativeAssetBridge")
     *   GetStaticMethodID("openAsset", "(Ljava/lang/String;)[B")
     *   CallStaticObjectMethod(...)
     */
    public static byte[] openAsset(String name) {
        if (name == null || name.isEmpty()) return null;
        Application app = Application.getInstance();
        if (app == null) {
            NativeLog.w(TAG, "openAsset(" + name + "): no Application instance");
            return null;
        }
        try (InputStream is = app.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            byte[] result = out.toByteArray();
            NativeLog.i(TAG, "openAsset(" + name + ") -> " + result.length + " bytes");
            return result;
        } catch (IOException e) {
            NativeLog.w(TAG, "openAsset(" + name + ") failed: " + e);
            return null;
        }
    }

    /**
     * Probe whether an asset exists without reading it. Some engines
     * (cocos2d-x in particular) ask first and only read if found.
     */
    public static boolean hasAsset(String name) {
        if (name == null || name.isEmpty()) return false;
        Application app = Application.getInstance();
        if (app == null) return false;
        try (InputStream is = app.getAssets().open(name)) {
            return is != null;
        } catch (IOException e) {
            return false;
        }
    }
}
