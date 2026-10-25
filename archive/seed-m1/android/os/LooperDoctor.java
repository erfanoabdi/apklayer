package android.os;

// apklayer patch: LooperDoctor stand-in (monitoring disabled via Flags anyway).
public final class LooperDoctor {
    public static final class LooperDoctorAlarm {}
    public LooperDoctor(Looper looper) {}
    public LooperDoctorAlarm notifyLooperStartedLooping(Thread t) { return null; }
    public void startMessageTimer(LooperDoctorAlarm alarm) {}
    public long stopMessageTimer(LooperDoctorAlarm alarm) { return 0L; }
    public void notifyLooperQuit(LooperDoctorAlarm alarm) {}
}
