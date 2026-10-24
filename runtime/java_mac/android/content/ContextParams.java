// android.content.ContextParams (API 31) -- the properties a Context created
// with Context.createContext(ContextParams) carries: its attribution tag and
// the next link in its AttributionSource chain.
//
// AOSP ref: frameworks/base/core/java/android/content/ContextParams.java
// (android16-release). Ported verbatim apart from the @SystemApi
// renounced-permissions half, which is not in the public API-36 surface and
// whose setter's only behaviour is a SecurityException gated on a signature
// permission -- it is kept as an empty set so isRenouncedPermission() answers
// the same "no" a normal app gets on a device.
package android.content;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class ContextParams {
    private final String mAttributionTag;
    private final AttributionSource mNext;
    private final Set<String> mRenouncedPermissions;
    private final boolean mShouldRegisterAttributionSource;

    /** AOSP's @hide EMPTY singleton; ContextImpl uses it as the default. */
    public static final ContextParams EMPTY = new ContextParams.Builder().build();

    private ContextParams(String attributionTag, AttributionSource next,
                          Set<String> renouncedPermissions, boolean shouldRegister) {
        mAttributionTag = attributionTag;
        mNext = next;
        mRenouncedPermissions = (renouncedPermissions != null)
                ? renouncedPermissions : Collections.<String>emptySet();
        mShouldRegisterAttributionSource = shouldRegister;
    }

    public String getAttributionTag() { return mAttributionTag; }

    public AttributionSource getNextAttributionSource() { return mNext; }

    public boolean shouldRegisterAttributionSource() { return mShouldRegisterAttributionSource; }

    /** AOSP @hide/@SystemApi pair, kept because they are public in bytecode. */
    public Set<String> getRenouncedPermissions() { return mRenouncedPermissions; }
    public boolean isRenouncedPermission(String permission) {
        return mRenouncedPermissions.contains(permission);
    }

    public static final class Builder {
        private String mAttributionTag;
        private Set<String> mRenouncedPermissions = Collections.<String>emptySet();
        private AttributionSource mNext;
        private boolean mShouldRegisterAttributionSource;

        public Builder() {
        }

        /** AOSP's copy constructor deliberately does NOT copy
         *  mShouldRegisterAttributionSource -- that omission is upstream's,
         *  not a transcription slip, so it is preserved here. */
        public Builder(ContextParams params) {
            Objects.requireNonNull(params);
            mAttributionTag = params.mAttributionTag;
            mRenouncedPermissions = params.mRenouncedPermissions;
            mNext = params.mNext;
        }

        public Builder setAttributionTag(String attributionTag) {
            mAttributionTag = attributionTag;
            return this;
        }

        public Builder setNextAttributionSource(AttributionSource next) {
            mNext = next;
            return this;
        }

        public Builder setShouldRegisterAttributionSource(boolean shouldRegister) {
            mShouldRegisterAttributionSource = shouldRegister;
            return this;
        }

        public ContextParams build() {
            return new ContextParams(mAttributionTag, mNext, mRenouncedPermissions,
                                     mShouldRegisterAttributionSource);
        }
    }
}
