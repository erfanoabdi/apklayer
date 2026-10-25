package android.service.autofill;

import java.util.Set;

/**
 * API 31 callback for onSavedDatasetsInfoRequest. The service calls exactly one
 * of onSuccess / onError.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   ERROR_OTHER = 0, ERROR_UNSUPPORTED = 1, ERROR_NEEDS_USER_ACTION = 2
 *
 * Source: frameworks/base/core/java/android/service/autofill/SavedDatasetsInfoCallback.java
 */
public interface SavedDatasetsInfoCallback {

    int ERROR_OTHER = 0;
    int ERROR_UNSUPPORTED = 1;
    int ERROR_NEEDS_USER_ACTION = 2;

    void onSuccess(Set<SavedDatasetsInfo> results);

    void onError(int error);
}
