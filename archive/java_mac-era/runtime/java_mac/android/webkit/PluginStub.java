package android.webkit;

import android.content.Context;
import android.view.View;

// android.webkit.PluginStub (AOSP API-8, removed/deprecated long ago).
// Legacy NPAPI plugin entry point. Kept only so old APKs that reference the
// interface resolve it. Ref (historical):
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/PluginStub.java
@Deprecated
public interface PluginStub {

    View getEmbeddedView(int npp, Context context);

    View getFullScreenView(int npp, Context context);
}
