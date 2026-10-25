package android.view;
import android.os.Bundle;
import android.view.autofill.AutofillId;
/** The structure a View exports for autofill / content-capture / assist. AOSP's
 *  ViewStructure is abstract and the platform supplies the impl; an app only ever
 *  RECEIVES one (in onProvideAutofillStructure / onProvideStructure) and SETS
 *  fields on it. Mac.apk has no autofill/assist consumer, so this is a concrete
 *  sink: setters are accepted and dropped, getExtras/newChild return usable
 *  objects. Source: frameworks/base/core/java/android/view/ViewStructure.java */
public class ViewStructure {
    private Bundle mExtras;
    public ViewStructure() {}
    public void setId(int id, String packageName, String typeName, String entryName) {}
    public void setDimens(int left, int top, int scrollX, int scrollY, int width, int height) {}
    public void setTransformation(android.graphics.Matrix matrix) {}
    public void setElevation(float elevation) {}
    public void setAlpha(float alpha) {}
    public void setVisibility(int visibility) {}
    public void setEnabled(boolean state) {}
    public void setClickable(boolean state) {}
    public void setLongClickable(boolean state) {}
    public void setContextClickable(boolean state) {}
    public void setFocusable(boolean state) {}
    public void setFocused(boolean state) {}
    public void setAccessibilityFocused(boolean state) {}
    public void setCheckable(boolean state) {}
    public void setChecked(boolean state) {}
    public void setSelected(boolean state) {}
    public void setActivated(boolean state) {}
    public void setOpaque(boolean opaque) {}
    public void setClassName(String className) {}
    public void setContentDescription(CharSequence contentDescription) {}
    public void setText(CharSequence text) {}
    public void setText(CharSequence text, int selectionStart, int selectionEnd) {}
    public void setTextStyle(float size, int fgColor, int bgColor, int style) {}
    public void setTextLines(int[] charOffsets, int[] baselines) {}
    public void setHint(CharSequence hint) {}
    public CharSequence getText() { return null; }
    public int getTextSelectionStart() { return -1; }
    public int getTextSelectionEnd() { return -1; }
    public CharSequence getHint() { return null; }
    public Bundle getExtras() { if (mExtras == null) mExtras = new Bundle(); return mExtras; }
    public boolean hasExtras() { return mExtras != null; }
    public void setChildCount(int num) {}
    public int addChildCount(int num) { return 0; }
    public int getChildCount() { return 0; }
    public ViewStructure newChild(int index) { return new ViewStructure(); }
    public ViewStructure asyncNewChild(int index) { return new ViewStructure(); }
    public void asyncCommit() {}
    public android.view.autofill.AutofillId getAutofillId() { return null; }
    public void setAutofillId(AutofillId id) {}
    public void setAutofillId(AutofillId parentId, int virtualId) {}
    public void setAutofillType(int type) {}
    public void setAutofillHints(String[] hint) {}
    public void setAutofillValue(android.view.autofill.AutofillValue value) {}
    public void setAutofillOptions(CharSequence[] options) {}
    public void setInputType(int inputType) {}
    public void setDataIsSensitive(boolean sensitive) {}
    public void setWebDomain(String domain) {}
    public void setLocaleList(android.os.LocaleList localeList) {}
    public android.view.ViewStructure.HtmlInfo.Builder newHtmlInfoBuilder(String tagName) { return null; }
    public void setHtmlInfo(android.view.ViewStructure.HtmlInfo htmlInfo) {}
    /** Opaque HTML descriptor (setHtmlInfo). */
    public abstract static class HtmlInfo {
        public abstract String getTag();
        public abstract java.util.List<android.util.Pair<String, String>> getAttributes();
        public abstract static class Builder {
            public abstract Builder addAttribute(String name, String value);
            public abstract HtmlInfo build();
        }
    }

    public void setMaxTextLength(int maxLength) { }

    // ---- AOSP API-36 surface completion (android.view lane) -----------------
    // Same contract as the setters above: this ViewStructure is a sink (see the
    // class header), so each one is accepted and dropped. They exist because a
    // widget that fills in a structure -- TextView, EditText, Compose's
    // semantics-to-autofill bridge -- calls them unconditionally, and an absent
    // one is a NoSuchMethodError in the middle of onProvideAutofillStructure
    // rather than a missing autofill feature.

    /** AOSP ViewStructure.setMinTextEms(int) / setMaxTextEms(int) (API 26):
     *  the view's android:minEms / android:maxEms, so an autofill service can
     *  size its own UI. */
    public void setMinTextEms(int minEms) { }
    public void setMaxTextEms(int maxEms) { }

    /** AOSP ViewStructure.setTextIdEntry(String) (API 28) /
     *  setHintIdEntry(String) (API 31): the RESOURCE ENTRY NAME behind the
     *  view's android:text / android:hint, which is what lets an autofill
     *  service match a field across locales. */
    public void setTextIdEntry(String entryName) { }
    public void setHintIdEntry(String entryName) { }

    /** AOSP ViewStructure.setImportantForAutofill(int) (API 28): mirrors
     *  View.getImportantForAutofill() into the structure. */
    public void setImportantForAutofill(int mode) { }

    /** AOSP ViewStructure.setReceiveContentMimeTypes(String[]) (API 31):
     *  the MIME types View.getReceiveContentMimeTypes() reports. */
    public void setReceiveContentMimeTypes(String[] mimeTypes) { }

    /** AOSP ViewStructure.setPendingCredentialRequest(GetCredentialRequest,
     *  OutcomeReceiver) / getPendingCredentialRequest() /
     *  getPendingCredentialCallback() / clearCredentialManagerRequest()
     *  (API 35): carries View.setPendingCredentialRequest into the structure so
     *  an autofill service can fulfil it through Credential Manager. Mac.apk
     *  bridges no credential provider (see View.setPendingCredentialRequest),
     *  but the request round-trips so a caller that writes then reads it back
     *  sees its own value rather than null. */
    private android.credentials.GetCredentialRequest mPendingCredentialRequest;
    private android.os.OutcomeReceiver<android.credentials.GetCredentialResponse,
            android.credentials.GetCredentialException> mPendingCredentialCallback;
    public void setPendingCredentialRequest(android.credentials.GetCredentialRequest request,
            android.os.OutcomeReceiver<android.credentials.GetCredentialResponse,
                    android.credentials.GetCredentialException> callback) {
        mPendingCredentialRequest = request;
        mPendingCredentialCallback = callback;
    }
    public android.credentials.GetCredentialRequest getPendingCredentialRequest() {
        return mPendingCredentialRequest;
    }
    public android.os.OutcomeReceiver<android.credentials.GetCredentialResponse,
            android.credentials.GetCredentialException> getPendingCredentialCallback() {
        return mPendingCredentialCallback;
    }
    public void clearCredentialManagerRequest() {
        mPendingCredentialRequest = null;
        mPendingCredentialCallback = null;
    }
}

