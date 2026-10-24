package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

// AOSP declares `public class EditorInfo implements InputType, Parcelable`.
// The InputType half is a CONSTANT interface (android/text/InputType.java), so
// it adds no members -- which is exactly why a member-level API diff cannot see
// it missing, while at runtime every `instanceof InputType` on an EditorInfo
// goes the wrong way and every `EditorInfo.TYPE_CLASS_TEXT` reference through
// the inherited-constant path fails to resolve. Same class of defect as a
// missing `implements Parcelable`.
public class EditorInfo implements android.text.InputType, Parcelable {
    // Public fields per AOSP frameworks/base/.../view/inputmethod/EditorInfo.java.
    // IME-using engines (libGDX text input, Unity TextField) read/write these
    // directly; absent, callers threw NoSuchFieldError when configuring the
    // soft keyboard. Constants mirror android.text.InputType / EditorInfo.IME_*.
    public int inputType;
    public int imeOptions;
    public String privateImeOptions;
    public CharSequence actionLabel;
    public int actionId;
    public int initialSelStart = -1;
    public int initialSelEnd = -1;
    public int initialCapsMode;
    public CharSequence hintText;
    public CharSequence label;
    public String packageName;
    public int fieldId;
    public String fieldName;
    public android.os.Bundle extras;

    public static final int IME_ACTION_UNSPECIFIED = 0x00000000;
    public static final int IME_ACTION_NONE        = 0x00000001;
    public static final int IME_ACTION_GO          = 0x00000002;
    public static final int IME_ACTION_SEARCH      = 0x00000003;
    public static final int IME_ACTION_SEND        = 0x00000004;
    public static final int IME_ACTION_NEXT        = 0x00000005;
    public static final int IME_ACTION_DONE        = 0x00000006;
    public static final int IME_ACTION_PREVIOUS    = 0x00000007;
    // AOSP EditorInfo.java (core/java/android/view/inputmethod/EditorInfo.java):
    // IME_NULL is the "no options" imeOptions/internalImeOptions value; the six
    // IME_FLAG_* below round out the flag bits an IME reads out of imeOptions
    // (personalized-learning opt-out, prev/next navigation availability, the
    // accessory/enter-action suppression pair, and ASCII-only input request).
    public static final int IME_NULL                         = 0x00000000;
    public static final int IME_FLAG_NO_FULLSCREEN           = 0x02000000;
    public static final int IME_FLAG_NAVIGATE_PREVIOUS       = 0x04000000;
    public static final int IME_FLAG_NAVIGATE_NEXT           = 0x08000000;
    public static final int IME_FLAG_NO_EXTRACT_UI           = 0x10000000;
    public static final int IME_FLAG_NO_ACCESSORY_ACTION     = 0x20000000;
    public static final int IME_FLAG_NO_ENTER_ACTION         = 0x40000000;
    public static final int IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000;
    public static final int IME_FLAG_FORCE_ASCII             = 0x80000000;
    public static final int IME_MASK_ACTION                  = 0x000000ff;
    // AOSP internalImeOptions ("@hide", used by the framework itself, not
    // meant for app use) -- included for full field-parity since app code
    // that iterates EditorInfo's declared fields via reflection (some
    // serialization/logging libraries do) would otherwise see a shape
    // mismatch versus a real device.
    public int internalImeOptions = IME_NULL;

    // v1.0.426 Tier-2: the surrounding-text snapshot AndroidX/Compose pass to the
    // IME so it has context (autocorrect, caps mode) without a round-trip. AOSP
    // stores them on these fields; setInitialSurroundingText caches the text +
    // selection. Compose's EditorInfo setup + AppCompat call this; a missing
    // method NoSuchMethodError'd during input start.
    public CharSequence initialSurroundingText;

    public EditorInfo() {}

    public void setInitialSurroundingText(CharSequence sourceText) {
        setInitialSurroundingSubText(sourceText, 0);
    }
    public void setInitialSurroundingSubText(CharSequence subText, int subTextStart) {
        if (subText == null) return;
        initialSurroundingText = subText;
        if (initialSelStart < 0 || initialSelEnd < 0) {
            int len = subText.length();
            initialSelStart = subTextStart + len;
            initialSelEnd = subTextStart + len;
        }
    }
    public CharSequence getInitialTextBeforeCursor(int length, int flags) {
        if (initialSurroundingText == null) return "";
        int end = Math.max(0, Math.min(initialSelStart, initialSurroundingText.length()));
        int start = Math.max(0, end - Math.max(0, length));
        return initialSurroundingText.subSequence(start, end);
    }
    public CharSequence getInitialTextAfterCursor(int length, int flags) {
        if (initialSurroundingText == null) return "";
        int start = Math.max(0, Math.min(initialSelEnd, initialSurroundingText.length()));
        int end = Math.min(initialSurroundingText.length(), start + Math.max(0, length));
        return initialSurroundingText.subSequence(start, end);
    }
    public CharSequence getInitialSelectedText(int flags) {
        if (initialSurroundingText == null) return null;
        int a = Math.max(0, Math.min(initialSelStart, initialSurroundingText.length()));
        int b = Math.max(0, Math.min(initialSelEnd, initialSurroundingText.length()));
        if (a == b) return null;
        return initialSurroundingText.subSequence(Math.min(a, b), Math.max(a, b));
    }

    public final void makeCompatible(int targetSdkVersion) {}
    public void dump(android.util.Printer pw, String prefix) {}

    // ---- AOSP API-36 surface completion (android.view lane) ------------------

    /** AOSP EditorInfo.getInitialSurroundingText(int, int, int) (API 31) --
     *  the SurroundingText form of the three getInitialText* helpers above,
     *  built from the same initialSurroundingText this class already stores.
     *  Argument validation and the clamping arithmetic are AOSP's own
     *  (EditorInfo.java:1111). */
    public SurroundingText getInitialSurroundingText(int beforeLength, int afterLength,
            int flags) {
        if (beforeLength < 0) {
            throw new IllegalArgumentException("beforeLength must not be negative");
        }
        if (afterLength < 0) {
            throw new IllegalArgumentException("afterLength must not be negative");
        }
        if (initialSurroundingText == null) return null;

        final int length = initialSurroundingText.length();
        int selStart = Math.max(0, Math.min(initialSelStart, length));
        int selEnd = Math.max(0, Math.min(initialSelEnd, length));
        if (selStart > selEnd) { int tmp = selStart; selStart = selEnd; selEnd = tmp; }

        final int before = Math.min(beforeLength, selStart);
        final int after = Math.min(selEnd + afterLength, length);
        final int offset = selStart - before;
        final CharSequence newText =
                (flags & InputConnection.GET_TEXT_WITH_STYLES) != 0
                        ? initialSurroundingText.subSequence(offset, after)
                        : TextUtils.substring(initialSurroundingText, offset, after);
        final int newSelEnd = Math.min(selEnd - offset, length);
        return new SurroundingText(newText, before, newSelEnd, offset);
    }

    /** AOSP EditorInfo.setInitialToolType(int) / getInitialToolType() (API 34):
     *  the MotionEvent.TOOL_TYPE_* that started the editing session, so an IME
     *  can tell a stylus tap from a finger tap. Mac.apk delivers every pointer
     *  as a finger (the Cocoa bridge synthesises TOOL_TYPE_FINGER), so the
     *  default is TOOL_TYPE_UNKNOWN until a caller sets it, which is AOSP's own
     *  default for a session started without a MotionEvent. */
    private int mInitialToolType = android.view.MotionEvent.TOOL_TYPE_UNKNOWN;
    public void setInitialToolType(int toolType) { mInitialToolType = toolType; }
    public int getInitialToolType() { return mInitialToolType; }

    /** AOSP EditorInfo.setAutofillId(AutofillId) / getAutofillId() (API 36):
     *  the id of the view this EditorInfo describes, so an IME can correlate an
     *  input session with an autofill session. */
    private android.view.autofill.AutofillId mAutofillId;
    public void setAutofillId(android.view.autofill.AutofillId autofillId) {
        mAutofillId = autofillId;
    }
    public android.view.autofill.AutofillId getAutofillId() { return mAutofillId; }

    /** AOSP EditorInfo.setWritingToolsEnabled(boolean) / isWritingToolsEnabled()
     *  (API 36): whether the editor opts into system writing tools. Default
     *  false, as on AOSP. */
    private boolean mWritingToolsEnabled;
    public void setWritingToolsEnabled(boolean enabled) { mWritingToolsEnabled = enabled; }
    public boolean isWritingToolsEnabled() { return mWritingToolsEnabled; }

    /** AOSP EditorInfo.getSupportedHandwritingGestures() /
     *  getSupportedHandwritingGesturePreviews() (API 34): what the setters two
     *  methods up already accept. Those setters DISCARDED their argument, so the
     *  getters could only have answered a value the editor never set; they now
     *  store it. AOSP's getters are @NonNull, so an editor that declared nothing
     *  gets an empty collection, never null. */
    private java.util.List<Class<? extends android.view.inputmethod.HandwritingGesture>>
            mSupportedHandwritingGestures = java.util.Collections.emptyList();
    private java.util.Set<Class<? extends android.view.inputmethod.PreviewableHandwritingGesture>>
            mSupportedHandwritingGesturePreviews = java.util.Collections.emptySet();
    public java.util.List<Class<? extends android.view.inputmethod.HandwritingGesture>>
            getSupportedHandwritingGestures() {
        return mSupportedHandwritingGestures;
    }
    public java.util.Set<Class<? extends android.view.inputmethod.PreviewableHandwritingGesture>>
            getSupportedHandwritingGesturePreviews() {
        return mSupportedHandwritingGesturePreviews;
    }

    public boolean isStylusHandwritingEnabled() { return false; }

    public void setStylusHandwritingEnabled(boolean enabled) { }
    public void setSupportedHandwritingGestures(java.util.List<Class<? extends android.view.inputmethod.HandwritingGesture>> gestures) {
        mSupportedHandwritingGestures = gestures != null
                ? gestures : java.util.Collections.<Class<? extends android.view.inputmethod.HandwritingGesture>>emptyList();
    }
    public void setSupportedHandwritingGesturePreviews(java.util.Set<Class<? extends android.view.inputmethod.PreviewableHandwritingGesture>> previews) {
        mSupportedHandwritingGesturePreviews = previews != null
                ? previews : java.util.Collections.<Class<? extends android.view.inputmethod.PreviewableHandwritingGesture>>emptySet();
    }

    public String[] contentMimeTypes;
    public android.os.LocaleList hintLocales;

    // ---- Parcelable ----------------------------------------------------
    //
    // AOSP declares EditorInfo Parcelable (core/java/android/view/inputmethod/
    // EditorInfo.java, android16-release) and an IME crosses a Parcel with one
    // on every input start. Omitting the interface is not cosmetic in this
    // runtime: Bundle.getParcelable ends
    //   return (v instanceof Parcelable) ? (T) v : null;
    // while the matching put is unchecked under VM-wide -Xverify:none, so an
    // app that stashed an EditorInfo in a Bundle (AppCompat and Compose both
    // do, around configuration changes) got NULL back and no exception.
    //
    // FIELD ORDER follows AOSP's writeToParcel exactly for the fields we
    // declare. It is deliberately NOT wire-compatible with a device, and does
    // not need to be: nothing outside this process ever reads our Parcel bytes.
    // The fields AOSP writes that we do not model are simply absent rather than
    // written as placeholders, because a placeholder would have to be read back
    // by someone -- these are: mInitialToolType, autofillId (needs AutofillId),
    // mSupportedHandwritingGesture{,Preview}Types, targetInputMethodUser (needs
    // UserHandle.writeToParcel) and mWritingToolsEnabled. Adding any of them
    // here means adding the field first; that is a surface change, not a
    // parcel change.
    //
    // hintLocales is written as its language-tag string rather than delegated,
    // because our android.os.LocaleList is not itself Parcelable (AOSP's is).
    // toLanguageTags()/forLanguageTags() is the lossless round trip AOSP itself
    // uses for the same data elsewhere.

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(inputType);
        dest.writeInt(imeOptions);
        dest.writeString(privateImeOptions);
        dest.writeInt(internalImeOptions);
        TextUtils.writeToParcel(actionLabel, dest, flags);
        dest.writeInt(actionId);
        dest.writeInt(initialSelStart);
        dest.writeInt(initialSelEnd);
        dest.writeInt(initialCapsMode);
        TextUtils.writeToParcel(hintText, dest, flags);
        TextUtils.writeToParcel(label, dest, flags);
        dest.writeString(packageName);
        dest.writeInt(fieldId);
        dest.writeString(fieldName);
        dest.writeBundle(extras);
        TextUtils.writeToParcel(initialSurroundingText, dest, flags);
        dest.writeString(hintLocales == null ? null : hintLocales.toLanguageTags());
        dest.writeStringArray(contentMimeTypes);
    }

    public static final Parcelable.Creator<EditorInfo> CREATOR =
            new Parcelable.Creator<EditorInfo>() {
        @Override public EditorInfo createFromParcel(Parcel source) {
            EditorInfo res = new EditorInfo();
            res.inputType = source.readInt();
            res.imeOptions = source.readInt();
            res.privateImeOptions = source.readString();
            res.internalImeOptions = source.readInt();
            res.actionLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            res.actionId = source.readInt();
            res.initialSelStart = source.readInt();
            res.initialSelEnd = source.readInt();
            res.initialCapsMode = source.readInt();
            res.hintText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            res.label = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            res.packageName = source.readString();
            res.fieldId = source.readInt();
            res.fieldName = source.readString();
            res.extras = source.readBundle();
            res.initialSurroundingText =
                    TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            String tags = source.readString();
            res.hintLocales = (tags == null || tags.isEmpty())
                    ? null : android.os.LocaleList.forLanguageTags(tags);
            res.contentMimeTypes = source.createStringArray();
            return res;
        }
        @Override public EditorInfo[] newArray(int size) {
            return new EditorInfo[size];
        }
    };
}



