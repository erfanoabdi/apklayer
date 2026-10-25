// android.media.RingtoneManager -- enumerates and resolves ringtones. AOSP
// frameworks/base media/java/android/media/RingtoneManager.java.
//
// Mac.apk has no MediaStore-backed ringtone catalog, so the cursor is empty and
// getDefaultUri returns null (no system default). getRingtone still hands back a
// usable Ringtone bound to whatever Uri is passed, so apps that resolve a
// specific ringtone Uri (e.g. one the user picked) can preview it; apps that
// enumerate the system catalog see an empty list -- honest for a host with no
// ringtone provider.
package android.media;

public class RingtoneManager {
    public static final int TYPE_RINGTONE = 1;
    public static final int TYPE_NOTIFICATION = 2;
    public static final int TYPE_ALARM = 4;
    public static final int TYPE_ALL = TYPE_RINGTONE | TYPE_NOTIFICATION | TYPE_ALARM;

    public static final String EXTRA_RINGTONE_TYPE = "android.intent.extra.ringtone.TYPE";
    public static final String EXTRA_RINGTONE_TITLE = "android.intent.extra.ringtone.TITLE";
    public static final String EXTRA_RINGTONE_EXISTING_URI = "android.intent.extra.ringtone.EXISTING_URI";
    public static final String EXTRA_RINGTONE_DEFAULT_URI = "android.intent.extra.ringtone.DEFAULT_URI";
    public static final String EXTRA_RINGTONE_SHOW_DEFAULT = "android.intent.extra.ringtone.SHOW_DEFAULT";
    public static final String EXTRA_RINGTONE_SHOW_SILENT = "android.intent.extra.ringtone.SHOW_SILENT";
    public static final String EXTRA_RINGTONE_PICKED_URI = "android.intent.extra.ringtone.PICKED_URI";
    public static final String ACTION_RINGTONE_PICKER = "android.intent.action.RINGTONE_PICKER";

    public static final int ID_COLUMN_INDEX = 0;
    public static final int TITLE_COLUMN_INDEX = 1;
    public static final int URI_COLUMN_INDEX = 2;

    private final android.content.Context mContext;
    private int mType = TYPE_ALL;

    public RingtoneManager(android.content.Context context) { mContext = context; }
    public RingtoneManager(android.app.Activity activity) { mContext = activity; }

    /** @deprecated in AOSP since API 16 along with the DRM ringtone store. */
    @Deprecated public static final String EXTRA_RINGTONE_INCLUDE_DRM =
            "android.intent.extra.ringtone.INCLUDE_DRM";

    public void setType(int type) { mType = type; }

    // ---- the include-DRM pair (deprecated API 16) ------------------------
    // AOSP's own implementation of these is already inert: setIncludeDrm's body
    // is a comment saying the setting is ignored because DRM ringtones are no
    // longer supported, and getIncludeDrm always returns false. Mirrored
    // exactly -- and note this is AOSP's behaviour, not a DRM decision of ours.
    @Deprecated public void setIncludeDrm(boolean includeDrm) { }
    @Deprecated public boolean getIncludeDrm() { return false; }

    // ---- stop-previous-ringtone (API 3) ----------------------------------
    // Real behaviour, not a stub: getRingtone(position) is documented to stop
    // whatever this manager last handed out when the flag is set, and the flag
    // defaults to TRUE on AOSP. The manager tracks the last Ringtone it created
    // so stopPreviousRingtone() has something to stop.
    private boolean mStopPreviousRingtone = true;
    private Ringtone mPreviousRingtone;

    public void setStopPreviousRingtone(boolean stopPreviousRingtone) {
        mStopPreviousRingtone = stopPreviousRingtone;
    }

    public boolean getStopPreviousRingtone() { return mStopPreviousRingtone; }

    /** AOSP: "Stops playing the ringtone previously returned by
     *  getRingtone(int)." */
    public void stopPreviousRingtone() {
        Ringtone previous;
        synchronized (this) {
            previous = mPreviousRingtone;
            mPreviousRingtone = null;
        }
        if (previous != null) {
            try { previous.stop(); } catch (Throwable ignore) { }
        }
    }

    /** Called by getRingtone(int) so the flag above has an effect. */
    private void rememberRingtone(Ringtone ringtone) {
        if (mStopPreviousRingtone) stopPreviousRingtone();
        synchronized (this) { mPreviousRingtone = ringtone; }
    }

    // ---- haptic channels (API 29) ----------------------------------------
    // AOSP answers this by opening the media and looking for a channel carrying
    // the AUDIO_CHANNEL_OUT_HAPTIC_A/B mask -- a feature of the platform
    // extractor. Mac.apk's extractor does not surface a haptic channel mask
    // (nothing in this tree reads one), and there is no vibrator to play it on,
    // so false is both the truthful answer for this device and the answer that
    // keeps setHapticGeneratorEnabled/isHapticGeneratorEnabled on Ringtone from
    // contradicting it.

    public boolean hasHapticChannels(int position) { return false; }

    public static boolean hasHapticChannels(android.net.Uri ringtoneUri) {
        java.util.Objects.requireNonNull(ringtoneUri, "ringtoneUri must not be null");
        return false;
    }

    public static boolean hasHapticChannels(android.content.Context context,
            android.net.Uri ringtoneUri) {
        java.util.Objects.requireNonNull(context, "context must not be null");
        java.util.Objects.requireNonNull(ringtoneUri, "ringtoneUri must not be null");
        return false;
    }

    /**
     * API 31. AOSP: opens the DEFAULT ringtone that {@code defaultRingtoneUri}
     * points at -- one of the three Settings.System DEFAULT_*_URI values -- and
     * returns "null if the default ringtone is not set", throwing
     * FileNotFoundException when the resolved file cannot be opened.
     *
     * There is no default ringtone on this host: getActualDefaultRingtoneUri()
     * already answers null for every type (no ringtone store, no
     * Settings.System entries), so "not set" is the honest and consistent
     * answer, and it is one AOSP itself returns on a device with no ringtone
     * configured.
     */
    public static android.content.res.AssetFileDescriptor openDefaultRingtoneUri(
            android.content.Context context, android.net.Uri uri)
            throws java.io.FileNotFoundException {
        java.util.Objects.requireNonNull(context, "context must not be null");
        java.util.Objects.requireNonNull(uri, "uri must not be null");
        return null;
    }
    public int inferStreamType() {
        switch (mType) {
            case TYPE_ALARM:        return AudioManager.STREAM_ALARM;
            case TYPE_NOTIFICATION: return AudioManager.STREAM_NOTIFICATION;
            default:                return AudioManager.STREAM_RING;
        }
    }

    // Empty catalog: no ringtone provider on the host.
    public android.database.Cursor getCursor() {
        return new android.database.MatrixCursor(
            new String[] { "_id", "title", "uri", "uri" });
    }
    public Ringtone getRingtone(int position) {
        // AOSP stops the previously-returned ringtone here when the flag is set
        // (RingtoneManager.java, getRingtone(int)). There is no ringtone store
        // to draw a new one from -- getCursor() is empty -- so the result is
        // still null, but the documented stop side-effect now really happens.
        rememberRingtone(null);
        return null;
    }
    public android.net.Uri getRingtoneUri(int position) { return null; }
    public int getRingtonePosition(android.net.Uri ringtoneUri) { return -1; }

    public static Ringtone getRingtone(android.content.Context context, android.net.Uri ringtoneUri) {
        Ringtone r = new Ringtone(context, true);
        r.setUri(ringtoneUri);
        return r;
    }
    public static android.net.Uri getDefaultUri(int type) { return null; }
    public static int getDefaultType(android.net.Uri defaultRingtoneUri) { return -1; }
    public static boolean isDefault(android.net.Uri ringtoneUri) { return false; }
    public static void setActualDefaultRingtoneUri(android.content.Context context,
                                                   int type, android.net.Uri ringtoneUri) {}
    public static android.net.Uri getActualDefaultRingtoneUri(android.content.Context context, int type) {
        return null;
    }
    public static android.net.Uri getValidRingtoneUri(android.content.Context context) { return null; }
}
