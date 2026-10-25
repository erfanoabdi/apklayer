package android.provider;

import android.content.Context;
import android.content.Intent;

/**
 * The legacy browser contract.
 *
 * Only the Intent extras and sendString() survive as public API; the bookmark
 * and history tables were removed from the platform long ago. sendString() is
 * pure Intent construction, reproduced exactly.
 *
 * Source: frameworks/base/core/java/android/provider/Browser.java
 */
public class Browser {

    /** AOSP declares a public no-arg constructor on this contract class. */
    public Browser() { }

    public static final String EXTRA_APPLICATION_ID = "com.android.browser.application_id";
    public static final String EXTRA_HEADERS = "com.android.browser.headers";
    public static final String EXTRA_CREATE_NEW_TAB = "create_new_tab";
    public static final String INITIAL_ZOOM_LEVEL = "browser.initialZoomLevel";

    /**
     * AOSP builds an ACTION_SEND text intent and starts the chooser. Verbatim;
     * whether any activity handles it is the ordinary resolution question.
     */
    public static final void sendString(Context c, String s) {
        sendString(c, s, "");
    }

    /** @hide AOSP's chooser-title overload. */
    public static final void sendString(Context c, String s, String chooserDialogTitle) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, s);
        try {
            Intent i = Intent.createChooser(send, chooserDialogTitle);
            // AOSP: the caller may not be an Activity, so a new task is required.
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (android.content.ActivityNotFoundException ex) {
            // AOSP swallows this: "if no app handles it, do nothing".
        }
    }
}
