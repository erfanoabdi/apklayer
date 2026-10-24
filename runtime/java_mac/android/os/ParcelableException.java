// android.os.ParcelableException -- the RuntimeException a DocumentsProvider
// wraps a checked FileNotFoundException in, so it can cross ContentProvider.call()
// (AOSP DocumentsProvider.call, android14-release core/java/android/provider/
// DocumentsProvider.java:1084-1095: `catch (FileNotFoundException e) { throw new
// ParcelableException(e); }`). DocumentsContract's helpers unwrap it again with
// maybeRethrow(FileNotFoundException.class) (DocumentsContract.java,
// rethrowIfNecessary). Added with the DocumentsProvider URI/call dispatch
// (MACAPK_DOCSPROVIDER_DISPATCH, docs/document-picker-host-panel_2026-09-27.md):
// before that, nothing in Mac.apk dispatched a provider call that could throw it.
//
// Mirrors android14-release core/java/android/os/ParcelableException.java
// verbatim in shape: public final, extends RuntimeException, implements
// Parcelable, maybeRethrow, and the class-name + message parcel form.
package android.os;

public final class ParcelableException extends RuntimeException implements Parcelable {
    public ParcelableException(Throwable t) {
        super(t);
    }

    @SuppressWarnings("unchecked")
    public <T extends Throwable> void maybeRethrow(Class<T> clazz) throws T {
        if (clazz.isAssignableFrom(getCause().getClass())) {
            throw (T) getCause();
        }
    }

    /** {@hide} */
    public static Throwable readFromParcel(Parcel in) {
        final String name = in.readString();
        final String msg = in.readString();
        try {
            final Class<?> clazz = Class.forName(name, true, Parcelable.class.getClassLoader());
            if (Throwable.class.isAssignableFrom(clazz)) {
                return (Throwable) clazz.getConstructor(String.class).newInstance(msg);
            }
        } catch (ReflectiveOperationException e) {
        }
        return new RuntimeException(name + ": " + msg);
    }

    /** {@hide} */
    public static void writeToParcel(Parcel out, Throwable t) {
        out.writeString(t.getClass().getName());
        out.writeString(t.getMessage());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeToParcel(dest, getCause());
    }

    public static final Creator<ParcelableException> CREATOR = new Creator<ParcelableException>() {
        @Override
        public ParcelableException createFromParcel(Parcel source) {
            return new ParcelableException(readFromParcel(source));
        }

        @Override
        public ParcelableException[] newArray(int size) {
            return new ParcelableException[size];
        }
    };
}
