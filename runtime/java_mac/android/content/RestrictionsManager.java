package android.content;

import android.os.Bundle;
import android.os.PersistableBundle;

import java.util.Collections;
import java.util.List;

/**
 * Managed-configuration (enterprise MDM) restrictions applied to an app by a
 * device-policy controller.
 *
 * Mac.apk is not a managed device and has no device-policy controller, which is
 * the same state as an ordinary unmanaged Android phone: AOSP returns an empty
 * Bundle from getApplicationRestrictions() and false from
 * hasRestrictionsProvider(). Apps read the Bundle and fall back to their own
 * defaults when a key is absent, so the empty Bundle is the correct answer
 * rather than a placeholder.
 *
 * Source: frameworks/base/core/java/android/content/RestrictionsManager.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/content/RestrictionsManager.java
 */
public class RestrictionsManager {
    // ---- API-36 public constants, from AOSP
    // core/java/android/content/RestrictionsManager.java (android16-release).
    // NOTE RESULT_APPROVED/RESULT_DENIED and RESULT_ERROR_BAD_REQUEST/
    // RESULT_ERROR_NETWORK share values (1 and 2): they belong to two
    // different result domains upstream, and that collision is AOSP's.
    public static final String EXTRA_REQUEST_BUNDLE = "android.content.extra.REQUEST_BUNDLE";
    public static final String REQUEST_KEY_ICON = "android.request.icon";
    public static final int RESULT_APPROVED = 1;
    public static final int RESULT_DENIED = 2;
    public static final int RESULT_ERROR_BAD_REQUEST = 1;
    public static final int RESULT_ERROR_NETWORK = 2;
    public static final int RESULT_ERROR_INTERNAL = 3;
    public static final String RESPONSE_KEY_ERROR_CODE = "android.response.errorcode";
    public static final String RESPONSE_KEY_MESSAGE = "android.response.msg";
    public static final String RESPONSE_KEY_RESPONSE_TIMESTAMP = "android.response.timestamp";
    public static final String META_DATA_APP_RESTRICTIONS = "android.content.APP_RESTRICTIONS";


    public static final String ACTION_PERMISSION_RESPONSE_RECEIVED =
            "android.content.action.PERMISSION_RESPONSE_RECEIVED";
    public static final String ACTION_REQUEST_PERMISSION =
            "android.content.action.REQUEST_PERMISSION";
    public static final String ACTION_REQUEST_LOCAL_APPROVAL =
            "android.content.action.REQUEST_LOCAL_APPROVAL";

    public static final String EXTRA_PACKAGE_NAME = "android.content.extra.PACKAGE_NAME";
    public static final String EXTRA_REQUEST_TYPE = "android.content.extra.REQUEST_TYPE";
    public static final String EXTRA_REQUEST_ID = "android.content.extra.REQUEST_ID";
    public static final String EXTRA_RESPONSE_BUNDLE = "android.content.extra.RESPONSE_BUNDLE";

    public static final String REQUEST_TYPE_APPROVAL = "android.request.type.approval";
    public static final String REQUEST_KEY_ID = "android.request.id";
    public static final String REQUEST_KEY_DATA = "android.request.data";
    public static final String REQUEST_KEY_TITLE = "android.request.title";
    public static final String REQUEST_KEY_MESSAGE = "android.request.mesg";
    // AOSP spells these with an UNDERSCORE before the last word, not a dot. javac
    // inlines String constants into the calling APK, so a guest compiled against the
    // real SDK carries "android.request.approve_label" while a dotted value here
    // would never match the key it reads back out of the bundle.
    // frameworks/base/core/java/android/content/RestrictionsManager.java (android-11.0.0_r48)
    public static final String REQUEST_KEY_APPROVE_LABEL = "android.request.approve_label";
    public static final String REQUEST_KEY_DENY_LABEL = "android.request.deny_label";
    public static final String REQUEST_KEY_NEW_REQUEST = "android.request.new_request";

    public static final String RESPONSE_KEY_RESULT = "android.response.result";
    public static final int RESULT_APPROVAL = 1;
    public static final int RESULT_DENIAL = 2;
    public static final int RESULT_NO_RESPONSE = 3;
    public static final int RESULT_UNKNOWN_REQUEST = 4;
    public static final int RESULT_ERROR = 5;

    public RestrictionsManager() { }

    /** Unmanaged device: AOSP returns an empty Bundle, never null. */
    public Bundle getApplicationRestrictions() { return new Bundle(); }

    public boolean hasRestrictionsProvider() { return false; }

    /** No provider to deliver the request to. */
    public void requestPermission(String requestType, String requestId,
                                  PersistableBundle request) { }

    public Intent createLocalApprovalIntent() { return null; }

    public void notifyPermissionResponse(String packageName, PersistableBundle response) { }

    /** No manifest-declared restriction entries on an unmanaged device. */
    public List<RestrictionEntry> getManifestRestrictions(String packageName) {
        return Collections.emptyList();
    }

    /** AOSP core/java/android/content/RestrictionsManager.java:757 --
     *  flattens a list of RestrictionEntry into the Bundle shape
     *  getApplicationRestrictions() returns. Pure data conversion, no service
     *  call, so it is ported in full including the nested-bundle recursion. */
    public static Bundle convertRestrictionsToBundle(List<RestrictionEntry> entries) {
        final Bundle bundle = new Bundle();
        for (RestrictionEntry entry : entries) {
            addRestrictionToBundle(bundle, entry);
        }
        return bundle;
    }

    private static Bundle addRestrictionToBundle(Bundle bundle, RestrictionEntry entry) {
        switch (entry.getType()) {
            case RestrictionEntry.TYPE_BOOLEAN:
                bundle.putBoolean(entry.getKey(), entry.getSelectedState());
                break;
            case RestrictionEntry.TYPE_CHOICE:
            case RestrictionEntry.TYPE_CHOICE_LEVEL:
            case RestrictionEntry.TYPE_MULTI_SELECT:
                bundle.putStringArray(entry.getKey(), entry.getAllSelectedStrings());
                break;
            case RestrictionEntry.TYPE_INTEGER:
                bundle.putInt(entry.getKey(), entry.getIntValue());
                break;
            case RestrictionEntry.TYPE_STRING:
            case RestrictionEntry.TYPE_NULL:
                bundle.putString(entry.getKey(), entry.getSelectedString());
                break;
            case RestrictionEntry.TYPE_BUNDLE: {
                RestrictionEntry[] restrictions = entry.getRestrictions();
                Bundle childBundle = convertRestrictionsToBundle(
                        java.util.Arrays.asList(restrictions));
                bundle.putBundle(entry.getKey(), childBundle);
                break;
            }
            case RestrictionEntry.TYPE_BUNDLE_ARRAY: {
                RestrictionEntry[] bundleRestrictionArray = entry.getRestrictions();
                Bundle[] bundleArray = new Bundle[bundleRestrictionArray.length];
                for (int i = 0; i < bundleRestrictionArray.length; i++) {
                    RestrictionEntry[] bundleRestrictions =
                            bundleRestrictionArray[i].getRestrictions();
                    if (bundleRestrictions == null) {
                        // AOSP logs "Non-bundle entry found in bundle array"
                        // and substitutes an empty Bundle rather than failing.
                        bundleArray[i] = new Bundle();
                    } else {
                        bundleArray[i] = convertRestrictionsToBundle(
                                java.util.Arrays.asList(bundleRestrictions));
                    }
                }
                bundle.putParcelableArray(entry.getKey(), bundleArray);
                break;
            }
            default:
                throw new IllegalArgumentException(
                        "Unsupported restrictionEntry type: " + entry.getType());
        }
        return bundle;
    }

    /** AOSP :479 -- "a List containing a Bundle for each managing agent that
     *  has set restrictions", @NonNull. Mac.apk is unmanaged, so there is no
     *  managing agent and the list is empty -- the same answer an unmanaged
     *  device gives. Note this is NOT the same as getApplicationRestrictions()
     *  above, which returns one (empty) Bundle rather than a list of them. */
    public List<Bundle> getApplicationRestrictionsPerAdmin() {
        return Collections.emptyList();
    }
}
