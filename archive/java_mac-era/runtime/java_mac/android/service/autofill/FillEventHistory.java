package android.service.autofill;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the user did with the service's last response: which dataset they
 * picked, which were shown and ignored, which fields they edited by hand.
 *
 * The platform builds this; a service only reads it, and AOSP returns null from
 * getFillEventHistory() whenever there is nothing to report. Mac.apk has no
 * session to report on, so getEvents() is an empty list rather than a
 * fabricated history.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt.
 * ⚠ Two dense integer runs live in Event: TYPE_* (0..6) and NO_SAVE_UI_REASON_*
 * (0..7). TYPE_DATASET_SELECTED is 0 and NO_SAVE_UI_REASON_NONE is 0; every
 * other value was copied from that file rather than recalled.
 *
 * Source: frameworks/base/core/java/android/service/autofill/FillEventHistory.java
 */
public final class FillEventHistory implements Parcelable {

    private final Bundle mClientState;
    private final List<Event> mEvents;
    private final int mSessionId;

    FillEventHistory(Bundle clientState, List<Event> events, int sessionId) {
        mClientState = clientState;
        mEvents = events == null ? Collections.<Event>emptyList() : events;
        mSessionId = sessionId;
    }

    /** @deprecated in AOSP: read the per-event client state instead, because
     *  the history can span responses with different states. */
    @Deprecated
    public Bundle getClientState() { return mClientState; }

    public List<Event> getEvents() { return mEvents; }

    /** API 36. */
    public int getSessionId() { return mSessionId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBundle(mClientState);
        parcel.writeInt(mSessionId);
        // Event is NOT Parcelable in AOSP; the platform reconstructs the list
        // on the far side. Only the count crosses, which keeps the wire shape
        // self-consistent without inventing a public Event parcel format.
        parcel.writeInt(mEvents.size());
    }

    @Override public String toString() {
        return "FillEventHistory[events=" + mEvents.size() + "]";
    }

    public static final Creator<FillEventHistory> CREATOR =
            new Creator<FillEventHistory>() {
        @Override public FillEventHistory createFromParcel(Parcel parcel) {
            final Bundle clientState = parcel.readBundle();
            final int sessionId = parcel.readInt();
            parcel.readInt();
            return new FillEventHistory(clientState, null, sessionId);
        }
        @Override public FillEventHistory[] newArray(int size) {
            return new FillEventHistory[size];
        }
    };

    /** One thing the user did. */
    public static final class Event {

        public static final int TYPE_DATASET_SELECTED = 0;
        public static final int TYPE_DATASET_AUTHENTICATION_SELECTED = 1;
        public static final int TYPE_AUTHENTICATION_SELECTED = 2;
        public static final int TYPE_SAVE_SHOWN = 3;
        public static final int TYPE_CONTEXT_COMMITTED = 4;
        public static final int TYPE_DATASETS_SHOWN = 5;
        public static final int TYPE_VIEW_REQUESTED_AUTOFILL = 6;

        public static final int UI_TYPE_UNKNOWN = 0;
        public static final int UI_TYPE_MENU = 1;
        public static final int UI_TYPE_INLINE = 2;
        public static final int UI_TYPE_DIALOG = 3;
        public static final int UI_TYPE_CREDENTIAL_MANAGER = 4;

        public static final int NO_SAVE_UI_REASON_NONE = 0;
        public static final int NO_SAVE_UI_REASON_NO_SAVE_INFO = 1;
        public static final int NO_SAVE_UI_REASON_WITH_DELAY_SAVE_FLAG = 2;
        public static final int NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED = 3;
        public static final int NO_SAVE_UI_REASON_NO_VALUE_CHANGED = 4;
        public static final int NO_SAVE_UI_REASON_FIELD_VALIDATION_FAILED = 5;
        public static final int NO_SAVE_UI_REASON_DATASET_MATCH = 6;
        public static final int NO_SAVE_UI_REASON_USING_CREDMAN = 7;

        private final int mType;
        private final String mDatasetId;
        private final Bundle mClientState;
        private final Set<String> mSelectedDatasetIds;
        private final Set<String> mIgnoredDatasetIds;
        private final Set<String> mShownDatasetIds;
        private final Map<AutofillId, String> mChangedFields;
        private final Map<AutofillId, Set<String>> mManuallyFilledFields;
        private final Map<AutofillId, FieldClassification> mFieldClassifications;
        private final int mNoSaveUiReason;
        private final int mUiType;
        private final AutofillId mFocusedId;

        Event(int type, String datasetId, Bundle clientState,
                Set<String> selectedDatasetIds, Set<String> ignoredDatasetIds,
                Set<String> shownDatasetIds, Map<AutofillId, String> changedFields,
                Map<AutofillId, Set<String>> manuallyFilledFields,
                Map<AutofillId, FieldClassification> fieldClassifications,
                int noSaveUiReason, int uiType, AutofillId focusedId) {
            mType = type;
            mDatasetId = datasetId;
            mClientState = clientState;
            mSelectedDatasetIds = emptyIfNull(selectedDatasetIds);
            mIgnoredDatasetIds = emptyIfNull(ignoredDatasetIds);
            mShownDatasetIds = emptyIfNull(shownDatasetIds);
            mChangedFields = emptyIfNull(changedFields);
            mManuallyFilledFields = emptyIfNull(manuallyFilledFields);
            mFieldClassifications = emptyIfNull(fieldClassifications);
            mNoSaveUiReason = noSaveUiReason;
            mUiType = uiType;
            mFocusedId = focusedId;
        }

        private static <T> Set<T> emptyIfNull(Set<T> s) {
            return s == null ? Collections.<T>emptySet() : s;
        }

        private static <K, V> Map<K, V> emptyIfNull(Map<K, V> m) {
            return m == null ? Collections.<K, V>emptyMap() : m;
        }

        public int getType() { return mType; }

        public String getDatasetId() { return mDatasetId; }

        public Bundle getClientState() { return mClientState; }

        /** Never null in AOSP -- an empty set when nothing applies. */
        public Set<String> getSelectedDatasetIds() { return mSelectedDatasetIds; }

        public Set<String> getIgnoredDatasetIds() { return mIgnoredDatasetIds; }

        /** API 36. */
        public Set<String> getShownDatasetIds() { return mShownDatasetIds; }

        public Map<AutofillId, String> getChangedFields() { return mChangedFields; }

        public Map<AutofillId, Set<String>> getManuallyEnteredField() {
            return mManuallyFilledFields;
        }

        public Map<AutofillId, FieldClassification> getFieldsClassification() {
            return mFieldClassifications;
        }

        /** API 30. */
        public int getNoSaveUiReason() { return mNoSaveUiReason; }

        /** API 33. */
        public int getUiType() { return mUiType; }

        /** API 36. */
        public AutofillId getFocusedId() { return mFocusedId; }

        @Override public String toString() {
            return "FillEvent[type=" + mType + ", datasetId=" + mDatasetId + "]";
        }
    }
}
