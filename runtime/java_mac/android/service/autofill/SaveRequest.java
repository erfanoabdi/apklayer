package android.service.autofill;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.List;

/**
 * What the platform hands AutofillService.onSaveRequest: the contexts whose
 * values should be saved, the service's client state, and the ids of the
 * datasets the user selected during the session.
 *
 * Pure value object. See the package note on AutofillService.
 *
 * Source: frameworks/base/core/java/android/service/autofill/SaveRequest.java
 */
public final class SaveRequest implements Parcelable {

    private final List<FillContext> mContexts;
    private final Bundle mClientState;
    private final List<String> mDatasetIds;

    SaveRequest(List<FillContext> contexts, Bundle clientState, List<String> datasetIds) {
        mContexts = contexts == null ? Collections.<FillContext>emptyList() : contexts;
        mClientState = clientState;
        mDatasetIds = datasetIds;
    }

    public List<FillContext> getFillContexts() { return mContexts; }

    public Bundle getClientState() { return mClientState; }

    /** API 28. Null when no dataset was selected, which AOSP documents. */
    public List<String> getDatasetIds() { return mDatasetIds; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedList(mContexts);
        parcel.writeBundle(mClientState);
        parcel.writeStringList(mDatasetIds);
    }

    @Override public String toString() {
        return "SaveRequest[contexts=" + mContexts.size() + "]";
    }

    public static final Creator<SaveRequest> CREATOR = new Creator<SaveRequest>() {
        @Override public SaveRequest createFromParcel(Parcel parcel) {
            final List<FillContext> contexts =
                    parcel.createTypedArrayList(FillContext.CREATOR);
            final Bundle clientState = parcel.readBundle();
            final List<String> datasetIds = parcel.createStringArrayList();
            return new SaveRequest(contexts, clientState, datasetIds);
        }
        @Override public SaveRequest[] newArray(int size) { return new SaveRequest[size]; }
    };
}
