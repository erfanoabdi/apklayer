// android.media.ToneGenerator -- synthesizes and plays the DTMF and
// call-supervisory tones. AOSP frameworks/base
// media/java/android/media/ToneGenerator.java (the Java surface) over
// frameworks/av media/libaudioclient/ToneGenerator.cpp (the synthesizer).
//
// This is NOT telephony hardware: on every real Android device the tones are a
// pure software oscillator bank feeding an AudioTrack, so "this device has no
// supervisory-tone synth" was never a truthful answer. Mac.apk owns the same
// output path (android.media.AudioTrack -> com.macapk.loader.NativeAudio ->
// CoreAudio), so the honest implementation is the real synthesizer rather than
// a startTone() that returns true and plays silence.
//
// What is mirrored from AOSP, and why it has to be exact:
//   * The TONE_* values are AOSP's (0..98). The previous table had the
//     supervisory/proprietary block shifted by 9-10 (TONE_SUP_DIAL was 25 where
//     AOSP says 16). That was invisible while nothing read the value, but a
//     Java compile-time constant is inlined into the APK's dex from the REAL
//     android.jar, so the app hands us AOSP's number and every tone would have
//     selected the wrong descriptor.
//   * sToneDescriptors[0..98] transcribed from ToneGenerator.cpp:34+: per
//     segment a duration in ms (TONEGEN_INF = until stopTone()), up to
//     TONEGEN_MAX_WAVES=3 sine frequencies (none = silence) and a
//     loopCnt/loopIndx back-jump, plus repeatCnt/repeatSegment for the whole
//     sequence. The DTMF pairs are the ITU-T Q.23 grid.
//   * TONEGEN_GAIN (0.9) is the amplitude of a segment, split evenly across its
//     waves, and the app's 0..100 volume goes through AudioSystem::linearToLog
//     onto AudioTrack.setVolume, exactly as ToneGenerator.cpp:1356 does.
//
// Deliberately not modeled: getToneForRegion()'s regional descriptor swap. It
// only fires when the gsm.operator.iso-country property names a non-CEPT
// region; with no telephony stack the property is absent, and AOSP itself then
// takes the CEPT branch and plays the base descriptor (ToneGenerator.cpp:1745).
package android.media;

public class ToneGenerator {
    // AOSP tone types, values verbatim from frameworks/base
    // media/java/android/media/ToneGenerator.java. TONE_UNKNOWN is -1; the
    // playable range is 0..98 and indexes TONES[] one-for-one.
    public static final int TONE_UNKNOWN = -1;
    public static final int TONE_DTMF_0 = 0;
    public static final int TONE_DTMF_1 = 1;
    public static final int TONE_DTMF_2 = 2;
    public static final int TONE_DTMF_3 = 3;
    public static final int TONE_DTMF_4 = 4;
    public static final int TONE_DTMF_5 = 5;
    public static final int TONE_DTMF_6 = 6;
    public static final int TONE_DTMF_7 = 7;
    public static final int TONE_DTMF_8 = 8;
    public static final int TONE_DTMF_9 = 9;
    public static final int TONE_DTMF_S = 10;
    public static final int TONE_DTMF_P = 11;
    public static final int TONE_DTMF_A = 12;
    public static final int TONE_DTMF_B = 13;
    public static final int TONE_DTMF_C = 14;
    public static final int TONE_DTMF_D = 15;
    public static final int TONE_SUP_DIAL = 16;
    public static final int TONE_SUP_BUSY = 17;
    public static final int TONE_SUP_CONGESTION = 18;
    public static final int TONE_SUP_RADIO_ACK = 19;
    public static final int TONE_SUP_RADIO_NOTAVAIL = 20;
    public static final int TONE_SUP_ERROR = 21;
    public static final int TONE_SUP_CALL_WAITING = 22;
    public static final int TONE_SUP_RINGTONE = 23;
    public static final int TONE_PROP_BEEP = 24;
    public static final int TONE_PROP_ACK = 25;
    public static final int TONE_PROP_NACK = 26;
    public static final int TONE_PROP_PROMPT = 27;
    public static final int TONE_PROP_BEEP2 = 28;
    public static final int TONE_SUP_INTERCEPT = 29;
    public static final int TONE_SUP_INTERCEPT_ABBREV = 30;
    public static final int TONE_SUP_CONGESTION_ABBREV = 31;
    public static final int TONE_SUP_CONFIRM = 32;
    public static final int TONE_SUP_PIP = 33;
    public static final int TONE_CDMA_DIAL_TONE_LITE = 34;
    public static final int TONE_CDMA_NETWORK_USA_RINGBACK = 35;
    public static final int TONE_CDMA_INTERCEPT = 36;
    public static final int TONE_CDMA_ABBR_INTERCEPT = 37;
    public static final int TONE_CDMA_REORDER = 38;
    public static final int TONE_CDMA_ABBR_REORDER = 39;
    public static final int TONE_CDMA_NETWORK_BUSY = 40;
    public static final int TONE_CDMA_CONFIRM = 41;
    public static final int TONE_CDMA_ANSWER = 42;
    public static final int TONE_CDMA_NETWORK_CALLWAITING = 43;
    public static final int TONE_CDMA_PIP = 44;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL = 45;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_INTERGROUP = 46;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_SP_PRI = 47;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT3 = 48;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING = 49;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT5 = 50;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT6 = 51;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT7 = 52;
    public static final int TONE_CDMA_HIGH_L = 53;
    public static final int TONE_CDMA_MED_L = 54;
    public static final int TONE_CDMA_LOW_L = 55;
    public static final int TONE_CDMA_HIGH_SS = 56;
    public static final int TONE_CDMA_MED_SS = 57;
    public static final int TONE_CDMA_LOW_SS = 58;
    public static final int TONE_CDMA_HIGH_SSL = 59;
    public static final int TONE_CDMA_MED_SSL = 60;
    public static final int TONE_CDMA_LOW_SSL = 61;
    public static final int TONE_CDMA_HIGH_SS_2 = 62;
    public static final int TONE_CDMA_MED_SS_2 = 63;
    public static final int TONE_CDMA_LOW_SS_2 = 64;
    public static final int TONE_CDMA_HIGH_SLS = 65;
    public static final int TONE_CDMA_MED_SLS = 66;
    public static final int TONE_CDMA_LOW_SLS = 67;
    public static final int TONE_CDMA_HIGH_S_X4 = 68;
    public static final int TONE_CDMA_MED_S_X4 = 69;
    public static final int TONE_CDMA_LOW_S_X4 = 70;
    public static final int TONE_CDMA_HIGH_PBX_L = 71;
    public static final int TONE_CDMA_MED_PBX_L = 72;
    public static final int TONE_CDMA_LOW_PBX_L = 73;
    public static final int TONE_CDMA_HIGH_PBX_SS = 74;
    public static final int TONE_CDMA_MED_PBX_SS = 75;
    public static final int TONE_CDMA_LOW_PBX_SS = 76;
    public static final int TONE_CDMA_HIGH_PBX_SSL = 77;
    public static final int TONE_CDMA_MED_PBX_SSL = 78;
    public static final int TONE_CDMA_LOW_PBX_SSL = 79;
    public static final int TONE_CDMA_HIGH_PBX_SLS = 80;
    public static final int TONE_CDMA_MED_PBX_SLS = 81;
    public static final int TONE_CDMA_LOW_PBX_SLS = 82;
    public static final int TONE_CDMA_HIGH_PBX_S_X4 = 83;
    public static final int TONE_CDMA_MED_PBX_S_X4 = 84;
    public static final int TONE_CDMA_LOW_PBX_S_X4 = 85;
    public static final int TONE_CDMA_ALERT_NETWORK_LITE = 86;
    public static final int TONE_CDMA_ALERT_AUTOREDIAL_LITE = 87;
    public static final int TONE_CDMA_ONE_MIN_BEEP = 88;
    public static final int TONE_CDMA_KEYPAD_VOLUME_KEY_LITE = 89;
    public static final int TONE_CDMA_PRESSHOLDKEY_LITE = 90;
    public static final int TONE_CDMA_ALERT_INCALL_LITE = 91;
    public static final int TONE_CDMA_EMERGENCY_RINGBACK = 92;
    public static final int TONE_CDMA_ALERT_CALL_GUARD = 93;
    public static final int TONE_CDMA_SOFT_ERROR_LITE = 94;
    public static final int TONE_CDMA_CALLDROP_LITE = 95;
    public static final int TONE_CDMA_NETWORK_BUSY_ONE_SHOT = 96;
    public static final int TONE_CDMA_ABBR_ALERT = 97;
    public static final int TONE_CDMA_SIGNAL_OFF = 98;

    public static final int MAX_VOLUME = 100;
    public static final int MIN_VOLUME = 0;

    // AOSP ToneGenerator.h:153 -- the descriptor count. startTone() rejects any
    // type at or above it and there is no other reject.
    private static final int NUM_TONES = 99;
    // AOSP ToneGenerator.h:259: TONEGEN_INF is 0xFFFFFFFF, the unsigned
    // "forever" sentinel for a segment duration or a repeat count. Java has no
    // unsigned int, so use MAX_VALUE and always test for it before arithmetic.
    private static final int TONEGEN_INF = Integer.MAX_VALUE;
    // AOSP ToneGenerator.h:260: the total amplitude of a segment, shared out
    // across its sine waves so a 3-wave segment is no louder than a 1-wave one.
    private static final float TONEGEN_GAIN = 0.9f;

    // ---------------------------------------------------------------------
    // Descriptor model (AOSP ToneGenerator.h:262-294).
    //
    // A tone is a list of segments. A segment plays its waveFreq sine waves for
    // durationMs (no frequencies means silence, TONEGEN_INF means until
    // stopTone()). A segment whose loopCnt is non-zero jumps back to segment
    // loopIndx until it has looped loopCnt times. When the last segment ends
    // the whole sequence restarts at repeatSegment until it has repeated
    // repeatCnt times, so repeatCnt 0 plays the sequence exactly once.
    private static final class Segment {
        final int durationMs;
        final int[] waveFreq;
        final int loopCnt;
        final int loopIndx;
        Segment(int durationMs, int loopCnt, int loopIndx, int[] waveFreq) {
            this.durationMs = durationMs;
            this.loopCnt = loopCnt;
            this.loopIndx = loopIndx;
            this.waveFreq = waveFreq;
        }
    }

    private static final class ToneDescriptor {
        final Segment[] segments;
        final int repeatCnt;
        final int repeatSegment;
        ToneDescriptor(int repeatCnt, int repeatSegment, Segment... segments) {
            this.repeatCnt = repeatCnt;
            this.repeatSegment = repeatSegment;
            this.segments = segments;
        }
    }

    private static Segment seg(int durationMs, int... waveFreq) {
        return new Segment(durationMs, 0, 0, waveFreq);
    }

    private static Segment segLoop(int durationMs, int loopCnt, int loopIndx, int... waveFreq) {
        return new Segment(durationMs, loopCnt, loopIndx, waveFreq);
    }

    // AOSP's terminating zero-duration segment is implicit here: the end of the
    // segments array IS the end of the sequence.
    private static final ToneDescriptor[] TONES = buildToneDescriptors();

    private static ToneDescriptor[] buildToneDescriptors() {
        ToneDescriptor[] d = new ToneDescriptor[NUM_TONES];
        d[TONE_DTMF_0] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1336, 941));
        d[TONE_DTMF_1] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1209, 697));
        d[TONE_DTMF_2] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1336, 697));
        d[TONE_DTMF_3] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1477, 697));
        d[TONE_DTMF_4] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1209, 770));
        d[TONE_DTMF_5] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1336, 770));
        d[TONE_DTMF_6] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1477, 770));
        d[TONE_DTMF_7] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1209, 852));
        d[TONE_DTMF_8] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1336, 852));
        d[TONE_DTMF_9] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1477, 852));
        d[TONE_DTMF_S] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1209, 941));
        d[TONE_DTMF_P] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1477, 941));
        d[TONE_DTMF_A] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1633, 697));
        d[TONE_DTMF_B] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1633, 770));
        d[TONE_DTMF_C] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1633, 852));
        d[TONE_DTMF_D] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 1633, 941));
        d[TONE_SUP_DIAL] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 425));
        d[TONE_SUP_BUSY] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(500, 425),
                seg(500));
        d[TONE_SUP_CONGESTION] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(200, 425),
                seg(200));
        d[TONE_SUP_RADIO_ACK] = new ToneDescriptor(0, 0,
                seg(200, 425));
        d[TONE_SUP_RADIO_NOTAVAIL] = new ToneDescriptor(2, 0,
                seg(200, 425),
                seg(200));
        d[TONE_SUP_ERROR] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(330, 950, 1400, 1800),
                seg(1000));
        d[TONE_SUP_CALL_WAITING] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(200, 425),
                seg(600),
                seg(200, 425),
                seg(3000));
        d[TONE_SUP_RINGTONE] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(1000, 425),
                seg(4000));
        d[TONE_PROP_BEEP] = new ToneDescriptor(0, 0,
                seg(40, 400, 1200));
        d[TONE_PROP_ACK] = new ToneDescriptor(1, 0,
                seg(100, 1200),
                seg(100));
        d[TONE_PROP_NACK] = new ToneDescriptor(0, 0,
                seg(400, 300, 400, 500));
        d[TONE_PROP_PROMPT] = new ToneDescriptor(0, 0,
                seg(200, 400, 1200));
        d[TONE_PROP_BEEP2] = new ToneDescriptor(0, 0,
                seg(40, 400, 1200),
                seg(200),
                seg(40, 400, 1200));
        d[TONE_SUP_INTERCEPT] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(250, 440),
                seg(250, 620));
        d[TONE_SUP_INTERCEPT_ABBREV] = new ToneDescriptor(7, 0,
                seg(250, 440),
                seg(250, 620));
        d[TONE_SUP_CONGESTION_ABBREV] = new ToneDescriptor(7, 0,
                seg(250, 480, 620),
                seg(250));
        d[TONE_SUP_CONFIRM] = new ToneDescriptor(2, 0,
                seg(100, 350, 440),
                seg(100));
        d[TONE_SUP_PIP] = new ToneDescriptor(3, 0,
                seg(100, 480),
                seg(100));
        d[TONE_CDMA_DIAL_TONE_LITE] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(TONEGEN_INF, 425));
        d[TONE_CDMA_NETWORK_USA_RINGBACK] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(2000, 440, 480),
                seg(4000));
        d[TONE_CDMA_INTERCEPT] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(250, 440),
                seg(250, 620));
        d[TONE_CDMA_ABBR_INTERCEPT] = new ToneDescriptor(0, 0,
                seg(250, 440),
                seg(250, 620));
        d[TONE_CDMA_REORDER] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(250, 480, 620),
                seg(250));
        d[TONE_CDMA_ABBR_REORDER] = new ToneDescriptor(7, 0,
                seg(250, 480, 620),
                seg(250));
        d[TONE_CDMA_NETWORK_BUSY] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(500, 480, 620),
                seg(500));
        d[TONE_CDMA_CONFIRM] = new ToneDescriptor(2, 0,
                seg(100, 350, 440),
                seg(100));
        d[TONE_CDMA_ANSWER] = new ToneDescriptor(0, 0,
                seg(500, 660, 1000));
        d[TONE_CDMA_NETWORK_CALLWAITING] = new ToneDescriptor(0, 0,
                seg(300, 440));
        d[TONE_CDMA_PIP] = new ToneDescriptor(3, 0,
                seg(100, 480),
                seg(100));
        d[TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL] = new ToneDescriptor(0, 0,
                seg(32, 2091),
                segLoop(64, 19, 0, 2556),
                seg(32, 2091),
                seg(48, 2556),
                seg(4000));
        d[TONE_CDMA_CALL_SIGNAL_ISDN_INTERGROUP] = new ToneDescriptor(0, 0,
                seg(32, 2091),
                segLoop(64, 7, 0, 2556),
                seg(32, 2091),
                seg(400),
                seg(32, 2091),
                segLoop(64, 7, 4, 2556),
                seg(32, 2091),
                seg(4000));
        d[TONE_CDMA_CALL_SIGNAL_ISDN_SP_PRI] = new ToneDescriptor(0, 0,
                seg(32, 2091),
                segLoop(64, 3, 0, 2556),
                seg(16, 2091),
                seg(200),
                seg(32, 2091),
                segLoop(64, 3, 4, 2556),
                seg(16, 2091),
                seg(200));
        d[TONE_CDMA_CALL_SIGNAL_ISDN_PAT3] = new ToneDescriptor(0, 0);
        d[TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING] = new ToneDescriptor(0, 0,
                seg(32, 2091),
                segLoop(64, 4, 0, 2556),
                seg(20, 2091));
        d[TONE_CDMA_CALL_SIGNAL_ISDN_PAT5] = new ToneDescriptor(0, 0);
        d[TONE_CDMA_CALL_SIGNAL_ISDN_PAT6] = new ToneDescriptor(0, 0);
        d[TONE_CDMA_CALL_SIGNAL_ISDN_PAT7] = new ToneDescriptor(0, 0);
        d[TONE_CDMA_HIGH_L] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 39, 0, 4000),
                seg(4000));
        d[TONE_CDMA_MED_L] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 39, 0, 2900),
                seg(4000));
        d[TONE_CDMA_LOW_L] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 39, 0, 1450),
                seg(4000));
        d[TONE_CDMA_HIGH_SS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 15, 0, 4000),
                seg(400));
        d[TONE_CDMA_MED_SS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 15, 0, 2900),
                seg(400));
        d[TONE_CDMA_LOW_SS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 15, 0, 1450),
                seg(400));
        d[TONE_CDMA_HIGH_SSL] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 7, 0, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 7, 3, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 15, 6, 4000),
                seg(4000));
        d[TONE_CDMA_MED_SSL] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 7, 0, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 7, 3, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 15, 6, 2900),
                seg(4000));
        d[TONE_CDMA_LOW_SSL] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 7, 0, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 7, 3, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 15, 6, 1450),
                seg(4000));
        d[TONE_CDMA_HIGH_SS_2] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 19, 0, 4000),
                seg(1000),
                seg(25, 3700),
                segLoop(25, 19, 3, 4000),
                seg(3000));
        d[TONE_CDMA_MED_SS_2] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 19, 0, 2900),
                seg(1000),
                seg(25, 2600),
                segLoop(25, 19, 3, 2900),
                seg(3000));
        d[TONE_CDMA_LOW_SS_2] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 19, 0, 1450),
                seg(1000),
                seg(25, 1300),
                segLoop(25, 19, 3, 1450),
                seg(3000));
        d[TONE_CDMA_HIGH_SLS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 9, 0, 4000),
                seg(500),
                seg(25, 3700),
                segLoop(25, 19, 3, 4000),
                seg(500),
                seg(25, 3700),
                segLoop(25, 9, 6, 4000),
                seg(3000));
        d[TONE_CDMA_MED_SLS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 9, 0, 2900),
                seg(500),
                seg(25, 2600),
                segLoop(25, 19, 3, 2900),
                seg(500),
                seg(25, 2600),
                segLoop(25, 9, 6, 2900),
                seg(3000));
        d[TONE_CDMA_LOW_SLS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 9, 0, 1450),
                seg(500),
                seg(25, 1300),
                segLoop(25, 19, 3, 1450),
                seg(500),
                seg(25, 1300),
                segLoop(25, 9, 6, 1450),
                seg(3000));
        d[TONE_CDMA_HIGH_S_X4] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 9, 0, 4000),
                seg(500),
                seg(25, 3700),
                segLoop(25, 9, 3, 4000),
                seg(500),
                seg(25, 3700),
                segLoop(25, 9, 6, 4000),
                seg(500),
                seg(25, 3700),
                segLoop(25, 9, 9, 4000),
                seg(2500));
        d[TONE_CDMA_MED_S_X4] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 9, 0, 2900),
                seg(500),
                seg(25, 2600),
                segLoop(25, 9, 3, 2900),
                seg(500),
                seg(25, 2600),
                segLoop(25, 9, 6, 2900),
                seg(500),
                seg(25, 2600),
                segLoop(25, 9, 9, 2900),
                seg(2500));
        d[TONE_CDMA_LOW_S_X4] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 9, 0, 1450),
                seg(500),
                seg(25, 1300),
                segLoop(25, 9, 3, 1450),
                seg(500),
                seg(25, 1300),
                segLoop(25, 9, 6, 1450),
                seg(500),
                seg(25, 1300),
                segLoop(25, 9, 9, 1450),
                seg(2500));
        d[TONE_CDMA_HIGH_PBX_L] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 19, 0, 4000),
                seg(2000));
        d[TONE_CDMA_MED_PBX_L] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 19, 0, 2900),
                seg(2000));
        d[TONE_CDMA_LOW_PBX_L] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 19, 0, 1450),
                seg(2000));
        d[TONE_CDMA_HIGH_PBX_SS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 7, 0, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 7, 3, 4000),
                seg(2000));
        d[TONE_CDMA_MED_PBX_SS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 7, 0, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 7, 3, 2900),
                seg(2000));
        d[TONE_CDMA_LOW_PBX_SS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 7, 0, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 7, 3, 1450),
                seg(2000));
        d[TONE_CDMA_HIGH_PBX_SSL] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 7, 0, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 7, 3, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 15, 6, 4000),
                seg(1000));
        d[TONE_CDMA_MED_PBX_SSL] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 7, 0, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 7, 3, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 15, 6, 2900),
                seg(1000));
        d[TONE_CDMA_LOW_PBX_SSL] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 7, 0, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 7, 3, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 15, 6, 1450),
                seg(1000));
        d[TONE_CDMA_HIGH_PBX_SLS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 7, 0, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 15, 3, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 7, 6, 4000),
                seg(1000));
        d[TONE_CDMA_MED_PBX_SLS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 7, 0, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 15, 3, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 7, 6, 2900),
                seg(1000));
        d[TONE_CDMA_LOW_PBX_SLS] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 7, 0, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 15, 3, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 7, 6, 1450),
                seg(1000));
        d[TONE_CDMA_HIGH_PBX_S_X4] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 3700),
                segLoop(25, 7, 0, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 7, 3, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 7, 6, 4000),
                seg(200),
                seg(25, 3700),
                segLoop(25, 7, 9, 4000),
                seg(800));
        d[TONE_CDMA_MED_PBX_S_X4] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 2600),
                segLoop(25, 7, 0, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 7, 3, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 7, 6, 2900),
                seg(200),
                seg(25, 2600),
                segLoop(25, 7, 9, 2900),
                seg(800));
        d[TONE_CDMA_LOW_PBX_S_X4] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(25, 1300),
                segLoop(25, 7, 0, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 7, 3, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 7, 6, 1450),
                seg(200),
                seg(25, 1300),
                segLoop(25, 7, 9, 1450),
                seg(800));
        d[TONE_CDMA_ALERT_NETWORK_LITE] = new ToneDescriptor(0, 0,
                seg(62, 1109),
                seg(62, 784),
                seg(62, 740),
                seg(62, 622),
                seg(62, 1109));
        d[TONE_CDMA_ALERT_AUTOREDIAL_LITE] = new ToneDescriptor(0, 0,
                seg(62, 1245),
                segLoop(62, 2, 0, 659),
                seg(62, 1245));
        d[TONE_CDMA_ONE_MIN_BEEP] = new ToneDescriptor(0, 0,
                seg(400, 1150, 770));
        d[TONE_CDMA_KEYPAD_VOLUME_KEY_LITE] = new ToneDescriptor(0, 0,
                seg(120, 941, 1477));
        d[TONE_CDMA_PRESSHOLDKEY_LITE] = new ToneDescriptor(0, 0,
                seg(375, 587),
                seg(125, 1175));
        d[TONE_CDMA_ALERT_INCALL_LITE] = new ToneDescriptor(0, 0,
                seg(62, 587),
                seg(62, 784),
                seg(62, 831),
                seg(62, 784),
                seg(62, 1109),
                seg(62, 784),
                seg(62, 831),
                seg(62, 784));
        d[TONE_CDMA_EMERGENCY_RINGBACK] = new ToneDescriptor(TONEGEN_INF, 0,
                seg(125, 941),
                segLoop(10, 2, 0),
                seg(4990));
        d[TONE_CDMA_ALERT_CALL_GUARD] = new ToneDescriptor(2, 0,
                seg(125, 1319),
                seg(125));
        d[TONE_CDMA_SOFT_ERROR_LITE] = new ToneDescriptor(0, 0,
                seg(125, 1047),
                seg(125, 370));
        d[TONE_CDMA_CALLDROP_LITE] = new ToneDescriptor(0, 0,
                seg(125, 1480),
                seg(125, 1397),
                seg(125, 784));
        d[TONE_CDMA_NETWORK_BUSY_ONE_SHOT] = new ToneDescriptor(0, 0,
                seg(500, 425),
                seg(500));
        d[TONE_CDMA_ABBR_ALERT] = new ToneDescriptor(0, 0,
                seg(400, 1150, 770));
        d[TONE_CDMA_SIGNAL_OFF] = new ToneDescriptor(0, 0);
        return d;
    }

    // ---------------------------------------------------------------------
    // Playback.

    private final int mStreamType;
    private final float mVolume;          // linear gain 0..1, see linearToLog

    private final Object mLock = new Object();
    private AudioTrack mTrack;
    private int mTrackSampleRate;
    private int mTrackBufferFrames;
    private Thread mWorker;
    private ToneDescriptor mPending;      // what the worker should be playing
    private int mPendingDurationMs;
    private int mGeneration;              // bumped by every start/stop/release
    private boolean mReleased;

    // AOSP builds its AudioTrack in the constructor and throws RuntimeException
    // when the audio HAL is missing. Mac.apk's CoreAudio stream is cheap but not
    // free, so the track is created on the first startTone() instead; nothing
    // observable depends on the difference (getAudioSessionId reports 0 for a
    // track that does not exist yet, which is what AOSP reports for a null
    // track too).
    public ToneGenerator(int streamType, int volume) {
        mStreamType = streamType;
        mVolume = linearToLog(volume);
    }

    /**
     * AOSP: a tone plays until it ends by itself or until stopTone().
     */
    public boolean startTone(int toneType) {
        return startTone(toneType, -1);
    }

    /**
     * AOSP: durationMs caps the tone; -1 means "as defined by the tone".
     */
    public boolean startTone(int toneType, int durationMs) {
        // ToneGenerator.cpp:1149 -- out of range is the only hard reject. A
        // negative type reaches the native layer as a huge unsigned and fails
        // the same test.
        if (toneType < 0 || toneType >= NUM_TONES) {
            return false;
        }
        // ToneGenerator.cpp:1153 -- this one means "stop signaling"; it reports
        // success and plays nothing.
        if (toneType == TONE_CDMA_SIGNAL_OFF) {
            return true;
        }
        ToneDescriptor desc = TONES[toneType];
        if (desc == null) {
            return false;
        }
        // AOSP ships the ISDN pattern slots PAT3/PAT5/PAT6/PAT7 as bare
        // zero-duration placeholders (ToneGenerator.cpp:270, 279, 282, 285), so
        // prepareWave() builds no oscillator and the sequencer stops on its
        // first callback: a real device reports success and stays silent for
        // exactly these. Report the same rather than inventing a failure.
        if (desc.segments.length == 0) {
            return true;
        }
        synchronized (mLock) {
            if (mReleased) {
                return false;
            }
            if (!ensureTrackLocked()) {
                return false;
            }
            // AOSP: "only one tone can play at a time: if a tone is playing
            // while this method is called, this tone is stopped and replaced by
            // the one requested." Bumping the generation is what tells the
            // worker its current tone has been superseded.
            mPending = desc;
            mPendingDurationMs = durationMs;
            mGeneration++;
            if (mWorker == null) {
                mWorker = new Thread(new Runnable() {
                    public void run() { renderLoop(); }
                }, "ToneGenerator");
                mWorker.setDaemon(true);
                mWorker.start();
            }
            mLock.notifyAll();
        }
        return true;
    }

    public void stopTone() {
        synchronized (mLock) {
            mPending = null;
            mGeneration++;
            mLock.notifyAll();
        }
    }

    /** AOSP declares finalize() so a dropped generator still releases its
     *  native tone track. release() above already guards on mReleased, so this
     *  is safe on an already-released instance. */
    @Override protected void finalize() throws Throwable {
        try { release(); } finally { super.finalize(); }
    }

    public void release() {
        AudioTrack orphan = null;
        synchronized (mLock) {
            if (mReleased) {
                return;
            }
            mPending = null;
            mReleased = true;
            mGeneration++;
            if (mWorker == null) {
                // Nothing ever played, so no worker owns the track.
                orphan = mTrack;
                mTrack = null;
            }
            mLock.notifyAll();
        }
        // The worker tears its own track down on the way out, so the track is
        // never released underneath a thread that is still writing to it.
        if (orphan != null) {
            orphan.release();
        }
    }

    /** AOSP: the session this generator plays on, 0 when there is no track. */
    public int getAudioSessionId() {
        synchronized (mLock) {
            return mTrack != null ? mTrack.getAudioSessionId() : 0;
        }
    }

    // AOSP AudioSystem::linearToLog (frameworks/av media/libaudioclient/
    // AudioSystem.cpp:447-459), which android_media_ToneGenerator.cpp:99 runs
    // over the app's 0..100 before handing it to the synthesizer: the
    // percentage is a volume-step count at 0.5 dB per step, so 100 is unity
    // gain and 0 is silence. AudioTrack::setVolume clamps above unity natively;
    // do the clamp here since our AudioTrack passes the gain straight through.
    private static float linearToLog(int volume) {
        final float kdbPerStep = 0.5f;
        final float kdBConvert = -kdbPerStep * 2.302585093f / 20.0f;
        if (volume <= MIN_VOLUME) {
            return 0.0f;
        }
        if (volume > MAX_VOLUME) {
            volume = MAX_VOLUME;
        }
        return (float) Math.exp((100 - volume) * kdBConvert);
    }

    private boolean ensureTrackLocked() {
        if (mTrack != null) {
            return true;
        }
        final int sr = AudioTrack.getNativeOutputSampleRate(mStreamType);
        final int chunkFrames = chunkFrames(sr);
        final int minBytes = AudioTrack.getMinBufferSize(
                sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        // Four render chunks of headroom so the ring cannot underrun between
        // writes. Tones are mono 16-bit, matching the native synthesizer.
        final int bytes = Math.max(minBytes, chunkFrames * 2 * 4);
        AudioTrack track;
        try {
            track = new AudioTrack(mStreamType, sr, AudioFormat.CHANNEL_OUT_MONO,
                                   AudioFormat.ENCODING_PCM_16BIT, bytes,
                                   AudioTrack.MODE_STREAM);
        } catch (Throwable t) {
            return false;
        }
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            return false;
        }
        mTrack = track;
        mTrackSampleRate = sr;
        mTrackBufferFrames = track.getBufferSizeInFrames();
        return true;
    }

    private static int chunkFrames(int sampleRate) {
        // 20 ms: short enough that stopTone() cuts the tone promptly, long
        // enough that the per-write overhead is irrelevant.
        return Math.max(64, sampleRate / 50);
    }

    private void renderLoop() {
        final int sr;
        final int drainMs;
        synchronized (mLock) {
            sr = mTrackSampleRate;
            drainMs = Math.max(1, (int) ((long) mTrackBufferFrames * 1000L / mTrackSampleRate));
        }
        final short[] buf = new short[chunkFrames(sr)];
        while (true) {
            ToneDescriptor desc;
            int durationMs;
            int gen;
            synchronized (mLock) {
                while (!mReleased && mPending == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        mReleased = true;
                    }
                }
                if (mReleased) {
                    break;
                }
                desc = mPending;
                durationMs = mPendingDurationMs;
                gen = mGeneration;
            }
            playTone(desc, durationMs, gen, buf, sr);
            synchronized (mLock) {
                if (mGeneration == gen) {
                    // The tone ended on its own. Let the ring drain before
                    // parking the stream, otherwise the pause truncates the
                    // tail we just wrote; a tone that arrives during the drain
                    // wins instead.
                    mPending = null;
                    try {
                        mLock.wait(drainMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        mReleased = true;
                    }
                }
                // Park the stream whenever nothing is queued: a natural end,
                // a stopTone() (which truncates, as AOSP's does), or release().
                // A replacement tone leaves it running so there is no gap.
                if (mPending == null && mTrack != null) {
                    mTrack.pause();
                    mTrack.flush();
                }
            }
        }
        AudioTrack dying;
        synchronized (mLock) {
            dying = mTrack;
            mTrack = null;
        }
        if (dying != null) {
            dying.stop();
            dying.release();
        }
    }

    // The sequencer, mirroring ToneGenerator.cpp:1428-1530.
    private void playTone(ToneDescriptor desc, int durationMs, int gen, short[] buf, int sr) {
        final AudioTrack track;
        final long leadNanos;   // one buffer of slack, see pace()
        synchronized (mLock) {
            track = mTrack;
            leadNanos = (long) mTrackBufferFrames * 1000000000L / sr;
        }
        if (track == null || desc == null || desc.segments.length == 0) {
            return;
        }
        // ToneGenerator.cpp:1636-1642 -- a negative duration means "as long as
        // the descriptor says", otherwise the tone is cut at durationMs no
        // matter where its own sequence has got to.
        final long maxSmp = (durationMs < 0)
                ? Long.MAX_VALUE : (long) durationMs * sr / 1000L;

        track.setVolume(mVolume);
        track.play();

        int curSegment = 0;
        int curCount = 0;
        int loopCounter = 0;
        long totalSmp = 0;
        long segSmp = 0;
        final long startNanos = System.nanoTime();

        while (totalSmp < maxSmp && curSegment < desc.segments.length) {
            synchronized (mLock) {
                if (mReleased || mGeneration != gen) {
                    return;   // stopTone(), release(), or a replacement tone
                }
            }
            final Segment s = desc.segments[curSegment];
            final long segLen = (s.durationMs == TONEGEN_INF)
                    ? Long.MAX_VALUE : (long) s.durationMs * sr / 1000L;

            if (segSmp < segLen) {
                int n = buf.length;
                if (segLen - segSmp < n) {
                    n = (int) (segLen - segSmp);
                }
                if (maxSmp - totalSmp < n) {
                    n = (int) (maxSmp - totalSmp);
                }
                fillSegment(buf, n, s.waveFreq, segSmp, sr);
                int written = track.write(buf, 0, n);
                if (written <= 0) {
                    return;   // the output went away
                }
                segSmp += written;
                totalSmp += written;
                pace(startNanos, totalSmp, sr, leadNanos);
                continue;
            }

            // Segment finished. ToneGenerator.cpp:1463-1487: an in-segment loop
            // takes priority, otherwise advance.
            if (s.loopCnt != 0 && loopCounter < s.loopCnt) {
                loopCounter++;
                curSegment = s.loopIndx;
            } else {
                if (s.loopCnt != 0) {
                    loopCounter = 0;
                }
                curSegment++;
            }
            segSmp = 0;

            if (curSegment >= desc.segments.length) {
                // ToneGenerator.cpp:1490-1500: at the end of the sequence,
                // repeat from repeatSegment while curCount stays within
                // repeatCnt. repeatCnt 0 therefore plays the sequence once.
                curCount++;
                if (desc.repeatCnt != TONEGEN_INF && curCount > desc.repeatCnt) {
                    break;
                }
                curSegment = desc.repeatSegment;
                loopCounter = 0;
                if (curSegment < 0 || curSegment >= desc.segments.length) {
                    break;
                }
            }
        }
    }

    // ToneGenerator::WaveGenerator (ToneGenerator.cpp:1780-1800): each wave in
    // a segment is generated at TONEGEN_GAIN/numWaves so the summed segment
    // peaks at TONEGEN_GAIN regardless of how many waves it has. Phase restarts
    // at every segment boundary, which is why this takes the segment-relative
    // sample index (AOSP re-primes the generator with WAVEGEN_START there).
    private static void fillSegment(short[] buf, int n, int[] waveFreq, long segSmp, int sr) {
        final int waves = waveFreq.length;
        if (waves == 0) {
            java.util.Arrays.fill(buf, 0, n, (short) 0);
            return;
        }
        final double amplitude = (TONEGEN_GAIN / waves) * 32767.0;
        for (int i = 0; i < n; ++i) {
            final double t = (double) (segSmp + i) / sr;
            double v = 0.0;
            for (int w = 0; w < waves; ++w) {
                v += Math.sin(2.0 * Math.PI * waveFreq[w] * t);
            }
            v *= amplitude;
            if (v > 32767.0) {
                v = 32767.0;
            } else if (v < -32768.0) {
                v = -32768.0;
            }
            buf[i] = (short) v;
        }
    }

    // The CoreAudio ring applies real backpressure, so write() paces this
    // thread by itself and this is a no-op. When no output stream could be
    // opened AudioTrack.write() returns instantly, which would spin a core for
    // the whole length of an unbounded tone; never run more than one buffer
    // ahead of the wall clock so that case costs nothing.
    private static void pace(long startNanos, long framesWritten, int sr, long leadNanos) {
        final long dueNanos = startNanos + framesWritten * 1000000000L / sr;
        final long aheadNanos = dueNanos - System.nanoTime() - leadNanos;
        if (aheadNanos <= 0) {
            return;
        }
        try {
            Thread.sleep(aheadNanos / 1000000L, (int) (aheadNanos % 1000000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
