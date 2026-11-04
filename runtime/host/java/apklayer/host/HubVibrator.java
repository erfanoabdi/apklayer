package apklayer.host;

import android.os.CombinedVibration;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.IVibratorManagerService;
import android.os.IVibratorStateListener;
import android.os.Parcel;
import android.os.PermissionEnforcer;
import android.os.RemoteException;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.vibrator.IHapticGeneratorSessionCallback;
import android.os.vibrator.IVibrationSessionCallback;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;
import java.util.function.BiPredicate;

/**
 * The instance's "vibrator_manager" (system_server's VibratorManagerService, which apklayer does not run), over the vibrator HAL the stack starts
 * when providers.toml configures one (aosp/hals/vibrator): the framework's SystemVibrator/SystemVibratorManager in each app reach it by name. One
 * vibrator (id 0); a vibration's step segments are played on the HAL in order (setAmplitude, on for the step's duration); a new vibration or a
 * cancel ends the one playing. Published only when this stack declares the vibrator HAL: otherwise an app sees no vibrator, as before.
 */
final class HubVibrator extends IVibratorManagerService.Stub {
    static final String SERVICE = "vibrator_manager";
    private static final String HAL = "android.hardware.vibrator.IVibrator/default";
    private static final String HAL_DESCRIPTOR = "android.hardware.vibrator.IVibrator";
    // IVibrator's transactions (hardware/interfaces/vibrator/aidl/aidl_api/.../4/IVibrator.aidl, in declaration order) and capabilities
    private static final int GET_CAPABILITIES = IBinder.FIRST_CALL_TRANSACTION, OFF = GET_CAPABILITIES + 1, ON = GET_CAPABILITIES + 2,
            SET_AMPLITUDE = GET_CAPABILITIES + 5;
    private static final int CAP_AMPLITUDE_CONTROL = 4;

    private final BiPredicate<Integer, String> holds;  // uid, permission
    private Thread playing;

    private HubVibrator(BiPredicate<Integer, String> holds) {
        super(new PermissionEnforcer() {});  // the protected no-context enforcer: these methods check permissions themselves
        this.holds = holds;
    }

    /** Publishes the service when the stack declared the vibrator HAL (cli/stack.cpp stages its manifest only then). */
    static void publishIfProvided(BiPredicate<Integer, String> holds) {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        if (root == null || !new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-vibrator.xml").exists()) return;
        if (RealBinder.await(HAL, 5000) == null) { System.out.println("[apklayer-hub] the vibrator HAL is declared but not running: no vibrator"); return; }
        if (!RealBinder.publish(SERVICE, new HubVibrator(holds))) System.out.println("[apklayer-hub] cannot publish " + SERVICE);
    }

    /** One IVibrator call: its int result (0 for void methods). */
    private static int hal(int code, Parcel args) throws RemoteException {
        IBinder vibrator = RealBinder.lookup(HAL);  // looked up each time: `apklayer providers set` restarts the HAL with a new binder
        if (vibrator == null) throw new RemoteException("no vibrator HAL");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(HAL_DESCRIPTOR);
            if (args != null) data.appendFrom(args, 0, args.dataSize());
            vibrator.transact(code, data, reply, 0);
            reply.readException();
            return reply.dataAvail() >= 4 ? reply.readInt() : 0;
        } finally { data.recycle(); reply.recycle(); if (args != null) args.recycle(); }
    }

    private static Parcel ints(int... values) { Parcel p = Parcel.obtain(); for (int v : values) p.writeInt(v); return p; }

    @Override public int[] getVibratorIds() { return new int[] {0}; }

    @Override public int getCapabilities() { return 0; }  // the manager's own (synced/vendor sessions): none

    @Override public VibratorInfo getVibratorInfo(int vibratorId) {
        if (vibratorId != 0) return null;
        long caps;
        try { caps = hal(GET_CAPABILITIES, null); } catch (RemoteException gone) { caps = 0; }
        return new VibratorInfo.Builder(0).setCapabilities(caps).build();
    }

    @Override public synchronized boolean isVibrating(int vibratorId) { return playing != null && playing.isAlive(); }
    // shortcut: no state listeners (nothing is notified of on/off), until an app needs them
    @Override public boolean registerVibratorStateListener(int vibratorId, IVibratorStateListener listener) { return false; }
    @Override public boolean unregisterVibratorStateListener(int vibratorId, IVibratorStateListener listener) { return false; }
    @Override public boolean setAlwaysOnEffect(int uid, String opPkg, int alwaysOnId, CombinedVibration vibration, VibrationAttributes attributes) { return false; }

    @Override public void vibrate(int uid, int deviceId, String opPkg, CombinedVibration vibration, VibrationAttributes attributes, String reason, IBinder token) {
        if (!holds.test(uid, android.Manifest.permission.VIBRATE)) throw new SecurityException(opPkg + " does not hold " + android.Manifest.permission.VIBRATE);
        if (!(vibration instanceof CombinedVibration.Mono mono) || !(mono.getEffect() instanceof VibrationEffect.Composed composed)) {
            System.out.println("[apklayer-hub] vibrator: only single-vibrator composed effects are played, not " + vibration);
            return;
        }
        Thread player = new Thread(() -> play(composed), "hub-vibration");
        synchronized (this) {
            if (playing != null) playing.interrupt();
            playing = player;
        }
        player.start();
    }

    // shortcut: step segments only (createOneShot, createWaveform); prebaked, primitive and ramp segments are skipped, and a repeating waveform plays once
    private void play(VibrationEffect.Composed effect) {
        try {
            for (VibrationEffectSegment segment : effect.getSegments()) {
                if (!(segment instanceof StepSegment step) || step.getDuration() <= 0) continue;
                float amplitude = step.getAmplitude() == VibrationEffect.DEFAULT_AMPLITUDE ? 1f : step.getAmplitude();
                if (amplitude > 0) {
                    Parcel a = Parcel.obtain();
                    a.writeFloat(amplitude);
                    if ((hal(GET_CAPABILITIES, null) & CAP_AMPLITUDE_CONTROL) != 0) hal(SET_AMPLITUDE, a); else a.recycle();
                    Parcel on = ints((int) step.getDuration());
                    on.writeStrongBinder(null);  // no completion callback: this thread times the steps
                    hal(ON, on);
                } else hal(OFF, null);
                Thread.sleep(step.getDuration());
            }
            hal(OFF, null);
        } catch (InterruptedException cancelled) {
            try { hal(OFF, null); } catch (RemoteException gone) { /* the HAL went away */ }
        } catch (RemoteException gone) {
            System.out.println("[apklayer-hub] vibrator: " + gone);
        }
    }

    @Override public void cancelVibrate(int usageFilter, IBinder token) {
        synchronized (this) { if (playing != null) playing.interrupt(); playing = null; }
    }

    @Override public void performHapticFeedback(int uid, int deviceId, String opPkg, int constant, int usage, String reason, int flags, int privFlags) {
        // shortcut: haptic feedback constants map to prebaked effects (system_server's HapticFeedbackVibrationProvider): not played
    }

    @Override public void performHapticFeedbackForInputDevice(int uid, int deviceId, String opPkg, int constant, int inputDeviceId, int inputSource,
            String reason, int flags, int privFlags) {}

    @Override public ICancellationSignal startVendorVibrationSession(int uid, int deviceId, String opPkg, int[] vibratorIds, VibrationAttributes attributes,
            String reason, IVibrationSessionCallback callback) {
        throw new UnsupportedOperationException("vendor vibration sessions");
    }

    @Override public void startHapticGeneratorSession(int vibratorId, android.os.vibrator.HapticGeneratorSession.Config config, IHapticGeneratorSessionCallback callback) {
        throw new UnsupportedOperationException("haptic generator sessions");
    }
}
