// android.net.Credentials
//
// The peer credentials (pid/uid/gid) of a LocalSocket connection. Pure value
// type. Mac.apk's LocalSocket never connects, so these are not produced, but
// the type must resolve. Mirrors
// frameworks/base/core/java/android/net/Credentials.java.
package android.net;

public class Credentials {
    private final int mPid;
    private final int mUid;
    private final int mGid;

    public Credentials(int pid, int uid, int gid) {
        mPid = pid;
        mUid = uid;
        mGid = gid;
    }

    public int getPid() { return mPid; }
    public int getUid() { return mUid; }
    public int getGid() { return mGid; }
}
