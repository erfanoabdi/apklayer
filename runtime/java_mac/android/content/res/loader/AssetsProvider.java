package android.content.res.loader;
import android.content.res.AssetFileDescriptor;
/** Supplies file descriptors for assets an app-loaded resource table references.
 *  API 30. Optional callback; the default supplies nothing.
 *  Source: frameworks/base/core/java/android/content/res/loader/AssetsProvider.java */
public interface AssetsProvider {
    // AOSP returns AssetFileDescriptor (offset+length into a shared file), not a
    // bare ParcelFileDescriptor.
    default AssetFileDescriptor loadAssetFd(String path, int accessMode) { return null; }
}
