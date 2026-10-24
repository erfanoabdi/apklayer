// android.os.ResultReceiver -- a Parcelable callback that delivers a result code
// + Bundle back to its creator, optionally hopping onto a Handler's thread.
//
// PARCELLING IS AOSP'S, NOT A LIVE-OBJECT SHORTCUT (corrected, lanes20 gms-google-
// signin FIX stage). It used to be `out.writeValue(this)` / `in.readValue(null)`:
// the CLASS NAME of the (usually app-defined, often anonymous) subclass went into
// the parcel and the reader resolved it through the FRAMEWORK's class loader, so
// any ResultReceiver subclass that lives in the app's loader threw
//   BadParcelableException: ClassNotFoundException when unmarshalling:
//   androidx.credentials.playservices.controllers.BeginSignIn.
//   CredentialProviderBeginSignInController$resultReceiver$1
// the moment androidx's toIpcFriendlyResultReceiver round-tripped it (Duolingo at
// SDK < 34: every Play-services credential request died before binding GMS).
// AOSP never writes a class name: writeToParcel writes ONE strong binder (the
// IResultReceiver of the creator) and the reader builds a plain, NON-local
// ResultReceiver around that binder, whose send() is a transaction back to the
// original. The reading side therefore never needs the subclass, and the
// original's onReceiveResult runs on its own Handler exactly as before.
//
// SAME PROCESS = A DIRECT CALL, BUNDLE BY REFERENCE (review fix, lanes21). AOSP's
// reader is `mReceiver = IResultReceiver.Stub.asInterface(in.readStrongBinder())`,
// and the AIDL Stub's asInterface returns the LOCAL stub whenever
// queryLocalInterface finds it -- which it always does in one process. So
// send() is a plain Java call into MyResultReceiver.send: the creator receives
// the SAME Bundle object, never a re-parcelled copy. A copy would differ
// observably: its lazy unparcel runs with the framework class loader (BaseBundle's
// default), so an app-class Parcelable inside it could not be read back. Only a
// binder with no local IResultReceiver behind it takes the transaction path.
//
// AOSP ref: frameworks/base/core/java/android/os/ResultReceiver.java
//   (mLocal, MyRunnable, MyResultReceiver, ResultReceiver(Parcel), CREATOR)
package android.os;

public class ResultReceiver implements Parcelable {
    private static final String DESCRIPTOR = "com.android.internal.os.IResultReceiver";
    private static final int TRANSACTION_SEND = IBinder.FIRST_CALL_TRANSACTION;

    final boolean mLocal;
    final Handler mHandler;
    IBinder mReceiver;

    public ResultReceiver(Handler handler) {
        mLocal = true;
        mHandler = handler;
    }

    public void send(final int resultCode, final Bundle resultData) {
        if (mLocal) {
            if (mHandler != null) {
                mHandler.post(new Runnable() {
                    @Override public void run() { onReceiveResult(resultCode, resultData); }
                });
            } else {
                onReceiveResult(resultCode, resultData);
            }
            return;
        }
        if (mReceiver != null) {
            // AOSP IResultReceiver.Stub.asInterface: the local stub when there is one.
            IInterface local = mReceiver.queryLocalInterface(DESCRIPTOR);
            if (local instanceof MyResultReceiver) {
                ((MyResultReceiver) local).send(resultCode, resultData);
                return;
            }
            Parcel d = Parcel.obtain();
            try {
                d.writeInterfaceToken(DESCRIPTOR);
                d.writeInt(resultCode);
                d.writeTypedObject(resultData, 0);
                mReceiver.transact(TRANSACTION_SEND, d, null, IBinder.FLAG_ONEWAY);
            } catch (RemoteException e) {
                // AOSP: the creator died; nothing to deliver to.
            } finally {
                d.recycle();
            }
        }
    }

    protected void onReceiveResult(int resultCode, Bundle resultData) {}

    /** AOSP MyResultReceiver (an IResultReceiver.Stub): the creator's end of the
     *  binder. Like every AIDL Stub it attaches itself as the local interface, so a
     *  reader in this process finds it through queryLocalInterface. */
    private final class MyResultReceiver extends Binder implements IInterface {
        MyResultReceiver() {
            super(DESCRIPTOR);
            attachInterface(this, DESCRIPTOR);
        }
        /** AOSP MyResultReceiver.send. */
        void send(final int resultCode, final Bundle resultData) {
            if (mHandler != null) {
                mHandler.post(new Runnable() {
                    @Override public void run() { onReceiveResult(resultCode, resultData); }
                });
            } else {
                onReceiveResult(resultCode, resultData);
            }
        }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != TRANSACTION_SEND) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(DESCRIPTOR);
            int resultCode = data.readInt();
            Bundle resultData = data.readTypedObject(Bundle.CREATOR);
            send(resultCode, resultData);
            return true;
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        synchronized (this) {
            if (mReceiver == null) mReceiver = new MyResultReceiver();
            out.writeStrongBinder(mReceiver);
        }
    }

    /** AOSP ResultReceiver(Parcel): a non-local receiver around the creator's binder. */
    ResultReceiver(Parcel in) {
        mLocal = false;
        mHandler = null;
        mReceiver = in.readStrongBinder();
    }

    public static final Parcelable.Creator<ResultReceiver> CREATOR =
        new Parcelable.Creator<ResultReceiver>() {
            @Override public ResultReceiver createFromParcel(Parcel in) {
                return new ResultReceiver(in);
            }
            @Override public ResultReceiver[] newArray(int size) {
                return new ResultReceiver[size];
            }
        };
}
