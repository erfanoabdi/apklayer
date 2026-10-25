// android.hardware.lights.LightsRequest -- a batch of (Light -> LightState)
// overrides built via Builder and submitted through
// LightsManager.LightsSession#requestLights. Pure data holder; nothing in
// Mac.apk consumes the contents (see LightsManager.java), but the shape
// must match AOSP exactly so an app's Builder call chain compiles and links
// against our classes unmodified.
//
// Shape verified against AOSP (fetched 2026-08-12, android13-release):
// frameworks/base/core/java/android/hardware/lights/LightsRequest.java
package android.hardware.lights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LightsRequest {
    final Map<Light, LightState> mRequests = new HashMap<Light, LightState>();
    final List<Integer> mLightIds = new ArrayList<Integer>();
    final List<LightState> mLightStates = new ArrayList<LightState>();

    /** Only constructible via {@link Builder#build()}. */
    private LightsRequest(Map<Light, LightState> requests) {
        mRequests.putAll(requests);
        List<Light> lights = new ArrayList<Light>(mRequests.keySet());
        for (int i = 0; i < lights.size(); i++) {
            Light light = lights.get(i);
            mLightIds.add(i, light.getId());
            mLightStates.add(i, mRequests.get(light));
        }
    }

    public List<Integer> getLights() { return mLightIds; }
    public List<LightState> getLightStates() { return mLightStates; }
    public Map<Light, LightState> getLightsAndStates() { return mRequests; }

    public static final class Builder {
        final Map<Light, LightState> mChanges = new HashMap<Light, LightState>();

        public Builder addLight(Light light, LightState state) {
            if (light == null) throw new NullPointerException("light must not be null");
            if (state == null) throw new NullPointerException("state must not be null");
            mChanges.put(light, state);
            return this;
        }

        /** @deprecated use {@link #addLight(Light, LightState)}. */
        @Deprecated
        public Builder setLight(Light light, LightState state) {
            return addLight(light, state);
        }

        /** AOSP maps the light to a null state here (rather than removing
         *  the key) to record "clear this light" as distinct from "never
         *  mentioned"; preserved verbatim even though our requestLights()
         *  never inspects it. */
        public Builder clearLight(Light light) {
            if (light == null) throw new NullPointerException("light must not be null");
            mChanges.put(light, null);
            return this;
        }

        public LightsRequest build() {
            return new LightsRequest(mChanges);
        }
    }
}
