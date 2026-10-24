// android.os.Message -- pooled message object. We don't pool; just
// allocate. Only the public field surface matters to apps.
package android.os;

public final class Message implements Parcelable {
    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    public Bundle data;
    public Handler target;
    public Runnable callback;
    public long when;

    /** AOSP: optional Messenger where replies to this message can be sent.
     *  The bound-Service two-way pattern: a client sets replyTo before
     *  calling send(msg), and the Service later does
     *  msg.replyTo.send(response) to talk back. Our Messenger (Messenger
     *  .java) already implements the in-process send()/Parcelable contract
     *  correctly, so this is pure field wiring -- no new plumbing needed. */
    public Messenger replyTo;

    /** AOSP UID_NONE default. Real Android fills sendingUid from the Binder
     *  calling identity when a message crosses processes; Mac.apk is
     *  single-process, so it stays UID_NONE unless an app sets it directly. */
    public static final int UID_NONE = -1;
    public int sendingUid = UID_NONE;
    public int workSourceUid = UID_NONE;

    /** AOSP-faithful intrusive linked-list pointer. Real
     *  android.os.Message has a package-private `Message next` so the
     *  MessageQueue can keep an in-order (by `when`) singly-linked list
     *  without a wrapper allocation. Used only by the real MessageQueue
     *  (MACAPK_REAL_LOOPER main loop); null for the historical pooled
     *  ArrayDeque-free path. */
    Message next;

    // AOSP Message.java:107-115 (android-11.0.0_r48). The async flag used to be
    // a separate private boolean here, which made it impossible to express the
    // two contracts that read the WHOLE word at once: copyFrom copies the flags
    // minus FLAG_IN_USE, and recycleUnchecked resets flags to exactly
    // FLAG_IN_USE (thereby clearing async as a side effect).
    /*package*/ static final int FLAG_IN_USE = 1 << 0;
    /*package*/ static final int FLAG_ASYNCHRONOUS = 1 << 1;
    /*package*/ static final int FLAGS_TO_CLEAR_ON_COPY_FROM = FLAG_IN_USE;
    /*package*/ int flags;

    public Message() {}

    public static Message obtain() { return new Message(); }
    public static Message obtain(Handler h) {
        Message m = obtain(); m.target = h; return m;
    }
    public static Message obtain(Handler h, int what) {
        Message m = obtain(h); m.what = what; return m;
    }
    public static Message obtain(Handler h, int what, Object obj) {
        Message m = obtain(h, what); m.obj = obj; return m;
    }
    public static Message obtain(Handler h, int what, int arg1, int arg2) {
        Message m = obtain(h, what); m.arg1 = arg1; m.arg2 = arg2; return m;
    }
    public static Message obtain(Handler h, int what, int arg1, int arg2, Object obj) {
        Message m = obtain(h, what); m.arg1 = arg1; m.arg2 = arg2; m.obj = obj; return m;
    }
    public static Message obtain(Handler h, Runnable callback) {
        Message m = obtain(h); m.callback = callback; return m;
    }
    /** AOSP Message.obtain(Message orig) (:171-190): copies the reply channel,
     *  the uids and a COPY of the data Bundle as well as the scalars. It does
     *  NOT copy `flags`, because obtain() hands back a message with flags==0 --
     *  so the copy is never in-use and never inherits async. */
    public static Message obtain(Message orig) {
        Message m = obtain();
        m.what = orig.what; m.arg1 = orig.arg1; m.arg2 = orig.arg2;
        m.obj = orig.obj;
        m.replyTo = orig.replyTo;
        m.sendingUid = orig.sendingUid;
        m.workSourceUid = orig.workSourceUid;
        if (orig.data != null) m.data = new Bundle(orig.data);
        m.target = orig.target; m.callback = orig.callback;
        return m;
    }

    /** AOSP Message.gCheckRecycle + updateCheckRecycle (Message.java:314-322,
     *  android16-release):
     *      private static boolean gCheckRecycle = true;
     *      public static void updateCheckRecycle(int targetSdkVersion) {
     *          if (targetSdkVersion < Build.VERSION_CODES.LOLLIPOP) {
     *              gCheckRecycle = false;
     *          }
     *      }
     *  driven from ActivityThread.handleBindApplication with
     *  `data.appInfo.targetSdkVersion`.
     *
     *  Pre-L Handler code recycles its own Message routinely -- that was the
     *  documented idiom before the in-use check existed -- so throwing at such
     *  an app is a crash it cannot have been tested against. The comment here
     *  used to say the pre-L branch is unreachable because "nothing we run
     *  targets [< LOLLIPOP]"; MEASURED FALSE 2026-08-24, 4 of 62 APKs in
     *  ../Apks target below 21. Re-measure with `debug/compatgate.py --corpus`.
     *
     *  Two ways in, matching AOSP's authority order: an explicit
     *  updateCheckRecycle (android.app.Application.macapkInitVersion makes that
     *  call the moment the loader hands over the parsed manifest, which is our
     *  handleBindApplication), and failing that a LAZY read on first use, since
     *  a Message can be recycled before that hand-off. An UNRESOLVED targetSdk
     *  keeps the modern answer and does NOT latch. */
    private static volatile boolean gCheckRecycle = true;
    private static volatile boolean gCheckRecycleResolved = false;

    public static void updateCheckRecycle(int targetSdkVersion) {
        if (!android.app.Application.macapkTargetSdkKnown(targetSdkVersion)) return;   // unresolved: keep the default
        if (targetSdkVersion < Build.VERSION_CODES.LOLLIPOP) {
            gCheckRecycle = false;
        }
        gCheckRecycleResolved = true;
    }

    private static boolean macapkCheckRecycle() {
        if (!gCheckRecycleResolved) {
            updateCheckRecycle(android.app.Application.macapkAppTargetSdk());
        }
        return gCheckRecycle;
    }

    /** AOSP Message.recycle() (:308-316). Recycling a message that is still
     *  enqueued (or mid-delivery) is an ERROR and throws -- unless
     *  gCheckRecycle is off, in which case AOSP silently RETURNS (it does not
     *  fall through to recycleUnchecked).
     *
     *  This was a bare no-op, so every field survived a recycle and a
     *  double-recycle passed silently -- HDL.msg.recycle.* / .twice. */
    public void recycle() {
        if (isInUse()) {
            if (macapkCheckRecycle()) {
                throw new IllegalStateException(
                    "This message cannot be recycled because it is still in use.");
            }
            return;
        }
        recycleUnchecked();
    }

    /** AOSP Message.recycleUnchecked() (:321-341), minus the pool insert (we
     *  never pool -- obtain() always allocates, which is why a fresh message
     *  reliably has flags==0). Clearing every field is the part that matters:
     *  MessageQueue and Looper call this on messages they dispose of, and the
     *  probe reads all twelve back. Note `flags = FLAG_IN_USE` deliberately
     *  leaves the message marked in-use, which is what makes a second recycle()
     *  throw. */
    void recycleUnchecked() {
        flags = FLAG_IN_USE;
        what = 0;
        arg1 = 0;
        arg2 = 0;
        obj = null;
        replyTo = null;
        sendingUid = UID_NONE;
        workSourceUid = UID_NONE;
        when = 0;
        target = null;
        callback = null;
        data = null;
    }

    /** AOSP Message.copyFrom(Message o) (:353-369). Copies the flags (minus
     *  in-use), the reply channel and the uids, and DEEP-copies the data Bundle
     *  so writing through one message is not visible in the other -- while
     *  explicitly NOT copying when/target/callback. Ours copied four scalars,
     *  so the async flag was lost and `data` was neither cloned nor cleared. */
    public void copyFrom(Message o) {
        this.flags = o.flags & ~FLAGS_TO_CLEAR_ON_COPY_FROM;
        this.what = o.what;
        this.arg1 = o.arg1;
        this.arg2 = o.arg2;
        this.obj = o.obj;
        this.replyTo = o.replyTo;
        this.sendingUid = o.sendingUid;
        this.workSourceUid = o.workSourceUid;
        if (o.data != null) {
            this.data = (Bundle) o.data.clone();
        } else {
            this.data = null;
        }
    }

    public void setTarget(Handler target) { this.target = target; }
    public Handler getTarget() { return target; }
    public Runnable getCallback() { return callback; }
    public long getWhen() { return when; }
    /** AOSP getData() (:424-430) LAZILY CREATES the Bundle; only peekData()
     *  may return null. Returning the raw field made the single commonest
     *  Message idiom in every app -- `msg.getData().putString(...)` -- throw
     *  NullPointerException (HDL.msg.getDataLazyCreate). */
    public Bundle getData() {
        if (data == null) {
            data = new Bundle();
        }
        return data;
    }
    public Bundle peekData() { return data; }
    public void setData(Bundle data) { this.data = data; }

    /** AOSP (:471-473) is a bare `target.sendMessage(this)`, documented as
     *  "Throws a null pointer exception if this field has not been set." The
     *  null guard we had swallowed that, so a message with no target failed
     *  silently instead of telling the app which message it forgot to address. */
    public void sendToTarget() {
        target.sendMessage(this);
    }

    /** AOSP API-22+ asynchronous flag: async messages are exempt from the
     *  Looper's synchronization barriers. MessageQueue.next()/enqueueMessage
     *  consult it when a barrier sits at the head of the queue. */
    public void setAsynchronous(boolean async) {
        if (async) {
            flags |= FLAG_ASYNCHRONOUS;
        } else {
            flags &= ~FLAG_ASYNCHRONOUS;
        }
    }
    public boolean isAsynchronous() { return (flags & FLAG_ASYNCHRONOUS) != 0; }

    /** AOSP (:346-352). Set when the message is enqueued and left set while it
     *  is delivered and afterwards while it sits recycled; cleared only when a
     *  message is created or obtained. Enqueuing or recycling an in-use message
     *  is an error. */
    /*package*/ boolean isInUse() { return (flags & FLAG_IN_USE) == FLAG_IN_USE; }
    /*package*/ void markInUse()  { flags |= FLAG_IN_USE; }

    // ---- Parcelable ----
    // AOSP order: what/arg1/arg2/obj(Parcelable-checked)/when/data/replyTo/
    // sendingUid/workSourceUid. A Runnable callback can never cross a real
    // Binder (it isn't Parcelable), so AOSP throws rather than silently
    // dropping it -- mirrored here instead of a quieter shortcut.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (callback != null) {
            throw new RuntimeException("Can't marshal callbacks across processes.");
        }
        dest.writeInt(what);
        dest.writeInt(arg1);
        dest.writeInt(arg2);
        if (obj != null) {
            try {
                Parcelable p = (Parcelable) obj;
                dest.writeInt(1);
                dest.writeParcelable(p, flags);
            } catch (ClassCastException e) {
                throw new RuntimeException("Can't marshal non-Parcelable objects across processes.");
            }
        } else {
            dest.writeInt(0);
        }
        dest.writeLong(when);
        dest.writeBundle(data);
        Messenger.writeMessengerOrNullToParcel(replyTo, dest);
        dest.writeInt(sendingUid);
        dest.writeInt(workSourceUid);
    }

    private void readFromParcel(Parcel source) {
        what = source.readInt();
        arg1 = source.readInt();
        arg2 = source.readInt();
        if (source.readInt() != 0) {
            obj = source.readParcelable(null, Object.class);
        }
        when = source.readLong();
        data = source.readBundle();
        replyTo = Messenger.readMessengerOrNullFromParcel(source);
        sendingUid = source.readInt();
        workSourceUid = source.readInt();
    }

    public static final Parcelable.Creator<Message> CREATOR = new Parcelable.Creator<Message>() {
        @Override public Message createFromParcel(Parcel source) {
            Message msg = Message.obtain();
            msg.readFromParcel(source);
            return msg;
        }
        @Override public Message[] newArray(int size) {
            return new Message[size];
        }
    };
}
