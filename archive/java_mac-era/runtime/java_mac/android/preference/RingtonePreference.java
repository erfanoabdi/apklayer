// android.preference.RingtonePreference -- a Preference row that opens the
// system ringtone picker and persists the chosen Uri as a string.
//
// AOSP reference (aosp-mirror android16-release, fetched 2026-08-24):
//   frameworks/base/core/java/android/preference/RingtonePreference.java
//   public class RingtonePreference extends Preference
//           implements PreferenceManager.OnActivityResultListener
//   get/setRingtoneType, get/setShowDefault, get/setShowSilent
//   protected void onPrepareRingtonePickerIntent(Intent)
//   protected Uri onRestoreRingtone()  /  protected void onSaveRingtone(Uri)
//   public boolean onActivityResult(int, int, Intent)
//
// WHY IT EXISTS HERE: an alarm clock, a task app or a messenger that offers
// "notification sound" declares <RingtonePreference> in its settings XML. Absent
// the class, PreferenceInflater cannot resolve the tag and the WHOLE settings
// screen fails to inflate -- one missing row would take out the entire screen.
//
// WHAT IT HONESTLY DOES ON MAC.APK. The persistence half is fully real: the
// chosen Uri round-trips through SharedPreferences (or a PreferenceDataStore)
// exactly as on a device, so an app that reads its own key back gets what it
// wrote, and a defaultValue declared in XML is persisted on first run. The
// PICKER half depends on something outside this package: onClick fires
// startActivityForResult with ACTION_RINGTONE_PICKER, and whether an activity
// answers that is up to Mac.apk's PackageManager/Intent routing. If nothing
// answers, the row is inert and the stored value is untouched -- which is the
// same thing that happens on an Android device with no ringtone picker
// installed. Nothing here pretends a picker ran or invents a Uri; per the
// no-shortcuts rule, a capability we cannot honour is not advertised.
// RingtoneManager.getDefaultUri returns null on Mac.apk
// (runtime/java/android/media/RingtoneManager.java: "no system default"), so
// EXTRA_RINGTONE_DEFAULT_URI is simply absent rather than fabricated.
package android.preference;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.AttributeSet;

@SuppressWarnings("deprecation")
public class RingtonePreference extends Preference
        implements PreferenceManager.OnActivityResultListener {

    private int mRingtoneType;
    private boolean mShowDefault;
    private boolean mShowSilent;

    private int mRequestCode;

    public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // AOSP reads R.styleable.RingtonePreference_{ringtoneType,showDefault,
        // showSilent}; same names, read by PreferenceAttrs. AOSP's defaults are
        // TYPE_RINGTONE for the type and true for both flags.
        mRingtoneType = PreferenceAttrs.integer(context, attrs, "ringtoneType",
                RingtoneManager.TYPE_RINGTONE);
        mShowDefault = PreferenceAttrs.bool(context, attrs, "showDefault", true);
        mShowSilent = PreferenceAttrs.bool(context, attrs, "showSilent", true);
    }

    public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RingtonePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public RingtonePreference(Context context) {
        this(context, null, 0, 0);
    }

    /** Not AOSP; matches the context-less constructor every other Preference in
     *  this package keeps for reflective instantiation. */
    public RingtonePreference() {
        super();
        mRingtoneType = RingtoneManager.TYPE_RINGTONE;
        mShowDefault = true;
        mShowSilent = true;
    }

    public int getRingtoneType() { return mRingtoneType; }

    public void setRingtoneType(int type) { mRingtoneType = type; }

    public boolean getShowDefault() { return mShowDefault; }

    public void setShowDefault(boolean showDefault) { mShowDefault = showDefault; }

    public boolean getShowSilent() { return mShowSilent; }

    public void setShowSilent(boolean showSilent) { mShowSilent = showSilent; }

    /** AOSP body. See the file header for what "launch the picker" means here. */
    @Override
    protected void onClick() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        onPrepareRingtonePickerIntent(intent);

        PreferenceManager pm = getPreferenceManager();
        if (pm == null) return;
        PreferenceFragment owningFragment = pm.getFragment();
        try {
            if (owningFragment != null) {
                owningFragment.startActivityForResult(intent, mRequestCode);
            } else {
                android.app.Activity activity = pm.getActivity();
                if (activity != null) activity.startActivityForResult(intent, mRequestCode);
            }
        } catch (Throwable t) {
            // AOSP would throw ActivityNotFoundException when no picker is
            // installed. Swallowing it here is deliberate and NARROW: this
            // method is invoked from a row tap inside the list adapter, and an
            // escaping exception there tears down the whole settings screen for
            // a feature the host may simply not have. The persisted value is
            // untouched either way, which is the observable state the app reads.
        }
    }

    /** AOSP body, minus EXTRA_RINGTONE_AUDIO_ATTRIBUTES_FLAGS, which is
     *  @SystemApi (AudioAttributes.FLAG_BYPASS_INTERRUPTION_POLICY is not public
     *  API) and therefore not part of the surface an app can observe. */
    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                onRestoreRingtone());

        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, mShowDefault);
        if (mShowDefault) {
            Uri defaultUri = RingtoneManager.getDefaultUri(getRingtoneType());
            // Null on Mac.apk (no system ringtone set). AOSP would put a real
            // Uri; putting a null extra is what AOSP does when getDefaultUri
            // returns null, so the picker sees "no default" rather than a Uri
            // that resolves to nothing.
            ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri);
        }

        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, mShowSilent);
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, mRingtoneType);
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getTitle());
    }

    /** AOSP body: "By default, this saves the ringtone URI to the persistent
     *  storage as a string." */
    protected void onSaveRingtone(Uri ringtoneUri) {
        persistString(ringtoneUri != null ? ringtoneUri.toString() : "");
    }

    /** AOSP body. */
    protected Uri onRestoreRingtone() {
        final String uriString = getPersistedString(null);
        if (uriString == null || uriString.length() == 0) return null;
        try {
            return Uri.parse(uriString);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a == null ? null : a.getString(index);
    }

    /** The AttributeSet twin -- see Preference's constructor. */
    @Override
    Object onGetDefaultValue(AttributeSet attrs) {
        return PreferenceAttrs.string(getContext(), attrs, "defaultValue");
    }

    /** AOSP body, including its comment: there is no local state and the current
     *  ringtone is not shown until the dialog opens, so a restore needs no work;
     *  only a fresh default has to be written through. */
    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValueObj) {
        if (restorePersistedValue) return;

        String defaultValue = defaultValueObj instanceof String ? (String) defaultValueObj : null;
        if (defaultValue != null && defaultValue.length() > 0) {
            try {
                onSaveRingtone(Uri.parse(defaultValue));
            } catch (Throwable ignored) {
                // A malformed default in the app's own XML: AOSP's Uri.parse is
                // lenient enough that this is unreachable there. Do not let it
                // abort the hierarchy's initial-value dispatch.
            }
        }
    }

    /** AOSP body: claim a request code and subscribe so onActivityResult reaches
     *  us through the manager. */
    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);

        preferenceManager.registerOnActivityResultListener(this);
        mRequestCode = preferenceManager.getNextRequestCode();
    }

    /** AOSP body: consume only OUR request code, and let the change listener
     *  veto the new value before persisting -- the same contract every other
     *  Preference gives an app. */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != mRequestCode) return false;

        if (data != null) {
            Uri uri = null;
            try {
                uri = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            } catch (Throwable ignored) {}

            if (callChangeListener(uri != null ? uri.toString() : "")) {
                onSaveRingtone(uri);
            }
        }

        return true;
    }
}
