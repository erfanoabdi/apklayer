// android.view.autofill.VirtualViewFillInfo -- the autofill hints a view
// declares for one of its VIRTUAL children, handed to
// AutofillManager.notifyVirtualViewsReady. Source: frameworks/base
// core/java/android/view/autofill/VirtualViewFillInfo.java (android16-release).
//
// Mac.apk bridges no autofill service (AutofillManager.isEnabled() is honestly
// false), so nothing consumes the info -- but it is a plain immutable value
// object on AOSP too and round-trips exactly what the caller set.
package android.view.autofill;

public final class VirtualViewFillInfo {
    private final String[] mAutofillHints;

    private VirtualViewFillInfo(Builder b) { mAutofillHints = b.mAutofillHints; }

    public String[] getAutofillHints() { return mAutofillHints; }

    public static final class Builder {
        private String[] mAutofillHints;
        public Builder() {}
        public Builder setAutofillHints(String... autofillHints) {
            mAutofillHints = autofillHints;
            return this;
        }
        public VirtualViewFillInfo build() { return new VirtualViewFillInfo(this); }
    }
}
