package android.service.credentials;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;

/** One credential a provider can return, backed by a Slice that describes how
 *  to render it in the system picker. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/CredentialEntry.java
 *
 *  Three constructors on AOSP. The BeginGetCredentialOption form derives BOTH
 *  the option id and the type from the option, which is why the id is a real
 *  field here rather than a constant: getBeginGetCredentialOptionId() is how a
 *  provider's response is matched back to the request that asked for it. */
public final class CredentialEntry implements Parcelable {
    private final String mBeginGetCredentialOptionId;
    private final String mType;
    private final Slice mSlice;

    public CredentialEntry(BeginGetCredentialOption beginGetCredentialOption, Slice slice) {
        mBeginGetCredentialOptionId =
                beginGetCredentialOption != null ? beginGetCredentialOption.getId() : null;
        mType = beginGetCredentialOption != null ? beginGetCredentialOption.getType() : null;
        mSlice = slice;
    }

    public CredentialEntry(String type, Slice slice) {
        mBeginGetCredentialOptionId = "";
        mType = type;
        mSlice = slice;
    }

    public CredentialEntry(String beginGetCredentialOptionId, String type, Slice slice) {
        mBeginGetCredentialOptionId = beginGetCredentialOptionId;
        mType = type;
        mSlice = slice;
    }

    public String getBeginGetCredentialOptionId() { return mBeginGetCredentialOptionId; }
    public String getType() { return mType; }
    public Slice getSlice() { return mSlice; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mBeginGetCredentialOptionId);
        dest.writeString(mType);
        dest.writeTypedObject(mSlice, flags);
    }

    public static final Parcelable.Creator<CredentialEntry> CREATOR =
            new Parcelable.Creator<CredentialEntry>() {
                @Override public CredentialEntry createFromParcel(Parcel in) {
                    return new CredentialEntry(in.readString(), in.readString(),
                            in.readTypedObject(Slice.CREATOR));
                }
                @Override public CredentialEntry[] newArray(int size) {
                    return new CredentialEntry[size];
                }
            };
}
