// android.media.audiofx.AudioEffect -- base class for audio effects attached to
// an audio session. AOSP frameworks/base media/java/android/media/audiofx/AudioEffect.java.
//
// Mac.apk's mixer has no per-session effect insertion path, so every effect is a
// faithful "present but inert" implementation: construct succeeds, getEnabled
// reflects setEnabled, and parameter setters are accepted but do not alter the
// audio. The contract music apps rely on (construct an Equalizer/Visualizer
// without a NoSuchMethodError, toggle it, read back its enabled state) holds; the
// processing is simply a pass-through -- honest for a host with no effect engine.
package android.media.audiofx;

public class AudioEffect {
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ERROR_NO_INIT = -3;
    public static final int ERROR_BAD_VALUE = -4;
    public static final int ERROR_INVALID_OPERATION = -5;
    public static final int ERROR_NO_MEMORY = -6;
    public static final int ERROR_DEAD_OBJECT = -7;

    public static final int ALREADY_EXISTS = -2;

    public static final java.util.UUID EFFECT_TYPE_NULL =
        java.util.UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210");

    // Effect-type UUIDs, verbatim from AOSP
    // frameworks/base/media/java/android/media/audiofx/AudioEffect.java (android16-release,
    // lines 96-168). The first five match the OpenSL ES interface IDs in OpenSLES_IID.h;
    // the rest are Android-specific. Apps use these three ways: as the `type` argument to
    // the protected AudioEffect(UUID,UUID,int,int) constructor, to identify a Descriptor
    // returned by queryEffects(), and -- most commonly -- as a capability probe
    // (`AcousticEchoCanceler.isAvailable()` internally compares against EFFECT_TYPE_AEC).
    // They are pure identity constants, so the honest values are AOSP's own: an app that
    // scans queryEffects() here finds nothing, which is the truthful answer for a host
    // with no effect engine (see the class comment), and never a forged capability.
    public static final java.util.UUID EFFECT_TYPE_ENV_REVERB =
        java.util.UUID.fromString("c2e5d5f0-94bd-4763-9cac-4e234d06839e");
    public static final java.util.UUID EFFECT_TYPE_PRESET_REVERB =
        java.util.UUID.fromString("47382d60-ddd8-11db-bf3a-0002a5d5c51b");
    public static final java.util.UUID EFFECT_TYPE_EQUALIZER =
        java.util.UUID.fromString("0bed4300-ddd6-11db-8f34-0002a5d5c51b");
    public static final java.util.UUID EFFECT_TYPE_BASS_BOOST =
        java.util.UUID.fromString("0634f220-ddd4-11db-a0fc-0002a5d5c51b");
    public static final java.util.UUID EFFECT_TYPE_VIRTUALIZER =
        java.util.UUID.fromString("37cc2c00-dddd-11db-8577-0002a5d5c51b");
    public static final java.util.UUID EFFECT_TYPE_AGC =
        java.util.UUID.fromString("0a8abfe0-654c-11e0-ba26-0002a5d5c51b");
    public static final java.util.UUID EFFECT_TYPE_AEC =
        java.util.UUID.fromString("7b491460-8d4d-11e0-bd61-0002a5d5c51b");
    public static final java.util.UUID EFFECT_TYPE_NS =
        java.util.UUID.fromString("58b4b260-8e06-11e0-aa8e-0002a5d5c51b");
    public static final java.util.UUID EFFECT_TYPE_LOUDNESS_ENHANCER =
        java.util.UUID.fromString("fe3199be-aed0-413f-87bb-11260eb63cf1");
    public static final java.util.UUID EFFECT_TYPE_DYNAMICS_PROCESSING =
        java.util.UUID.fromString("7261676f-6d75-7369-6364-28e2fd3ac39e");
    public static final java.util.UUID EFFECT_TYPE_HAPTIC_GENERATOR =
        java.util.UUID.fromString("1411e6d6-aecd-4021-a1cf-a6aceb0d71e5");

    // AOSP connectMode values for Descriptor.connectMode (Camera.java-style
    // bag constants, but these are real public AOSP API surface, not private
    // keys). Only INSERT is ever produced below (every effect here behaves as
    // an insert effect -- applied in-line, never routed as a wet/dry send),
    // but AUXILIARY is declared alongside it because the two are always
    // documented as a pair and an app may reference either by name.
    public static final String EFFECT_INSERT = "Insert";
    public static final String EFFECT_AUXILIARY = "Auxiliary";

    private boolean mEnabled = false;
    private boolean mHasControl = true;
    protected boolean mReleased = false;

    // AOSP fills this from the native HAL's matched effect implementation at
    // construction time (native_setup's desc[] out-param). Mac.apk's mixer
    // has no effect engine to match against (see class comment), so there is
    // no real implementation to describe -- but getDescriptor() must still
    // return a populated, non-null Descriptor (AOSP never returns null; it
    // throws IllegalStateException on an uninitialized effect instead, a
    // state this permissive class never enters). Built once at construction
    // from exactly what the caller asked for, which is the only honest source
    // of "what effect is this" available here.
    private final Descriptor mDescriptor;

    public AudioEffect() {
        mDescriptor = buildDescriptor(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL);
    }
    protected AudioEffect(java.util.UUID type, java.util.UUID uuid, int priority, int audioSession) {
        mDescriptor = buildDescriptor(type, uuid);
    }

    // type/uuid echo back exactly what the caller asked to construct: the
    // type it named (falling back to EFFECT_TYPE_NULL, AOSP's own "no
    // specific type" marker, if null), and that same type standing in for
    // uuid when the caller passed EFFECT_TYPE_NULL for uuid too ("use the
    // default implementation for this type" -- there is exactly one
    // implementation available here, the pass-through). name is left "" ,
    // not null: AOSP's real implementor fills in a real device string here,
    // which we have none of, but every Descriptor field must stay non-null.
    private static Descriptor buildDescriptor(java.util.UUID type, java.util.UUID uuid) {
        Descriptor d = new Descriptor();
        d.type = (type != null) ? type : EFFECT_TYPE_NULL;
        d.uuid = (uuid != null && !uuid.equals(EFFECT_TYPE_NULL)) ? uuid : d.type;
        d.connectMode = EFFECT_INSERT;
        d.name = "";
        d.implementor = "Mac.apk";
        return d;
    }

    /** AOSP: "Get the effect descriptor." Throws IllegalStateException on an
     *  uninitialized effect in AOSP; this class has no uninitialized state
     *  (construction always succeeds, per the class comment), so it always
     *  has a real Descriptor to hand back. */
    public Descriptor getDescriptor() { return mDescriptor; }

    public int setEnabled(boolean enabled) { mEnabled = enabled; return SUCCESS; }
    public boolean getEnabled() { return mEnabled; }
    public boolean hasControl() { return mHasControl; }
    public int getId() { return 0; }
    public void release() { mReleased = true; }

    /** AOSP AudioEffect.finalize() releases the native effect if the app dropped the
     *  object without calling release(). release() here is idempotent (it only flips
     *  mReleased), so the same shape is safe. */
    @Override
    protected void finalize() throws Throwable {
        try { release(); } finally { super.finalize(); }
    }

    public void setControlStatusListener(OnControlStatusChangeListener listener) {}
    public void setEnableStatusListener(OnEnableStatusChangeListener listener) {}

    // Parameter accessors operate on a byte/int protocol in AOSP; effects with no
    // backend accept writes and report zero on reads.
    public int setParameter(byte[] param, byte[] value) { return SUCCESS; }
    public int setParameter(int param, int value) { return SUCCESS; }
    public int setParameter(int param, short value) { return SUCCESS; }
    public int setParameter(int param, byte[] value) { return SUCCESS; }
    public int getParameter(byte[] param, byte[] value) { return SUCCESS; }
    public int getParameter(int param, byte[] value) { return SUCCESS; }
    public int getParameter(int param, int[] value) { return SUCCESS; }
    public int getParameter(int param, short[] value) { return SUCCESS; }

    public static Descriptor[] queryEffects() { return new Descriptor[0]; }

    public static class Descriptor {
        public java.util.UUID type;
        public java.util.UUID uuid;
        public String connectMode;
        public String name;
        public String implementor;
        public Descriptor() {}
        public Descriptor(String type, String uuid, String connectMode,
                          String name, String implementor) {
            try { this.type = java.util.UUID.fromString(type); } catch (Throwable ignore) {}
            try { this.uuid = java.util.UUID.fromString(uuid); } catch (Throwable ignore) {}
            this.connectMode = connectMode;
            this.name = name;
            this.implementor = implementor;
        }
    }

    public interface OnControlStatusChangeListener {
        void onControlStatusChange(AudioEffect effect, boolean controlGranted);
    }
    public interface OnEnableStatusChangeListener {
        void onEnableStatusChange(AudioEffect effect, boolean enabled);
    }
}
