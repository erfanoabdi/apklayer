// android.nfc.Tag
//
// Represents a discovered NFC tag, delivered in the ACTION_TAG_DISCOVERED
// intent. Mac.apk has no NFC, so tags are never discovered and instances are
// never produced, but apps reference Tag in fields/casts (e.g. reading the
// intent extra), so the type + getId/getTechList must resolve. Mirrors
// frameworks/base/core/java/android/nfc/Tag.java.
package android.nfc;

import android.os.Parcel;
import android.os.Parcelable;

public final class Tag implements Parcelable {

    private final byte[] mId;
    private final String[] mTechList;

    Tag() { this(new byte[0], new String[0]); }
    Tag(byte[] id, String[] techList) {
        mId = id != null ? id : new byte[0];
        mTechList = techList != null ? techList : new String[0];
    }

    public byte[] getId() { return mId; }
    public String[] getTechList() { return mTechList; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder("TAG: Tech [");
        for (int i = 0; i < mTechList.length; i++) {
            sb.append(mTechList[i]);
            if (i < mTechList.length - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    // PARCELABLE on AOSP: a Tag arrives as an Intent extra, so the type has to
    // be marshallable for Intent.getParcelableExtra(EXTRA_TAG) to resolve at
    // all. No tag is ever produced here, but the round trip below is real and
    // symmetric rather than a stub.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId.length);
        dest.writeByteArray(mId);
        dest.writeInt(mTechList.length);
        dest.writeStringArray(mTechList);
    }

    public static final Creator<Tag> CREATOR = new Creator<Tag>() {
        @Override public Tag createFromParcel(Parcel in) {
            in.readInt();
            byte[] id = in.createByteArray();
            in.readInt();
            String[] techList = in.createStringArray();
            return new Tag(id, techList);
        }

        @Override public Tag[] newArray(int size) {
            return new Tag[size];
        }
    };
}
