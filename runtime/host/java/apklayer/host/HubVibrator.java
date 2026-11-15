package apklayer.host;

import android.os.CombinedVibration;
import android.view.HapticFeedbackConstants;
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
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.BasicPwleSegment;
import android.os.vibrator.PwleSegment;
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
            PERFORM = GET_CAPABILITIES + 3, SET_AMPLITUDE = GET_CAPABILITIES + 5, GET_COMPOSITION_DELAY_MAX = GET_CAPABILITIES + 7,
            GET_COMPOSITION_SIZE_MAX = GET_CAPABILITIES + 8, GET_PRIMITIVE_DURATION = GET_CAPABILITIES + 10, COMPOSE = GET_CAPABILITIES + 11, GET_FREQUENCY_RESOLUTION = GET_CAPABILITIES + 17, GET_FREQUENCY_MINIMUM = GET_CAPABILITIES + 18,
            GET_BANDWIDTH_AMPLITUDE_MAP = GET_CAPABILITIES + 19, COMPOSE_PWLE = GET_CAPABILITIES + 23;
    private static final int CAP_AMPLITUDE_CONTROL = 4, CAP_COMPOSE_EFFECTS = 32, CAP_COMPOSE_PWLE_EFFECTS = 1024, PRIMITIVES = 9;  // CompositePrimitive NOOP..LOW_TICK

    private final BiPredicate<Integer, String> holds;  // uid, permission
    private static final int RAMP_STEP_MS = 20;
    private Thread playing;

    private HubVibrator(BiPredicate<Integer, String> holds) {
        super(new PermissionEnforcer() {});  // the protected no-context enforcer: these methods check permissions themselves
        this.holds = holds;
    }

    /** Publishes the service when the stack declared the vibrator HAL (cli/stack.cpp stages its manifest only then). */
    static void publishIfProvided(BiPredicate<Integer, String> holds) {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        if (root == null || !(new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-vibrator.xml").exists() || deviceVibrator(root))) return;
        // the stack starts the HAL before SurfaceFlinger, whose registration makes it ready: normally there already
        if (RealBinder.lookup(HAL) == null && RealBinder.await(HAL, 5000) == null) { System.out.println("[apklayer-hub] the vibrator HAL is declared but not running: no vibrator"); return; }
        if (!RealBinder.publish(SERVICE, new HubVibrator(holds))) System.out.println("[apklayer-hub] cannot publish " + SERVICE);
    }

    /** providers.toml gives the vibrator to the device (a Halium phone's own HAL through the host binder context: host_hals.conf lists it). */
    private static boolean deviceVibrator(String root) {
        try {
            return new String(java.nio.file.Files.readAllBytes(new java.io.File(System.getenv("APKLAYER_DATA"), "apklayer/host_hals.conf").toPath())).contains("android.hardware.vibrator.IVibrator\n");
        } catch (java.io.IOException none) { return false; }
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
        VibratorInfo.Builder info = new VibratorInfo.Builder(0).setCapabilities(caps);
        if ((caps & CAP_COMPOSE_EFFECTS) != 0) {  // the primitives the HAL plays: their durations make VibratorInfo.isPrimitiveSupported true
            try {
                info.setCompositionSizeMax(hal(GET_COMPOSITION_SIZE_MAX, null)).setPrimitiveDelayMax(hal(GET_COMPOSITION_DELAY_MAX, null));
                for (int primitive = 1; primitive < PRIMITIVES; primitive++) {
                    try { info.setSupportedPrimitive(primitive, hal(GET_PRIMITIVE_DURATION, ints(primitive))); } catch (UnsupportedOperationException unsupported) { /* not this one */ }
                }
            } catch (RemoteException gone) { /* the capabilities stay */ }
        }
        return info.build();
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

    // shortcut: a PwleSegment goes to the HAL's composePwle when it has the capability (else, or when the HAL refuses it, as steps); BasicPwleSegment (intensity, no frequency) always as steps; a repeating waveform plays once
    private void play(VibrationEffect.Composed effect) {
        try {
            for (VibrationEffectSegment segment : effect.getSegments()) {
                if (segment instanceof PrebakedSegment prebaked) {  // IVibrator.perform: Effect and EffectStrength share the framework's values
                    Parcel args = ints(prebaked.getEffectId());
                    args.writeByte((byte) prebaked.getEffectStrength());
                    args.writeStrongBinder(null);
                    int ms;
                    try { ms = hal(PERFORM, args); } catch (UnsupportedOperationException unsupported) { continue; }  // an effect the HAL has not
                    Thread.sleep(ms);
                    continue;
                }
                if (segment instanceof PrimitiveSegment primitive) {  // IVibrator.compose with this one primitive (CompositeEffect: delay, primitive, scale)
                    Parcel args = Parcel.obtain();
                    args.writeInt(1);
                    args.writeInt(1);  // the element is not null
                    args.writeInt(16);  // the parcelable's size with its own header
                    args.writeInt(primitive.getDelay());
                    args.writeInt(primitive.getPrimitiveId());
                    args.writeFloat(primitive.getScale());
                    args.writeStrongBinder(null);
                    try { hal(COMPOSE, args); } catch (UnsupportedOperationException unsupported) { continue; }
                    Thread.sleep(primitive.getDelay() + hal(GET_PRIMITIVE_DURATION, ints(primitive.getPrimitiveId())));
                    continue;
                }
                if (segment instanceof PwleSegment pwle) {
                    if (!composePwle(pwle)) ramp(pwle.getStartAmplitude(), pwle.getEndAmplitude(), pwle.getDuration());
                    continue;
                }
                if (segment instanceof BasicPwleSegment pwle) { ramp(pwle.getStartIntensity(), pwle.getEndIntensity(), pwle.getDuration()); continue; }
                if (!(segment instanceof StepSegment step) || step.getDuration() <= 0) continue;
                step(step.getAmplitude() == VibrationEffect.DEFAULT_AMPLITUDE ? 1f : step.getAmplitude(), step.getDuration());
            }
            hal(OFF, null);
        } catch (InterruptedException cancelled) {
            try { hal(OFF, null); } catch (RemoteException gone) { /* the HAL went away */ }
        } catch (RemoteException gone) {
            System.out.println("[apklayer-hub] vibrator: " + gone);
        }
    }

    /** The segment as one IVibrator.composePwle (a vector of one PrimitivePwle union: active; ActivePwle's fields in AIDL order); false: the HAL cannot, play steps. */
    private boolean composePwle(PwleSegment pwle) throws RemoteException, InterruptedException {
        if ((hal(GET_CAPABILITIES, null) & CAP_COMPOSE_PWLE_EFFECTS) == 0) return false;
        // the HAL's range: from its minimum in its resolution's steps, one per entry of the bandwidth amplitude map (the float results read as ints by hal(); the array's first int is its length)
        float min = Float.intBitsToFloat(hal(GET_FREQUENCY_MINIMUM, null)), max = min + Float.intBitsToFloat(hal(GET_FREQUENCY_RESOLUTION, null)) * (hal(GET_BANDWIDTH_AMPLITUDE_MAP, null) - 1);
        Parcel args = Parcel.obtain();
        args.writeInt(1);  // one element
        args.writeInt(1);  // not null
        args.writeInt(0);  // the union's tag: active
        args.writeInt(1);  // the ActivePwle is not null
        args.writeInt(24);  // its size with its own header
        args.writeFloat(pwle.getStartAmplitude());
        args.writeFloat(Math.max(min, Math.min(max, pwle.getStartFrequencyHz())));  // as the framework's adapters keep a segment in the vibrator's range
        args.writeFloat(pwle.getEndAmplitude());
        args.writeFloat(Math.max(min, Math.min(max, pwle.getEndFrequencyHz())));
        args.writeInt((int) pwle.getDuration());
        args.writeStrongBinder(null);
        try { hal(COMPOSE_PWLE, args); }
        catch (RuntimeException refused) { System.out.println("[apklayer-hub] vibrator: composePwle refused (" + refused + "): steps"); return false; }  // IllegalArgument (frequency outside the HAL's range), Unsupported
        System.out.println("[apklayer-hub] vibrator: PWLE segment on the HAL");
        Thread.sleep(pwle.getDuration());
        return true;
    }

    /** A ramp (PWLE) as steps of RAMP_STEP_MS at the amplitude of each step's middle, as Android's RampToStepAdapter does where the HAL has no PWLE. */
    private void ramp(float from, float to, long duration) throws RemoteException, InterruptedException {
        for (long t = 0; t < duration; t += RAMP_STEP_MS) {
            long ms = Math.min(RAMP_STEP_MS, duration - t);
            step(from + (to - from) * (t + ms / 2f) / duration, ms);
        }
    }

    /** One step on the HAL: on for {@code ms} at {@code amplitude} (0 is off, and without amplitude control a step is full), timed here. */
    private void step(float amplitude, long ms) throws RemoteException, InterruptedException {
        if (amplitude > 0) {
            Parcel a = Parcel.obtain();
            a.writeFloat(amplitude);
            if ((hal(GET_CAPABILITIES, null) & CAP_AMPLITUDE_CONTROL) != 0) hal(SET_AMPLITUDE, a); else a.recycle();
            Parcel on = ints((int) ms);
            on.writeStrongBinder(null);  // no completion callback: this thread times the steps
            hal(ON, on);
        } else hal(OFF, null);
        Thread.sleep(ms);
    }

    @Override public void cancelVibrate(int usageFilter, IBinder token) {
        synchronized (this) { if (playing != null) playing.interrupt(); playing = null; }
    }

    @Override public void performHapticFeedback(int uid, int deviceId, String opPkg, int constant, int usage, String reason, int flags, int privFlags) {
        if (!holds.test(uid, android.Manifest.permission.VIBRATE) || constant == HapticFeedbackConstants.NO_HAPTICS) return;
        if ((flags & HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING) == 0 && !hapticFeedbackEnabled()) return;  // Settings > Sound > Touch feedback, as VibratorManagerService
        // the effects of system_server's HapticFeedbackVibrationProvider.getVibration (the HAL's own effects; no per-device or setting overrides)
        int effect = switch (constant) {
            case HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackConstants.GESTURE_END, HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE,
                    HapticFeedbackConstants.SCROLL_TICK, HapticFeedbackConstants.SEGMENT_TICK, HapticFeedbackConstants.VIRTUAL_KEY_RELEASE,
                    HapticFeedbackConstants.DRAG_CROSSING, HapticFeedbackConstants.KEYBOARD_RELEASE -> VibrationEffect.EFFECT_TICK;
            case HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.SEGMENT_FREQUENT_TICK,
                    HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE -> VibrationEffect.EFFECT_TEXTURE_TICK;
            case HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.EDGE_RELEASE, HapticFeedbackConstants.CALENDAR_DATE,
                    HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.BIOMETRIC_CONFIRM, HapticFeedbackConstants.GESTURE_START,
                    HapticFeedbackConstants.SCROLL_ITEM_FOCUS, HapticFeedbackConstants.SCROLL_LIMIT, HapticFeedbackConstants.TOGGLE_ON,
                    HapticFeedbackConstants.TOGGLE_OFF -> VibrationEffect.EFFECT_CLICK;
            case HapticFeedbackConstants.REJECT, HapticFeedbackConstants.BIOMETRIC_REJECT -> VibrationEffect.EFFECT_DOUBLE_CLICK;
            default -> VibrationEffect.EFFECT_HEAVY_CLICK;  // LONG_PRESS, DRAG_START, EDGE_SQUEEZE, and the provider's own default
        };
        System.out.println("[apklayer-hub] vibrator: haptic feedback " + constant + " -> effect " + effect);
        vibrate(uid, deviceId, opPkg, CombinedVibration.createParallel(VibrationEffect.get(effect)), null, reason, null);
    }

    /** Settings.System.HAPTIC_FEEDBACK_ENABLED (default on); unreadable (no settings provider yet): on. */
    private static boolean hapticFeedbackEnabled() {
        try {
            return android.provider.Settings.System.getInt(org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
                    android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;
        } catch (RuntimeException e) { return true; }
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
