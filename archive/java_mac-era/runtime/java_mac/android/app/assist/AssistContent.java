// android.app.assist.AssistContent -- holds content the app supplies to
// the Assistant (a web URI, structured JSON-LD data, an Intent, ClipData).
// Mirrors AOSP frameworks/base/core/java/android/app/assist/AssistContent.java
// (public API since API 23). Reddit's BaseActivity overrides
// Activity.onProvideAssistContent(AssistContent) (and references the type in
// several lifecycle paths), so the class MUST exist on the bootclasspath or
// loading the activity NoClassDefFoundErrors at EVERY lifecycle stage
// (onCreate/onStart/onResume) -- which is exactly what blocked Reddit once the
// DI graph came up. We implement the full observable API; the Parcel round
// trip is minimal (Assist/voice-interaction isn't wired on Mac.apk, so these
// objects are constructed and read but effectively never marshalled).
package android.app.assist;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class AssistContent implements Parcelable {
    private boolean mIsAppProvidedIntent = false;
    private boolean mIsAppProvidedWebUri = false;
    private Intent mIntent;
    private String mStructuredData;
    private ClipData mClipData;
    private Uri mUri;
    private final Bundle mExtras;

    public AssistContent() {
        mExtras = new Bundle();
    }

    /** @hide */
    public void setDefaultIntent(Intent intent) {
        mIntent = intent;
        mIsAppProvidedIntent = false;
        setWebUri(null);
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            mUri = intent.getData();
        }
    }

    public void setIntent(Intent intent) {
        mIsAppProvidedIntent = true;
        mIntent = intent;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public boolean isAppProvidedIntent() {
        return mIsAppProvidedIntent;
    }

    public void setClipData(ClipData clip) {
        mClipData = clip;
    }

    public ClipData getClipData() {
        return mClipData;
    }

    public void setStructuredData(String structuredData) {
        mStructuredData = structuredData;
    }

    public String getStructuredData() {
        return mStructuredData;
    }

    public void setWebUri(Uri uri) {
        mIsAppProvidedWebUri = uri != null;
        mUri = uri;
    }

    public Uri getWebUri() {
        return mUri;
    }

    public boolean isAppProvidedWebUri() {
        return mIsAppProvidedWebUri;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Minimal marshalling: the structured-data string + the two
        // app-provided flags. Assist isn't a live surface on Mac.apk, so a
        // faithful-but-light round trip is sufficient.
        dest.writeInt(mIsAppProvidedIntent ? 1 : 0);
        dest.writeInt(mIsAppProvidedWebUri ? 1 : 0);
        dest.writeString(mStructuredData);
        dest.writeString(mUri != null ? mUri.toString() : null);
    }

    AssistContent(Parcel in) {
        mIsAppProvidedIntent = in.readInt() != 0;
        mIsAppProvidedWebUri = in.readInt() != 0;
        mStructuredData = in.readString();
        String uri = in.readString();
        mUri = uri != null ? Uri.parse(uri) : null;
        mExtras = new Bundle();
    }

    public static final Parcelable.Creator<AssistContent> CREATOR =
            new Parcelable.Creator<AssistContent>() {
        @Override
        public AssistContent createFromParcel(Parcel in) {
            return new AssistContent(in);
        }

        @Override
        public AssistContent[] newArray(int size) {
            return new AssistContent[size];
        }
    };

    /** AOSP setSessionTransferUri(Uri) / getSessionTransferUri() (API 35):
     *  "Set a session transfer URI... to transfer the current app session to
     *  another device", read by the assistant when the user asks to continue on
     *  another device. It is a plain app-set value the app also reads back --
     *  AOSP stores it in the same extras Bundle the rest of this class uses --
     *  so it round-trips here rather than being dropped. Nothing in Mac.apk
     *  consumes it: there is no assistant and no cross-device session transfer,
     *  which is the same state as a phone with no assistant configured. */
    public void setSessionTransferUri(android.net.Uri uri) { mSessionTransferUri = uri; }
    public android.net.Uri getSessionTransferUri() { return mSessionTransferUri; }
    private android.net.Uri mSessionTransferUri;

    /** AOSP AssistContent.EXTRA_APP_FUNCTION_DATA (API 36): the extras key
     *  under which app-function data travels with an assist request. */
    public static final String EXTRA_APP_FUNCTION_DATA =
            "android.app.assist.extra.APP_FUNCTION_DATA";
}
