// com.google.android.gms.maps.MapsInitializer -- one-time SDK init helper.
// On a real device this loads the Maps dynamite module; we have no module,
// so initialize() reports SUCCESS (the SDK classes are already on the
// classpath and our map is purely client-side). Matches the documented
// return of ConnectionResult.SUCCESS.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/MapsInitializer
package com.google.android.gms.maps;

import android.content.Context;
import com.google.android.gms.common.ConnectionResult;

public final class MapsInitializer {
    private MapsInitializer() {}

    public static int initialize(Context context) {
        return ConnectionResult.SUCCESS;
    }

    public static int initialize(Context context, Renderer preferredRenderer,
                                 OnMapsSdkInitializedCallback callback) {
        if (callback != null) {
            // The legacy renderer is what an inert client-side map most
            // resembles; report it as the one that initialized.
            callback.onMapsSdkInitialized(Renderer.LEGACY);
        }
        return ConnectionResult.SUCCESS;
    }

    // Mirrors the SDK's renderer-selection enum (Maps SDK 18.0.0+).
    public enum Renderer {
        LEGACY,
        LATEST
    }

    public interface OnMapsSdkInitializedCallback {
        void onMapsSdkInitialized(Renderer renderer);
    }
}
