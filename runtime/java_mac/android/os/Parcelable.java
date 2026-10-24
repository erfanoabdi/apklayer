// android.os.Parcelable -- empty marker. Real Android uses this for
// IPC. Mac.apk has no IPC, so methods here are stubs that satisfy the
// app's @Override expectations.
package android.os;

public interface Parcelable {
    int CONTENTS_FILE_DESCRIPTOR = 0x1;
    int PARCELABLE_WRITE_RETURN_VALUE = 0x1;

    int describeContents();
    void writeToParcel(Parcel dest, int flags);

    interface Creator<T> {
        T createFromParcel(Parcel source);
        T[] newArray(int size);
    }
    interface ClassLoaderCreator<T> extends Creator<T> {
        T createFromParcel(Parcel source, ClassLoader loader);
    }
}
