// android.os.TestLooperManager -- a test-only handle for manually pumping a
// Looper's MessageQueue from instrumentation tests
// (InstrumentationRegistry.getInstrumentation().acquireLooperManager()). Mac.apk
// is not a test host, so this is provided only so the type resolves; the message
// inspection/dispatch helpers operate on the supplied Looper's live queue where
// the public Message API allows. Apps proper do not use this.
//
// AOSP ref: frameworks/base/core/java/android/os/TestLooperManager.java
package android.os;

public class TestLooperManager {
    private final Looper mLooper;

    public TestLooperManager(Looper looper) {
        mLooper = looper;
    }

    public MessageQueue getMessageQueue() {
        return (mLooper != null) ? mLooper.getQueue() : null;
    }

    // AOSP returns the next Message without dispatching it. We cannot peek the
    // queue without internal access, so we return null (no message available),
    // which is a safe "idle" answer for any caller polling the manager.
    public Message next() { return null; }

    public void execute(Message message) {
        if (message != null && message.getTarget() != null) {
            message.getTarget().dispatchMessage(message);
        }
    }

    public boolean hasMessages(Handler h, Object object, int what) {
        return (h != null) && h.hasMessages(what, object);
    }

    public boolean hasMessages(Handler h, Object object, Runnable r) {
        return false;
    }

    public void recycle(Message msg) {
        if (msg != null) msg.recycleUnchecked();  // AOSP: recycle() rejects an in-use message; the test hook must bypass that
    }

    /** AOSP core/java/android/os/TestLooperManager.java:108 --
     *  `mQueue.pollForTest()`. "Retrieves and removes the next message in the
     *  queue. If the queue is empty, returns null. This method never blocks."
     *  Unlike next() above, this one CAN be implemented against our own
     *  MessageQueue, which is in-tree. */
    public Message poll() {
        MessageQueue q = getMessageQueue();
        return (q != null) ? q.pollForTest() : null;
    }

    /** AOSP :123 -- `mQueue.peekWhenForTest()`. "Retrieves, but does not
     *  remove, the values of Message#when of next message in the queue ...
     *  null if the queue is empty." Boxed Long precisely so null is
     *  expressible. */
    public Long peekWhen() {
        MessageQueue q = getMessageQueue();
        return (q != null) ? q.peekWhenForTest() : null;
    }

    /** AOSP :133 -- `mQueue.isBlockedOnSyncBarrier()`. */
    public boolean isBlockedOnSyncBarrier() {
        MessageQueue q = getMessageQueue();
        return (q != null) && q.isBlockedOnSyncBarrier();
    }

    public void release() {}
}
