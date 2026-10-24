// android.view.inputmethod.InputBinding -- the handle the system gives an IME
// for one bound editor: its InputConnection plus the owning process identity.
// Source: frameworks/base
// core/java/android/view/inputmethod/InputBinding.java (android16-release).
//
// Mac.apk binds no IME (see InputMethodManager), so the framework never
// constructs one; the type exists because InputMethod.bindInput names it.
package android.view.inputmethod;

public final class InputBinding implements android.os.Parcelable {
    private final InputConnection mConnection;
    private final android.os.IBinder mConnectionToken;
    private final int mUid;
    private final int mPid;

    public InputBinding(InputConnection conn, android.os.IBinder connToken, int uid, int pid) {
        mConnection = conn;
        mConnectionToken = connToken;
        mUid = uid;
        mPid = pid;
    }

    /** AOSP InputBinding(InputConnection, InputBinding): re-point an existing
     *  binding at a different connection, keeping its identity. */
    public InputBinding(InputConnection conn, InputBinding binding) {
        mConnection = conn;
        mConnectionToken = binding.getConnectionToken();
        mUid = binding.getUid();
        mPid = binding.getPid();
    }

    public InputConnection getConnection() { return mConnection; }
    public android.os.IBinder getConnectionToken() { return mConnectionToken; }
    public int getUid() { return mUid; }
    public int getPid() { return mPid; }

    @Override public String toString() {
        return "InputBinding{" + mConnectionToken + " / uid " + mUid + " / pid " + mPid + "}";
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        // AOSP writes the connection's IInputContext binder, not the connection
        // itself. One process here, so the live reference IS the connection --
        // android/os/Parcel's writeStrongBinder/readStrongBinder keep object
        // identity, which is what the binder achieves cross-process.
        dest.writeStrongBinder(mConnectionToken);
        dest.writeInt(mUid);
        dest.writeInt(mPid);
    }
    public static final android.os.Parcelable.Creator<InputBinding> CREATOR =
            new android.os.Parcelable.Creator<InputBinding>() {
        @Override public InputBinding createFromParcel(android.os.Parcel source) {
            return new InputBinding(null, source.readStrongBinder(),
                    source.readInt(), source.readInt());
        }
        @Override public InputBinding[] newArray(int size) { return new InputBinding[size]; }
    };
}
