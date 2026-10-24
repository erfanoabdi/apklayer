package android.os;

// apklayer patch: minimal Process stand-in (upstream Process.java pulls in the whole
// binder/zygote graph). Only what Looper/HandlerThread/MessageQueue touch today.
public class Process {
    public static final int THREAD_GROUP_DEFAULT = -1;
    public static final int THREAD_GROUP_BACKGROUND = 0;
    public static final int THREAD_GROUP_FOREGROUND = 1;
    public static final int THREAD_GROUP_SYSTEM = 2;
    public static final int THREAD_GROUP_AUDIO_APP = 3;
    public static final int THREAD_GROUP_AUDIO_SYS = 4;
    public static final int THREAD_GROUP_TOP_APP = 5;
    public static final int THREAD_GROUP_RT_APP = 6;
    public static final int THREAD_PRIORITY_LOWEST = 19;
    public static final int THREAD_PRIORITY_BACKGROUND = 10;
    public static final int THREAD_PRIORITY_LESS_FAVORABLE = 1;
    public static final int THREAD_PRIORITY_DEFAULT = 0;
    public static final int THREAD_PRIORITY_MORE_FAVORABLE = -1;
    public static final int THREAD_PRIORITY_FOREGROUND = -2;
    public static final int THREAD_PRIORITY_DISPLAY = -4;
    public static final int THREAD_PRIORITY_URGENT_DISPLAY = -8;
    public static final int THREAD_PRIORITY_VIDEO = -10;
    public static final int THREAD_PRIORITY_AUDIO = -16;
    public static final int THREAD_PRIORITY_URGENT_AUDIO = -19;

    public static final int myPid() { return (int) ProcessHandle.current().pid(); }
    public static final int myTid() { return (int) Thread.currentThread().threadId(); }
    public static final int myUid() { return (int) ProcessHandle.current().pid(); }
    public static boolean isIsolated() { return false; }
    public static int getProcessGroup(int pid) { return 0; }
    public static void setThreadPriority(int tid, int priority) {}
    public static void setThreadPriority(int priority) {}
    public static int getThreadPriority(int tid) { return THREAD_PRIORITY_DEFAULT; }
}
