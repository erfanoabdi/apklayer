// android.view.KeyEvent -- Mac.apk's reimplementation of the AOSP class.
//
// Mirrors frameworks/base/core/java/android/view/KeyEvent.java (android-11.0.0_r48,
// API 30 -- the level our emulator golden was recorded at). Two things make this
// class worth getting exactly right rather than stubbing:
//
//  1. Host keystrokes are live through it. cocoa_bridge -> TouchDispatcher.dispatchKey
//     -> Activity.dispatchKeyEvent constructs a real KeyEvent per keypress, so every
//     getter here is on the typing path for EditText/InputConnection and for engines
//     (Unity, libGDX) that read key state themselves.
//  2. Engines build keycode TABLES from getMaxKeyCode()/keyCodeToString(). Answering
//     with a truncated keycode range silently truncates the engine's own table.
//
// The character-resolving getters (getUnicodeChar/getDisplayLabel/getNumber/
// getMatch/isPrintingKey) all delegate to KeyCharacterMap exactly as AOSP does --
// see that class for the data source. Resolving them here instead was the bug that
// made all of them disagree with the reference at once.
package android.view;

import android.os.Parcel;
import android.os.Parcelable;

public class KeyEvent extends InputEvent implements Parcelable {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP   = 1;
    public static final int ACTION_MULTIPLE = 2;

    // ---- key codes -------------------------------------------------------
    //
    // The COMPLETE AOSP keycode catalog, values 0..288, transcribed
    // mechanically from
    // raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/
    //   android-11.0.0_r48/core/java/android/view/KeyEvent.java:89-826
    // rather than by hand (every value was diffed against that file; the set is
    // exactly 0..288 with no duplicates and no gaps).
    //
    // These values are COMPILED INTO APPS AS LITERALS, so a wrong value here is
    // undetectable at link time and corrupts input silently. Never edit a value;
    // only ever append what AOSP appends.
    //
    // The full catalog (not the curated subset this class used to carry) is what
    // makes keyCodeToString/keyCodeFromString/getMaxKeyCode answer correctly --
    // they are driven by this field set, see NameTable below.
    public static final int KEYCODE_UNKNOWN                        = 0;
    public static final int KEYCODE_SOFT_LEFT                      = 1;
    public static final int KEYCODE_SOFT_RIGHT                     = 2;
    public static final int KEYCODE_HOME                           = 3;
    public static final int KEYCODE_BACK                           = 4;
    public static final int KEYCODE_CALL                           = 5;
    public static final int KEYCODE_ENDCALL                        = 6;
    public static final int KEYCODE_0                              = 7;
    public static final int KEYCODE_1                              = 8;
    public static final int KEYCODE_2                              = 9;
    public static final int KEYCODE_3                              = 10;
    public static final int KEYCODE_4                              = 11;
    public static final int KEYCODE_5                              = 12;
    public static final int KEYCODE_6                              = 13;
    public static final int KEYCODE_7                              = 14;
    public static final int KEYCODE_8                              = 15;
    public static final int KEYCODE_9                              = 16;
    public static final int KEYCODE_STAR                           = 17;
    public static final int KEYCODE_POUND                          = 18;
    public static final int KEYCODE_DPAD_UP                        = 19;
    public static final int KEYCODE_DPAD_DOWN                      = 20;
    public static final int KEYCODE_DPAD_LEFT                      = 21;
    public static final int KEYCODE_DPAD_RIGHT                     = 22;
    public static final int KEYCODE_DPAD_CENTER                    = 23;
    public static final int KEYCODE_VOLUME_UP                      = 24;
    public static final int KEYCODE_VOLUME_DOWN                    = 25;
    public static final int KEYCODE_POWER                          = 26;
    public static final int KEYCODE_CAMERA                         = 27;
    public static final int KEYCODE_CLEAR                          = 28;
    public static final int KEYCODE_A                              = 29;
    public static final int KEYCODE_B                              = 30;
    public static final int KEYCODE_C                              = 31;
    public static final int KEYCODE_D                              = 32;
    public static final int KEYCODE_E                              = 33;
    public static final int KEYCODE_F                              = 34;
    public static final int KEYCODE_G                              = 35;
    public static final int KEYCODE_H                              = 36;
    public static final int KEYCODE_I                              = 37;
    public static final int KEYCODE_J                              = 38;
    public static final int KEYCODE_K                              = 39;
    public static final int KEYCODE_L                              = 40;
    public static final int KEYCODE_M                              = 41;
    public static final int KEYCODE_N                              = 42;
    public static final int KEYCODE_O                              = 43;
    public static final int KEYCODE_P                              = 44;
    public static final int KEYCODE_Q                              = 45;
    public static final int KEYCODE_R                              = 46;
    public static final int KEYCODE_S                              = 47;
    public static final int KEYCODE_T                              = 48;
    public static final int KEYCODE_U                              = 49;
    public static final int KEYCODE_V                              = 50;
    public static final int KEYCODE_W                              = 51;
    public static final int KEYCODE_X                              = 52;
    public static final int KEYCODE_Y                              = 53;
    public static final int KEYCODE_Z                              = 54;
    public static final int KEYCODE_COMMA                          = 55;
    public static final int KEYCODE_PERIOD                         = 56;
    public static final int KEYCODE_ALT_LEFT                       = 57;
    public static final int KEYCODE_ALT_RIGHT                      = 58;
    public static final int KEYCODE_SHIFT_LEFT                     = 59;
    public static final int KEYCODE_SHIFT_RIGHT                    = 60;
    public static final int KEYCODE_TAB                            = 61;
    public static final int KEYCODE_SPACE                          = 62;
    public static final int KEYCODE_SYM                            = 63;
    public static final int KEYCODE_EXPLORER                       = 64;
    public static final int KEYCODE_ENVELOPE                       = 65;
    public static final int KEYCODE_ENTER                          = 66;
    public static final int KEYCODE_DEL                            = 67;
    public static final int KEYCODE_GRAVE                          = 68;
    public static final int KEYCODE_MINUS                          = 69;
    public static final int KEYCODE_EQUALS                         = 70;
    public static final int KEYCODE_LEFT_BRACKET                   = 71;
    public static final int KEYCODE_RIGHT_BRACKET                  = 72;
    public static final int KEYCODE_BACKSLASH                      = 73;
    public static final int KEYCODE_SEMICOLON                      = 74;
    public static final int KEYCODE_APOSTROPHE                     = 75;
    public static final int KEYCODE_SLASH                          = 76;
    public static final int KEYCODE_AT                             = 77;
    public static final int KEYCODE_NUM                            = 78;
    public static final int KEYCODE_HEADSETHOOK                    = 79;
    public static final int KEYCODE_FOCUS                          = 80;
    public static final int KEYCODE_PLUS                           = 81;
    public static final int KEYCODE_MENU                           = 82;
    public static final int KEYCODE_NOTIFICATION                   = 83;
    public static final int KEYCODE_SEARCH                         = 84;
    public static final int KEYCODE_MEDIA_PLAY_PAUSE               = 85;
    public static final int KEYCODE_MEDIA_STOP                     = 86;
    public static final int KEYCODE_MEDIA_NEXT                     = 87;
    public static final int KEYCODE_MEDIA_PREVIOUS                 = 88;
    public static final int KEYCODE_MEDIA_REWIND                   = 89;
    public static final int KEYCODE_MEDIA_FAST_FORWARD             = 90;
    public static final int KEYCODE_MUTE                           = 91;
    public static final int KEYCODE_PAGE_UP                        = 92;
    public static final int KEYCODE_PAGE_DOWN                      = 93;
    public static final int KEYCODE_PICTSYMBOLS                    = 94;
    public static final int KEYCODE_SWITCH_CHARSET                 = 95;
    public static final int KEYCODE_BUTTON_A                       = 96;
    public static final int KEYCODE_BUTTON_B                       = 97;
    public static final int KEYCODE_BUTTON_C                       = 98;
    public static final int KEYCODE_BUTTON_X                       = 99;
    public static final int KEYCODE_BUTTON_Y                       = 100;
    public static final int KEYCODE_BUTTON_Z                       = 101;
    public static final int KEYCODE_BUTTON_L1                      = 102;
    public static final int KEYCODE_BUTTON_R1                      = 103;
    public static final int KEYCODE_BUTTON_L2                      = 104;
    public static final int KEYCODE_BUTTON_R2                      = 105;
    public static final int KEYCODE_BUTTON_THUMBL                  = 106;
    public static final int KEYCODE_BUTTON_THUMBR                  = 107;
    public static final int KEYCODE_BUTTON_START                   = 108;
    public static final int KEYCODE_BUTTON_SELECT                  = 109;
    public static final int KEYCODE_BUTTON_MODE                    = 110;
    public static final int KEYCODE_ESCAPE                         = 111;
    public static final int KEYCODE_FORWARD_DEL                    = 112;
    public static final int KEYCODE_CTRL_LEFT                      = 113;
    public static final int KEYCODE_CTRL_RIGHT                     = 114;
    public static final int KEYCODE_CAPS_LOCK                      = 115;
    public static final int KEYCODE_SCROLL_LOCK                    = 116;
    public static final int KEYCODE_META_LEFT                      = 117;
    public static final int KEYCODE_META_RIGHT                     = 118;
    public static final int KEYCODE_FUNCTION                       = 119;
    public static final int KEYCODE_SYSRQ                          = 120;
    public static final int KEYCODE_BREAK                          = 121;
    public static final int KEYCODE_MOVE_HOME                      = 122;
    public static final int KEYCODE_MOVE_END                       = 123;
    public static final int KEYCODE_INSERT                         = 124;
    public static final int KEYCODE_FORWARD                        = 125;
    public static final int KEYCODE_MEDIA_PLAY                     = 126;
    public static final int KEYCODE_MEDIA_PAUSE                    = 127;
    public static final int KEYCODE_MEDIA_CLOSE                    = 128;
    public static final int KEYCODE_MEDIA_EJECT                    = 129;
    public static final int KEYCODE_MEDIA_RECORD                   = 130;
    public static final int KEYCODE_F1                             = 131;
    public static final int KEYCODE_F2                             = 132;
    public static final int KEYCODE_F3                             = 133;
    public static final int KEYCODE_F4                             = 134;
    public static final int KEYCODE_F5                             = 135;
    public static final int KEYCODE_F6                             = 136;
    public static final int KEYCODE_F7                             = 137;
    public static final int KEYCODE_F8                             = 138;
    public static final int KEYCODE_F9                             = 139;
    public static final int KEYCODE_F10                            = 140;
    public static final int KEYCODE_F11                            = 141;
    public static final int KEYCODE_F12                            = 142;
    public static final int KEYCODE_NUM_LOCK                       = 143;
    public static final int KEYCODE_NUMPAD_0                       = 144;
    public static final int KEYCODE_NUMPAD_1                       = 145;
    public static final int KEYCODE_NUMPAD_2                       = 146;
    public static final int KEYCODE_NUMPAD_3                       = 147;
    public static final int KEYCODE_NUMPAD_4                       = 148;
    public static final int KEYCODE_NUMPAD_5                       = 149;
    public static final int KEYCODE_NUMPAD_6                       = 150;
    public static final int KEYCODE_NUMPAD_7                       = 151;
    public static final int KEYCODE_NUMPAD_8                       = 152;
    public static final int KEYCODE_NUMPAD_9                       = 153;
    public static final int KEYCODE_NUMPAD_DIVIDE                  = 154;
    public static final int KEYCODE_NUMPAD_MULTIPLY                = 155;
    public static final int KEYCODE_NUMPAD_SUBTRACT                = 156;
    public static final int KEYCODE_NUMPAD_ADD                     = 157;
    public static final int KEYCODE_NUMPAD_DOT                     = 158;
    public static final int KEYCODE_NUMPAD_COMMA                   = 159;
    public static final int KEYCODE_NUMPAD_ENTER                   = 160;
    public static final int KEYCODE_NUMPAD_EQUALS                  = 161;
    public static final int KEYCODE_NUMPAD_LEFT_PAREN              = 162;
    public static final int KEYCODE_NUMPAD_RIGHT_PAREN             = 163;
    public static final int KEYCODE_VOLUME_MUTE                    = 164;
    public static final int KEYCODE_INFO                           = 165;
    public static final int KEYCODE_CHANNEL_UP                     = 166;
    public static final int KEYCODE_CHANNEL_DOWN                   = 167;
    public static final int KEYCODE_ZOOM_IN                        = 168;
    public static final int KEYCODE_ZOOM_OUT                       = 169;
    public static final int KEYCODE_TV                             = 170;
    public static final int KEYCODE_WINDOW                         = 171;
    public static final int KEYCODE_GUIDE                          = 172;
    public static final int KEYCODE_DVR                            = 173;
    public static final int KEYCODE_BOOKMARK                       = 174;
    public static final int KEYCODE_CAPTIONS                       = 175;
    public static final int KEYCODE_SETTINGS                       = 176;
    public static final int KEYCODE_TV_POWER                       = 177;
    public static final int KEYCODE_TV_INPUT                       = 178;
    public static final int KEYCODE_STB_POWER                      = 179;
    public static final int KEYCODE_STB_INPUT                      = 180;
    public static final int KEYCODE_AVR_POWER                      = 181;
    public static final int KEYCODE_AVR_INPUT                      = 182;
    public static final int KEYCODE_PROG_RED                       = 183;
    public static final int KEYCODE_PROG_GREEN                     = 184;
    public static final int KEYCODE_PROG_YELLOW                    = 185;
    public static final int KEYCODE_PROG_BLUE                      = 186;
    public static final int KEYCODE_APP_SWITCH                     = 187;
    public static final int KEYCODE_BUTTON_1                       = 188;
    public static final int KEYCODE_BUTTON_2                       = 189;
    public static final int KEYCODE_BUTTON_3                       = 190;
    public static final int KEYCODE_BUTTON_4                       = 191;
    public static final int KEYCODE_BUTTON_5                       = 192;
    public static final int KEYCODE_BUTTON_6                       = 193;
    public static final int KEYCODE_BUTTON_7                       = 194;
    public static final int KEYCODE_BUTTON_8                       = 195;
    public static final int KEYCODE_BUTTON_9                       = 196;
    public static final int KEYCODE_BUTTON_10                      = 197;
    public static final int KEYCODE_BUTTON_11                      = 198;
    public static final int KEYCODE_BUTTON_12                      = 199;
    public static final int KEYCODE_BUTTON_13                      = 200;
    public static final int KEYCODE_BUTTON_14                      = 201;
    public static final int KEYCODE_BUTTON_15                      = 202;
    public static final int KEYCODE_BUTTON_16                      = 203;
    public static final int KEYCODE_LANGUAGE_SWITCH                = 204;
    public static final int KEYCODE_MANNER_MODE                    = 205;
    public static final int KEYCODE_3D_MODE                        = 206;
    public static final int KEYCODE_CONTACTS                       = 207;
    public static final int KEYCODE_CALENDAR                       = 208;
    public static final int KEYCODE_MUSIC                          = 209;
    public static final int KEYCODE_CALCULATOR                     = 210;
    public static final int KEYCODE_ZENKAKU_HANKAKU                = 211;
    public static final int KEYCODE_EISU                           = 212;
    public static final int KEYCODE_MUHENKAN                       = 213;
    public static final int KEYCODE_HENKAN                         = 214;
    public static final int KEYCODE_KATAKANA_HIRAGANA              = 215;
    public static final int KEYCODE_YEN                            = 216;
    public static final int KEYCODE_RO                             = 217;
    public static final int KEYCODE_KANA                           = 218;
    public static final int KEYCODE_ASSIST                         = 219;
    public static final int KEYCODE_BRIGHTNESS_DOWN                = 220;
    public static final int KEYCODE_BRIGHTNESS_UP                  = 221;
    public static final int KEYCODE_MEDIA_AUDIO_TRACK              = 222;
    public static final int KEYCODE_SLEEP                          = 223;
    public static final int KEYCODE_WAKEUP                         = 224;
    public static final int KEYCODE_PAIRING                        = 225;
    public static final int KEYCODE_MEDIA_TOP_MENU                 = 226;
    public static final int KEYCODE_11                             = 227;
    public static final int KEYCODE_12                             = 228;
    public static final int KEYCODE_LAST_CHANNEL                   = 229;
    public static final int KEYCODE_TV_DATA_SERVICE                = 230;
    public static final int KEYCODE_VOICE_ASSIST                   = 231;
    public static final int KEYCODE_TV_RADIO_SERVICE               = 232;
    public static final int KEYCODE_TV_TELETEXT                    = 233;
    public static final int KEYCODE_TV_NUMBER_ENTRY                = 234;
    public static final int KEYCODE_TV_TERRESTRIAL_ANALOG          = 235;
    public static final int KEYCODE_TV_TERRESTRIAL_DIGITAL         = 236;
    public static final int KEYCODE_TV_SATELLITE                   = 237;
    public static final int KEYCODE_TV_SATELLITE_BS                = 238;
    public static final int KEYCODE_TV_SATELLITE_CS                = 239;
    public static final int KEYCODE_TV_SATELLITE_SERVICE           = 240;
    public static final int KEYCODE_TV_NETWORK                     = 241;
    public static final int KEYCODE_TV_ANTENNA_CABLE               = 242;
    public static final int KEYCODE_TV_INPUT_HDMI_1                = 243;
    public static final int KEYCODE_TV_INPUT_HDMI_2                = 244;
    public static final int KEYCODE_TV_INPUT_HDMI_3                = 245;
    public static final int KEYCODE_TV_INPUT_HDMI_4                = 246;
    public static final int KEYCODE_TV_INPUT_COMPOSITE_1           = 247;
    public static final int KEYCODE_TV_INPUT_COMPOSITE_2           = 248;
    public static final int KEYCODE_TV_INPUT_COMPONENT_1           = 249;
    public static final int KEYCODE_TV_INPUT_COMPONENT_2           = 250;
    public static final int KEYCODE_TV_INPUT_VGA_1                 = 251;
    public static final int KEYCODE_TV_AUDIO_DESCRIPTION           = 252;
    public static final int KEYCODE_TV_AUDIO_DESCRIPTION_MIX_UP    = 253;
    public static final int KEYCODE_TV_AUDIO_DESCRIPTION_MIX_DOWN  = 254;
    public static final int KEYCODE_TV_ZOOM_MODE                   = 255;
    public static final int KEYCODE_TV_CONTENTS_MENU               = 256;
    public static final int KEYCODE_TV_MEDIA_CONTEXT_MENU          = 257;
    public static final int KEYCODE_TV_TIMER_PROGRAMMING           = 258;
    public static final int KEYCODE_HELP                           = 259;
    public static final int KEYCODE_NAVIGATE_PREVIOUS              = 260;
    public static final int KEYCODE_NAVIGATE_NEXT                  = 261;
    public static final int KEYCODE_NAVIGATE_IN                    = 262;
    public static final int KEYCODE_NAVIGATE_OUT                   = 263;
    public static final int KEYCODE_STEM_PRIMARY                   = 264;
    public static final int KEYCODE_STEM_1                         = 265;
    public static final int KEYCODE_STEM_2                         = 266;
    public static final int KEYCODE_STEM_3                         = 267;
    public static final int KEYCODE_DPAD_UP_LEFT                   = 268;
    public static final int KEYCODE_DPAD_DOWN_LEFT                 = 269;
    public static final int KEYCODE_DPAD_UP_RIGHT                  = 270;
    public static final int KEYCODE_DPAD_DOWN_RIGHT                = 271;
    public static final int KEYCODE_MEDIA_SKIP_FORWARD             = 272;
    public static final int KEYCODE_MEDIA_SKIP_BACKWARD            = 273;
    public static final int KEYCODE_MEDIA_STEP_FORWARD             = 274;
    public static final int KEYCODE_MEDIA_STEP_BACKWARD            = 275;
    public static final int KEYCODE_SOFT_SLEEP                     = 276;
    public static final int KEYCODE_CUT                            = 277;
    public static final int KEYCODE_COPY                           = 278;
    public static final int KEYCODE_PASTE                          = 279;
    public static final int KEYCODE_SYSTEM_NAVIGATION_UP           = 280;
    public static final int KEYCODE_SYSTEM_NAVIGATION_DOWN         = 281;
    public static final int KEYCODE_SYSTEM_NAVIGATION_LEFT         = 282;
    public static final int KEYCODE_SYSTEM_NAVIGATION_RIGHT        = 283;
    public static final int KEYCODE_ALL_APPS                       = 284;
    public static final int KEYCODE_REFRESH                        = 285;
    public static final int KEYCODE_THUMBS_UP                      = 286;
    public static final int KEYCODE_THUMBS_DOWN                    = 287;
    public static final int KEYCODE_PROFILE_SWITCH                 = 288;

    /** AOSP KeyEvent.java:833 (@TestApi): the numerically last keycode declared.
     *  This -- NOT the deprecated MAX_KEYCODE below -- is what getMaxKeyCode()
     *  returns, and what bounds keyCodeIsValid(). */
    public static final int LAST_KEYCODE = KEYCODE_PROFILE_SWITCH;

    /** @deprecated AOSP KeyEvent.java:895. There have been more than
     *  MAX_KEYCODE keycodes since API 9; kept only because apps compiled long
     *  ago inlined it. Use {@link #getMaxKeyCode()}. */
    @Deprecated
    public static final int MAX_KEYCODE = 84;

    private static final String LABEL_PREFIX = "KEYCODE_";

    // ---- event flags (AOSP KeyEvent.java:1141-1240) -----------------------
    // isCanceled()/isLongPress()/isTracking() are DERIVED from these, so a
    // consumer that re-dispatches a modified event (changeFlags) sees the
    // change. They used to be hardcoded false, which made every such consumer
    // -- View.onKeyLongPress, Activity's BACK handling -- unreachable.
    public static final int FLAG_WOKE_HERE           = 0x1;
    public static final int FLAG_SOFT_KEYBOARD       = 0x2;
    public static final int FLAG_KEEP_TOUCH_MODE     = 0x4;
    public static final int FLAG_FROM_SYSTEM         = 0x8;
    public static final int FLAG_EDITOR_ACTION       = 0x10;
    public static final int FLAG_CANCELED            = 0x20;
    /** AOSP KeyEvent.FLAG_IS_ACCESSIBILITY_EVENT (API 30): the event was
     *  injected by an accessibility service rather than by real hardware. */
    public static final int FLAG_IS_ACCESSIBILITY_EVENT = 0x800;
    public static final int FLAG_VIRTUAL_HARD_KEY    = 0x40;
    public static final int FLAG_LONG_PRESS          = 0x80;
    public static final int FLAG_CANCELED_LONG_PRESS = 0x100;
    public static final int FLAG_TRACKING            = 0x200;
    public static final int FLAG_FALLBACK            = 0x400;
    public static final int FLAG_PREDISPATCH         = 0x20000000;
    public static final int FLAG_START_TRACKING      = 0x40000000;
    public static final int FLAG_TAINTED             = 0x80000000;

    // Meta-state bits (AOSP KeyEvent).
    public static final int META_SHIFT_ON = 0x1;
    public static final int META_ALT_ON   = 0x2;
    public static final int META_SYM_ON   = 0x4;
    public static final int META_FUNCTION_ON = 0x8;
    public static final int META_ALT_LEFT_ON  = 0x10;
    public static final int META_ALT_RIGHT_ON = 0x20;
    public static final int META_SHIFT_LEFT_ON  = 0x40;
    public static final int META_SHIFT_RIGHT_ON = 0x80;
    public static final int META_CTRL_ON  = 0x1000;
    public static final int META_CTRL_LEFT_ON  = 0x2000;
    public static final int META_CTRL_RIGHT_ON = 0x4000;
    public static final int META_META_ON  = 0x10000;
    public static final int META_META_LEFT_ON  = 0x20000;
    public static final int META_META_RIGHT_ON = 0x40000;
    public static final int META_CAPS_LOCK_ON   = 0x100000;
    public static final int META_NUM_LOCK_ON    = 0x200000;
    public static final int META_SCROLL_LOCK_ON = 0x400000;

    // AOSP @hide synthetic meta-state bits (KeyEvent.java itself, not
    // MetaKeyKeyListener -- android/text/method/MetaKeyKeyListener.java in
    // this tree republishes these names with an unrelated wide `long`
    // encoding for its own internal locked-shift tracking, so folding those
    // in here would silently never match on any real int metaState). Used
    // only by normalizeMetaState() below to fold legacy synthetic state into
    // the public META_*_ON bits, per AOSP KeyEvent.java lines 1457-1488.
    private static final int META_CAP_LOCKED = 0x100;
    private static final int META_ALT_LOCKED = 0x200;
    private static final int META_SYM_LOCKED = 0x400;
    private static final int META_SELECTING  = 0x800;

    // Mask of all modifier key meta states (excludes the lock keys).
    private static final int META_MODIFIER_MASK =
            META_SHIFT_ON | META_SHIFT_LEFT_ON | META_SHIFT_RIGHT_ON
            | META_ALT_ON | META_ALT_LEFT_ON | META_ALT_RIGHT_ON
            | META_CTRL_ON | META_CTRL_LEFT_ON | META_CTRL_RIGHT_ON
            | META_META_ON | META_META_LEFT_ON | META_META_RIGHT_ON
            | META_SYM_ON | META_FUNCTION_ON;
    // Mask of all lock key meta states.
    private static final int META_LOCK_MASK =
            META_CAPS_LOCK_ON | META_NUM_LOCK_ON | META_SCROLL_LOCK_ON;
    // Mask of all valid meta states.
    private static final int META_ALL_MASK = META_MODIFIER_MASK | META_LOCK_MASK;
    // Mask of all synthetic meta states reserved for MetaKeyKeyListener compat.
    private static final int META_SYNTHETIC_MASK =
            META_CAP_LOCKED | META_ALT_LOCKED | META_SYM_LOCKED | META_SELECTING;
    // Mask of meta states that are not valid to pass as a `modifiers` argument.
    private static final int META_INVALID_MODIFIER_MASK = META_LOCK_MASK | META_SYNTHETIC_MASK;

    /** AOSP: the bitmask of all modifier meta states (SHIFT/ALT/SYM/FUNCTION/
     *  CTRL/META, including the left/right variants) -- AOSP's own
     *  META_MODIFIER_MASK, which evaluates to 0x000770FF. The AppsFlyer SDK
     *  (com.appsflyer.internal.AFf1mSDK.<clinit>) reads this; without it the
     *  SDK's static init throws NoSuchMethodError on its worker thread. */
    public static int getModifierMetaStateMask() { return META_MODIFIER_MASK; }

    private int action;
    private int code;
    private long eventTime;
    private long downTime;
    private int metaState;
    // AOSP mRepeatCount: "> 0 if this is after the initial down" for a held key,
    // or the event count for ACTION_MULTIPLE. Was hardcoded 0, which made
    // View.dispatchKeyEvent's repeat handling and every engine's key-repeat
    // suppression read the same value for a first press and the 40th repeat.
    private int repeatCount;
    // -1 => derive from keyCode via the KeyCharacterMap; otherwise the literal
    // character the host keyboard produced (already shift/option-resolved).
    private int unicodeChar = -1;
    // Raw hardware scan code. 0 for the synthetic events our host key bridge
    // produces (AOSP KeyEvent.getScanCode() is 0 for virtual/injected events).
    private int scanCode = 0;
    // AOSP InputEvent contract: "<0 for a virtual device". Every AOSP
    // constructor that does not take an explicit deviceId sets exactly this
    // (KeyEvent.java:1370), so an event built from a bare keyCode resolves its
    // characters through the VIRTUAL keyboard map.
    private int deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD;
    private int flags = 0;
    // AOSP leaves mSource at SOURCE_UNKNOWN (0) in every constructor except the
    // 10-arg one, which takes it explicitly (KeyEvent.java:1516). On a real
    // device an app never observes SOURCE_UNKNOWN because InputReader stamps
    // the true source when it builds the event; the injector plays that role
    // here and stamps it with macapkSetSource below.
    private int source = InputDevice.SOURCE_UNKNOWN;
    // AOSP: non-null only for the deprecated ACTION_MULTIPLE character-
    // sequence event flavor. Mac.apk never constructs one.
    private String characters = null;

    /** AOSP KeyEvent.java:1365-1371. Sets ONLY action, keyCode, repeatCount=0
     *  and deviceId=VIRTUAL_KEYBOARD -- everything else keeps its default, so
     *  both timestamps are 0 and the source is SOURCE_UNKNOWN. */
    public KeyEvent(int action, int code) {
        this.action = action;
        this.code = code;
        this.repeatCount = 0;
        this.deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD;
    }
    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat) {
        this.downTime = downTime; this.eventTime = eventTime; this.action = action;
        this.code = code; this.repeatCount = repeat;
    }
    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState) {
        this(downTime, eventTime, action, code, repeat);
        this.metaState = metaState;
    }
    /** AOSP 8-arg form: adds the originating deviceId and raw hardware scancode. */
    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat,
            int metaState, int deviceId, int scancode) {
        this(downTime, eventTime, action, code, repeat, metaState);
        this.deviceId = deviceId;
        this.scanCode = scancode;
    }
    /** AOSP 9-arg form: adds the event's FLAG_* bitmask. */
    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat,
            int metaState, int deviceId, int scancode, int flags) {
        this(downTime, eventTime, action, code, repeat, metaState, deviceId, scancode);
        this.flags = flags;
    }
    /** AOSP 10-arg form: adds the InputDevice.SOURCE_* the event came from. */
    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat,
            int metaState, int deviceId, int scancode, int flags, int source) {
        this(downTime, eventTime, action, code, repeat, metaState, deviceId, scancode, flags);
        this.source = source;
    }
    /** AOSP KeyEvent.java:1610-1625, the character-sequence constructor: the
     *  only public way to build an ACTION_MULTIPLE event, and the only way to
     *  populate `characters` at all. Both timestamps take the single `time`
     *  argument, the key code is KEYCODE_UNKNOWN, and the source is
     *  SOURCE_KEYBOARD (not SOURCE_UNKNOWN like the bare 2-arg form) because
     *  AOSP hardcodes it here.
     *
     *  Added with the real writeToParcel below: without it `characters` was
     *  unreachable from outside this file, so the field could neither be set by
     *  an app nor exercised by debug/parcel_verify.sh -- a marshalled field
     *  with no way to populate it is a field nobody can prove round-trips. */
    public KeyEvent(long time, String characters, int deviceId, int flags) {
        this.downTime = time;
        this.eventTime = time;
        this.characters = characters;
        this.action = ACTION_MULTIPLE;
        this.code = KEYCODE_UNKNOWN;
        this.repeatCount = 0;
        this.deviceId = deviceId;
        this.flags = flags;
        this.source = InputDevice.SOURCE_KEYBOARD;
    }

    /** AOSP copy constructor (KeyEvent.java:1662-1677): an EXACT copy, including
     *  the characters payload. */
    public KeyEvent(KeyEvent origEvent) {
        this.downTime = origEvent.downTime;
        this.eventTime = origEvent.eventTime;
        this.action = origEvent.action;
        this.code = origEvent.code;
        this.repeatCount = origEvent.repeatCount;
        this.metaState = origEvent.metaState;
        this.deviceId = origEvent.deviceId;
        this.source = origEvent.source;
        this.scanCode = origEvent.scanCode;
        this.flags = origEvent.flags;
        this.characters = origEvent.characters;
        this.unicodeChar = origEvent.unicodeChar;
    }
    /** @deprecated AOSP KeyEvent.java:1569 -- use
     *  {@link #changeTimeRepeat(KeyEvent, long, int)}. Copies everything but
     *  substitutes the event time and repeat count. */
    @Deprecated
    public KeyEvent(KeyEvent origEvent, long eventTime, int newRepeat) {
        this(origEvent);
        this.eventTime = eventTime;
        this.repeatCount = newRepeat;
    }
    /** AOSP private copy-with-new-action (KeyEvent.java:1769-1786). Deliberately
     *  does NOT copy `characters`: AOSP's own comment is "one way or the other
     *  we'll lose it when changing the action". */
    private KeyEvent(KeyEvent origEvent, int action) {
        this.downTime = origEvent.downTime;
        this.eventTime = origEvent.eventTime;
        this.action = action;
        this.code = origEvent.code;
        this.repeatCount = origEvent.repeatCount;
        this.metaState = origEvent.metaState;
        this.deviceId = origEvent.deviceId;
        this.source = origEvent.source;
        this.scanCode = origEvent.scanCode;
        this.flags = origEvent.flags;
        this.unicodeChar = origEvent.unicodeChar;
    }

    /** Mac.apk: stamp the literal Unicode character the host keyboard
     *  produced so getUnicodeChar() returns it verbatim (the guest text
     *  layer -- Unity InputField, EditText/InputConnection -- reads this
     *  to insert typed text). This is what a real device's InputReader
     *  achieves by loading the physical keyboard's own .kcm; we already know
     *  the composed character from the NSEvent, so we carry it directly. */
    public void macapkSetUnicodeChar(int c) { this.unicodeChar = c; }
    /** Mac.apk: stamp a raw scan code onto an injected event (host key bridge). */
    public void macapkSetScanCode(int sc) { this.scanCode = sc; }
    /** Mac.apk: stamp the true InputDevice.SOURCE_* onto an injected event.
     *  Needed because AOSP's constructors leave the source at SOURCE_UNKNOWN
     *  and it is the InputReader -- our injector -- that knows the real one.
     *  Without this an engine's isFromSource(SOURCE_KEYBOARD) check fails on
     *  events we synthesize. */
    public void macapkSetSource(int src) { this.source = src; }

    public int  getAction()    { return action; }
    public int  getKeyCode()   { return code; }
    public long getEventTime() { return eventTime; }
    public long getDownTime()  { return downTime; }
    public int  getRepeatCount(){ return repeatCount; }
    /** AOSP KeyEvent.java:2545 -- derived from FLAG_LONG_PRESS, not hardcoded. */
    public final boolean isLongPress() { return (flags & FLAG_LONG_PRESS) != 0; }
    /** AOSP KeyEvent.java:2507 -- derived from FLAG_CANCELED. */
    public final boolean isCanceled()  { return (flags & FLAG_CANCELED) != 0; }
    public int  getMetaState()   { return metaState; }
    public int  getFlags()       { return flags; }
    /** AOSP KeyEvent.getScanCode(): the raw hardware scan code. Was MISSING ->
     *  AGDK's GameActivity.initializeNativeCode caches this method ID during
     *  native init (GameActivityKeyEvent) and threw NoSuchMethodError, aborting
     *  onCreate for every AGDK/GameActivity app (Minecraft). 0 for the synthetic
     *  events we inject, matching AOSP for virtual/injected KeyEvents. */
    public int  getScanCode()    { return scanCode; }

    public interface Callback {
        boolean onKeyDown(int keyCode, KeyEvent event);
        boolean onKeyUp(int keyCode, KeyEvent event);
        boolean onKeyMultiple(int keyCode, int count, KeyEvent event);
        boolean onKeyLongPress(int keyCode, KeyEvent event);
    }

    /** AOSP KeyEvent.DispatcherState (android14 KeyEvent.java :3007-3067),
     *  verbatim. One per window (View.getKeyDispatcherState). It is what turns
     *  a DOWN a receiver chose to track (startTracking) into FLAG_TRACKING on
     *  the matching UP, which Activity/Dialog.onKeyUp(BACK), PopupWindow's
     *  decor and Compose's PopupLayout all require before acting on BACK.
     *  v1.0.1642: was all no-ops, so no dialog or popup could ever be closed
     *  with BACK. */
    public static class DispatcherState {
        int mDownKeyCode;
        Object mDownTarget;
        android.util.SparseIntArray mActiveLongPresses = new android.util.SparseIntArray();

        public void reset() {
            mDownKeyCode = 0;
            mDownTarget = null;
            mActiveLongPresses.clear();
        }

        public void reset(Object target) {
            if (mDownTarget == target) {
                mDownKeyCode = 0;
                mDownTarget = null;
            }
        }

        public void startTracking(KeyEvent event, Object target) {
            if (event.getAction() != ACTION_DOWN) {
                throw new IllegalArgumentException(
                        "Can only start tracking on a down event");
            }
            mDownKeyCode = event.getKeyCode();
            mDownTarget = target;
        }

        public boolean isTracking(KeyEvent event) {
            return mDownKeyCode == event.getKeyCode();
        }

        public void performedLongPress(KeyEvent event) {
            mActiveLongPresses.put(event.getKeyCode(), 1);
        }

        public void handleUpEvent(KeyEvent event) {
            final int keyCode = event.getKeyCode();
            int index = mActiveLongPresses.indexOfKey(keyCode);
            if (index >= 0) {
                event.flags |= FLAG_CANCELED | FLAG_CANCELED_LONG_PRESS;
                mActiveLongPresses.removeAt(index);
            }
            if (mDownKeyCode == keyCode) {
                event.flags |= FLAG_TRACKING;
                mDownKeyCode = 0;
                mDownTarget = null;
            }
        }
    }

    // Per AOSP, ints for source-classification + meta-state.
    @Override public int  getSource()   { return source; }
    @Override public int  getDeviceId() { return deviceId; }
    /** AOSP KeyEvent.getKeyCharacterMap(): the char map for the event's device.
     *  Was MISSING -> any app reading it (e.g. com.android.calculator2 maps
     *  hardware number keys to digits via event.getKeyCharacterMap()) hit
     *  NoSuchMethodError in its onKey handler, so HARDWARE-KEY input was dead for
     *  the whole app. */
    public KeyCharacterMap getKeyCharacterMap() {
        return KeyCharacterMap.load(getDeviceId());
    }
    public int getUnicodeChar() { return getUnicodeChar(metaState); }
    /** AOSP KeyEvent.java:2734-2736 delegates straight to the device's key
     *  character map. The short-circuit above it is Mac.apk's only addition:
     *  when the host NSEvent already told us the composed character (option-
     *  modified keys, dead-key sequences, a non-US layout) that literal beats
     *  any table lookup, because it IS what this keyboard produced. */
    public int getUnicodeChar(int meta) {
        if (unicodeChar >= 0) return unicodeChar;
        return getKeyCharacterMap().get(code, meta);
    }
    /** AOSP KeyEvent.java:2689-2690. */
    public char getDisplayLabel() { return getKeyCharacterMap().getDisplayLabel(code); }
    /** AOSP KeyEvent.java:2803-2804. */
    public char getNumber() { return getKeyCharacterMap().getNumber(code); }
    /** AOSP KeyEvent.java:1252-1254: a STATIC delegate to KeyCharacterMap.
     *  (It was an instance method returning 0 here, which both answers wrongly
     *  and mismatches the invoke-static a real SDK-compiled app emits.) */
    public static int getDeadChar(int accent, int c) {
        return KeyCharacterMap.getDeadChar(accent, c);
    }
    /** AOSP KeyEvent.getKeyData: delegates to the event's device
     *  KeyCharacterMap to fill the caller-supplied KeyData with the key's
     *  meta/number/display-label variants. Real signature is
     *  (KeyCharacterMap.KeyData):boolean -- the old (Object):Object stub had
     *  a different erasure and never linked against real call sites. */
    @Deprecated
    public boolean getKeyData(KeyCharacterMap.KeyData results) {
        return getKeyCharacterMap().getKeyData(code, results);
    }
    /** AOSP KeyEvent.java:2812-2813. */
    public boolean isPrintingKey() { return getKeyCharacterMap().isPrintingKey(code); }

    /** AOSP KeyEvent.getModifiers(): the normalized modifier meta state,
     *  masked to only the valid modifier bits (SHIFT/ALT/CTRL/META/SYM/
     *  FUNCTION including left/right variants; excludes the lock keys). */
    public final int getModifiers() {
        return normalizeMetaState(metaState) & META_MODIFIER_MASK;
    }
    public final boolean isAltPressed()      { return (metaState & META_ALT_ON) != 0; }
    public final boolean isShiftPressed()    { return (metaState & META_SHIFT_ON) != 0; }
    public final boolean isSymPressed()      { return (metaState & META_SYM_ON) != 0; }
    public final boolean isCtrlPressed()     { return (metaState & META_CTRL_ON) != 0; }
    public final boolean isMetaPressed()     { return (metaState & META_META_ON) != 0; }
    public final boolean isFunctionPressed() { return (metaState & META_FUNCTION_ON) != 0; }
    public final boolean isCapsLockOn()      { return (metaState & META_CAPS_LOCK_ON) != 0; }
    public final boolean isNumLockOn()       { return (metaState & META_NUM_LOCK_ON) != 0; }
    public final boolean isScrollLockOn()    { return (metaState & META_SCROLL_LOCK_ON) != 0; }

    /** AOSP KeyEvent.normalizeMetaState(int): if either the left or right bit
     *  of a modifier is set, ORs in the modifier's universal bit too, folds
     *  in the legacy MetaKeyKeyListener synthetic locked-shift states, then
     *  masks to only the valid meta-state bits. */
    public static int normalizeMetaState(int metaState) {
        if ((metaState & (META_SHIFT_LEFT_ON | META_SHIFT_RIGHT_ON)) != 0) {
            metaState |= META_SHIFT_ON;
        }
        if ((metaState & (META_ALT_LEFT_ON | META_ALT_RIGHT_ON)) != 0) {
            metaState |= META_ALT_ON;
        }
        if ((metaState & (META_CTRL_LEFT_ON | META_CTRL_RIGHT_ON)) != 0) {
            metaState |= META_CTRL_ON;
        }
        if ((metaState & (META_META_LEFT_ON | META_META_RIGHT_ON)) != 0) {
            metaState |= META_META_ON;
        }
        if ((metaState & META_CAP_LOCKED) != 0) metaState |= META_CAPS_LOCK_ON;
        if ((metaState & META_ALT_LOCKED) != 0) metaState |= META_ALT_ON;
        if ((metaState & META_SYM_LOCKED) != 0) metaState |= META_SYM_ON;
        return metaState & META_ALL_MASK;
    }
    public static boolean metaStateHasNoModifiers(int metaState) {
        return (normalizeMetaState(metaState) & META_MODIFIER_MASK) == 0;
    }
    /** AOSP KeyEvent.metaStateHasModifiers: true iff exactly the given
     *  modifier bits are pressed (after left/right normalization). Throws
     *  IllegalArgumentException if `modifiers` contains a lock-key or
     *  synthetic bit, or both a combined and a left/right-specific bit for
     *  the same key, matching AOSP's contract. */
    public static boolean metaStateHasModifiers(int metaState, int modifiers) {
        if ((modifiers & META_INVALID_MODIFIER_MASK) != 0) {
            throw new IllegalArgumentException("modifiers must not contain "
                    + "META_CAPS_LOCK_ON, META_NUM_LOCK_ON, META_SCROLL_LOCK_ON, "
                    + "or any synthetic MetaKeyKeyListener state");
        }
        metaState = normalizeMetaState(metaState) & META_MODIFIER_MASK;
        metaState = metaStateFilterDirectionalModifiers(metaState, modifiers,
                META_SHIFT_ON, META_SHIFT_LEFT_ON, META_SHIFT_RIGHT_ON);
        metaState = metaStateFilterDirectionalModifiers(metaState, modifiers,
                META_ALT_ON, META_ALT_LEFT_ON, META_ALT_RIGHT_ON);
        metaState = metaStateFilterDirectionalModifiers(metaState, modifiers,
                META_CTRL_ON, META_CTRL_LEFT_ON, META_CTRL_RIGHT_ON);
        metaState = metaStateFilterDirectionalModifiers(metaState, modifiers,
                META_META_ON, META_META_LEFT_ON, META_META_RIGHT_ON);
        return metaState == modifiers;
    }
    private static int metaStateFilterDirectionalModifiers(int metaState,
            int modifiers, int basic, int left, int right) {
        final boolean wantBasic = (modifiers & basic) != 0;
        final int directional = left | right;
        final boolean wantLeftOrRight = (modifiers & directional) != 0;
        if (wantBasic) {
            if (wantLeftOrRight) {
                throw new IllegalArgumentException("modifiers must not contain both "
                        + "a combined and a left/right-specific bit for the same key "
                        + "(0x" + Integer.toHexString(basic) + " with 0x"
                        + Integer.toHexString(directional) + ")");
            }
            return metaState & ~directional;
        } else if (wantLeftOrRight) {
            return metaState & ~basic;
        }
        return metaState;
    }
    public final boolean hasNoModifiers() { return metaStateHasNoModifiers(metaState); }
    public final boolean hasModifiers(int modifiers) { return metaStateHasModifiers(metaState, modifiers); }

    /** AOSP KeyEvent.isModifierKey(int): true for the physical modifier keys.
     *  Per AOSP's own doc on this method, the lock keys (CAPS_LOCK/
     *  SCROLL_LOCK/NUM_LOCK) are NOT considered modifier keys and return
     *  false. */
    /** AOSP KeyEvent.isConfirmKey (@hide, public static): the keys that
     *  "confirm" a focused view -- what View.onKeyDown/onKeyUp click on. */
    public static final boolean isConfirmKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_DPAD_CENTER:
            case KEYCODE_ENTER:
            case KEYCODE_SPACE:
            case KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return false;
        }
    }
    public static boolean isModifierKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_SHIFT_LEFT:
            case KEYCODE_SHIFT_RIGHT:
            case KEYCODE_ALT_LEFT:
            case KEYCODE_ALT_RIGHT:
            case KEYCODE_CTRL_LEFT:
            case KEYCODE_CTRL_RIGHT:
            case KEYCODE_META_LEFT:
            case KEYCODE_META_RIGHT:
            case KEYCODE_SYM:
            case KEYCODE_NUM:
            case KEYCODE_FUNCTION:
                return true;
            default:
                return false;
        }
    }
    /** AOSP KeyEvent.java:1840-1842. */
    public final boolean isSystem() { return isSystemKey(code); }
    /** AOSP KeyEvent.isSystemKey(int) (KeyEvent.java:1935-1971): keys reserved
     *  for system-level handling, so they cannot be bound as menu shortcuts.
     *  The list is AOSP's verbatim -- the previous curated version omitted
     *  KEYCODE_POWER and the telephony/media/camera/brightness keys, so an app
     *  could bind a menu shortcut to a key the platform reserves. */
    public static final boolean isSystemKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_MENU:
            case KEYCODE_SOFT_RIGHT:
            case KEYCODE_HOME:
            case KEYCODE_BACK:
            case KEYCODE_CALL:
            case KEYCODE_ENDCALL:
            case KEYCODE_VOLUME_UP:
            case KEYCODE_VOLUME_DOWN:
            case KEYCODE_VOLUME_MUTE:
            case KEYCODE_MUTE:
            case KEYCODE_POWER:
            case KEYCODE_HEADSETHOOK:
            case KEYCODE_MEDIA_PLAY:
            case KEYCODE_MEDIA_PAUSE:
            case KEYCODE_MEDIA_PLAY_PAUSE:
            case KEYCODE_MEDIA_STOP:
            case KEYCODE_MEDIA_NEXT:
            case KEYCODE_MEDIA_PREVIOUS:
            case KEYCODE_MEDIA_REWIND:
            case KEYCODE_MEDIA_RECORD:
            case KEYCODE_MEDIA_FAST_FORWARD:
            case KEYCODE_CAMERA:
            case KEYCODE_FOCUS:
            case KEYCODE_SEARCH:
            case KEYCODE_BRIGHTNESS_DOWN:
            case KEYCODE_BRIGHTNESS_UP:
            case KEYCODE_MEDIA_AUDIO_TRACK:
            case KEYCODE_SYSTEM_NAVIGATION_UP:
            case KEYCODE_SYSTEM_NAVIGATION_DOWN:
            case KEYCODE_SYSTEM_NAVIGATION_LEFT:
            case KEYCODE_SYSTEM_NAVIGATION_RIGHT:
                return true;
        }
        return false;
    }
    /** AOSP KeyEvent.java:1976-1986 (@hide). */
    public static final boolean isWakeKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_CAMERA:
            case KEYCODE_MENU:
            case KEYCODE_PAIRING:
            case KEYCODE_STEM_1:
            case KEYCODE_STEM_2:
            case KEYCODE_STEM_3:
            case KEYCODE_WAKEUP:
                return true;
        }
        return false;
    }
    /** AOSP KeyEvent.java:1990-1992 (@hide). */
    public static final boolean isMetaKey(int keyCode) {
        return keyCode == KEYCODE_META_LEFT || keyCode == KEYCODE_META_RIGHT;
    }
    /** AOSP KeyEvent.java:1995-1997 (@hide). */
    public static final boolean isAltKey(int keyCode) {
        return keyCode == KEYCODE_ALT_LEFT || keyCode == KEYCODE_ALT_RIGHT;
    }
    /** AOSP KeyEvent.isGamepadButton(int) (KeyEvent.java:1853-1890). Covers the
     *  named BUTTON_A..MODE block AND the generic BUTTON_1..BUTTON_16 block --
     *  the latter is NOT contiguous with the former (96-110 vs 188-203), which
     *  is why the old single range check answered false for every generic
     *  gamepad button. */
    public static final boolean isGamepadButton(int keyCode) {
        switch (keyCode) {
            case KEYCODE_BUTTON_A:
            case KEYCODE_BUTTON_B:
            case KEYCODE_BUTTON_C:
            case KEYCODE_BUTTON_X:
            case KEYCODE_BUTTON_Y:
            case KEYCODE_BUTTON_Z:
            case KEYCODE_BUTTON_L1:
            case KEYCODE_BUTTON_R1:
            case KEYCODE_BUTTON_L2:
            case KEYCODE_BUTTON_R2:
            case KEYCODE_BUTTON_THUMBL:
            case KEYCODE_BUTTON_THUMBR:
            case KEYCODE_BUTTON_START:
            case KEYCODE_BUTTON_SELECT:
            case KEYCODE_BUTTON_MODE:
            case KEYCODE_BUTTON_1:
            case KEYCODE_BUTTON_2:
            case KEYCODE_BUTTON_3:
            case KEYCODE_BUTTON_4:
            case KEYCODE_BUTTON_5:
            case KEYCODE_BUTTON_6:
            case KEYCODE_BUTTON_7:
            case KEYCODE_BUTTON_8:
            case KEYCODE_BUTTON_9:
            case KEYCODE_BUTTON_10:
            case KEYCODE_BUTTON_11:
            case KEYCODE_BUTTON_12:
            case KEYCODE_BUTTON_13:
            case KEYCODE_BUTTON_14:
            case KEYCODE_BUTTON_15:
            case KEYCODE_BUTTON_16:
                return true;
            default:
                return false;
        }
    }

    /** AOSP KeyEvent.java:1245-1247: returns LAST_KEYCODE. NOT the deprecated
     *  MAX_KEYCODE (84) -- an engine sizes its keycode table from this value,
     *  so answering 84 silently truncates that table at KEYCODE_SEARCH and
     *  drops every letter/gamepad/media key above it. */
    public static int getMaxKeyCode() { return LAST_KEYCODE; }

    /** AOSP KeyEvent.java:3071-3073. */
    private static boolean keyCodeIsValid(int keyCode) {
        return keyCode >= KEYCODE_UNKNOWN && keyCode <= LAST_KEYCODE;
    }

    /** AOSP resolves symbolic key names through a NATIVE table
     *  (nativeKeyCodeToString / nativeKeyCodeFromString, KeyEvent.java:1352-1353)
     *  that is generated from the platform's own keycode list. That list IS this
     *  class's KEYCODE_* field set, so reflecting over it is the faithful
     *  equivalent -- and it cannot drift out of sync the way a hand-written
     *  parallel table would. Built in a holder so it is created on first use
     *  only: an app that never asks for a key name never pays for it.
     *
     *  Relies on the AOSP invariant that keycode VALUES are unique (verified
     *  against android-11.0.0_r48: exactly 0..288, no duplicates), so the
     *  code -> name direction is unambiguous regardless of getDeclaredFields()
     *  ordering, which is unspecified. */
    private static final class NameTable {
        static final String[] TO_NAME = new String[LAST_KEYCODE + 1];
        static final java.util.HashMap<String, Integer> FROM_NAME =
                new java.util.HashMap<String, Integer>(512);
        static {
            for (java.lang.reflect.Field f : KeyEvent.class.getDeclaredFields()) {
                String name = f.getName();
                if (!name.startsWith(LABEL_PREFIX) || f.getType() != int.class) continue;
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                int keyCode;
                try { keyCode = f.getInt(null); } catch (Throwable ignored) { continue; }
                if (keyCode < 0 || keyCode >= TO_NAME.length) continue;
                String bare = name.substring(LABEL_PREFIX.length());
                TO_NAME[keyCode] = bare;
                FROM_NAME.put(bare, Integer.valueOf(keyCode));
            }
        }
    }

    /** AOSP KeyEvent.java:3038-3041: "KEYCODE_" + the symbolic name, or the
     *  decimal keycode when the table has no name for it. */
    public static String keyCodeToString(int keyCode) {
        String symbolicName = (keyCode >= 0 && keyCode < NameTable.TO_NAME.length)
                ? NameTable.TO_NAME[keyCode] : null;
        return symbolicName != null ? LABEL_PREFIX + symbolicName : Integer.toString(keyCode);
    }

    /** AOSP KeyEvent.java:3052-3069: an all-digits string is taken verbatim when
     *  in range; otherwise the "KEYCODE_" prefix is optional (since API 29) and
     *  the bare name is looked up. Anything unrecognized is KEYCODE_UNKNOWN.
     *
     *  Deliberate divergence: AOSP declares the parameter @NonNull and throws
     *  NullPointerException on null. We answer KEYCODE_UNKNOWN instead, because
     *  a guest parsing its own key-binding config would otherwise take a crash
     *  on a malformed line rather than falling back. */
    public static int keyCodeFromString(String symbolicName) {
        if (symbolicName == null) return KEYCODE_UNKNOWN;
        try {
            int keyCode = Integer.parseInt(symbolicName);
            if (keyCodeIsValid(keyCode)) return keyCode;
        } catch (NumberFormatException ignored) {}
        if (symbolicName.startsWith(LABEL_PREFIX)) {
            symbolicName = symbolicName.substring(LABEL_PREFIX.length());
        }
        Integer keyCode = NameTable.FROM_NAME.get(symbolicName);
        if (keyCode != null && keyCodeIsValid(keyCode.intValue())) return keyCode.intValue();
        return KEYCODE_UNKNOWN;
    }

    /** AOSP KeyEvent.java:1794-1796: a copy whose action is replaced. */
    public static KeyEvent changeAction(KeyEvent event, int action) {
        return new KeyEvent(event, action);
    }
    /** AOSP KeyEvent.java:1805-1810: a copy whose flags are replaced wholesale. */
    public static KeyEvent changeFlags(KeyEvent event, int flags) {
        event = new KeyEvent(event);
        event.flags = flags;
        return event;
    }
    /** AOSP KeyEvent.java:1737-1740. */
    public static KeyEvent changeTimeRepeat(KeyEvent event, long eventTime, int newRepeat) {
        return new KeyEvent(event, eventTime, newRepeat);
    }
    /** AOSP KeyEvent.java:1753-1760: as above, and replaces the flags too. */
    public static KeyEvent changeTimeRepeat(KeyEvent event, long eventTime,
            int newRepeat, int newFlags) {
        KeyEvent ret = new KeyEvent(event);
        ret.eventTime = eventTime;
        ret.repeatCount = newRepeat;
        ret.flags = newFlags;
        return ret;
    }

    /** AOSP KeyEvent.getMatch(char[]): first character in `chars` producible
     *  by this key, preferring the unmodified variant. */
    public final char getMatch(char[] chars) { return getMatch(chars, 0); }
    /** AOSP KeyEvent.getMatch(char[], int): as above, preferring the variant
     *  produced under the given meta state. */
    public final char getMatch(char[] chars, int metaState) {
        return getKeyCharacterMap().getMatch(code, chars, metaState);
    }
    /** @deprecated AOSP: no longer used by the input system; historically the
     *  string payload of a deprecated ACTION_MULTIPLE event. Mac.apk never
     *  constructs one, so this is always null, matching AOSP's own doc
     *  ("In all other cases it is null"). */
    @Deprecated
    public final String getCharacters() { return characters; }

    /** AOSP KeyEvent.dispatch (android14 KeyEvent.java :2921-2975): route this
     *  event to a Callback by action, and keep the window's DispatcherState: a
     *  consumed DOWN that called startTracking() starts tracking, and the UP
     *  gets FLAG_TRACKING from handleUpEvent. v1.0.1642: `state` used to be
     *  ignored, so isTracking() could never become true. */
    public final boolean dispatch(Callback receiver, DispatcherState state, Object target) {
        if (receiver == null) return false;
        switch (getAction()) {
            case ACTION_DOWN: {
                flags &= ~FLAG_START_TRACKING;
                boolean res = receiver.onKeyDown(getKeyCode(), this);
                if (state != null) {
                    if (res && getRepeatCount() == 0 && (flags & FLAG_START_TRACKING) != 0) {
                        state.startTracking(this, target);
                    } else if (isLongPress() && state.isTracking(this)) {
                        try {
                            if (receiver.onKeyLongPress(getKeyCode(), this)) {
                                state.performedLongPress(this);
                                res = true;
                            }
                        } catch (AbstractMethodError e) {
                        }
                    }
                }
                return res;
            }
            case ACTION_UP:
                if (state != null) state.handleUpEvent(this);
                return receiver.onKeyUp(getKeyCode(), this);
            case ACTION_MULTIPLE: return receiver.onKeyMultiple(getKeyCode(), getRepeatCount(), this);
            default:              return false;
        }
    }

    /** AOSP KeyEvent.isTracking (API 5): whether FLAG_TRACKING is set. Note
     *  that FLAG_TRACKING (0x200) is set by the SYSTEM in response to
     *  startTracking(), which sets the distinct FLAG_START_TRACKING -- so this
     *  stays false for the events Mac.apk injects, as it does on AOSP for an
     *  event the framework has not re-dispatched. */
    public final boolean isTracking() { return (flags & FLAG_TRACKING) != 0; }
    /** AOSP KeyEvent.java:2565-2567. */
    public final void startTracking() { flags |= FLAG_START_TRACKING; }

    /** AOSP InputEvent.isTainted / setTainted (@hide). */
    public final boolean isTainted() { return (flags & FLAG_TAINTED) != 0; }
    public final void setTainted(boolean tainted) {
        flags = tainted ? (flags | FLAG_TAINTED) : (flags & ~FLAG_TAINTED);
    }

    @Override public String toString() {
        return "KeyEvent { action=" + action + ", keyCode=" + keyCodeToString(code)
                + ", scanCode=" + scanCode + ", metaState=0x" + Integer.toHexString(metaState)
                + ", flags=0x" + Integer.toHexString(flags) + ", repeatCount=" + repeatCount
                + ", eventTime=" + eventTime + ", downTime=" + downTime
                + ", deviceId=" + deviceId + ", source=0x" + Integer.toHexString(source) + " }";
    }

    // ---------------------------------------------------------------------
    // Parcelable. Mirrors frameworks/base/core/java/android/view/KeyEvent.java
    // :3130-3165 (android11-release) -- the private KeyEvent(Parcel) ctor and
    // writeToParcel -- in AOSP's field ORDER, restricted to the fields this
    // tree declares.
    //
    // ⚠ Until v1.0.940 this was an empty body plus a CREATOR that answered
    // `new KeyEvent(0, 0)`, i.e. an ACTION_DOWN of KEYCODE_UNKNOWN. That was
    // invisible while android.os.Parcel faked marshalling with a side list of
    // live objects; now that it marshals for real, an app that puts a KeyEvent
    // in a Bundle (IME edit sessions and remote-control/media-button handlers
    // both do) would get a blank event back with no error.
    //
    // OMITTED, because this tree has no field for them (each is read back as
    // its constructor default, never as garbage, since the writer omits the
    // slot too):
    //   mId       -- AOSP's per-event id for input tracing.
    //   mDisplayId-- multi-display routing; Mac.apk is single-display.
    //   mHmac     -- the input-verification MAC, produced by the system's
    //                InputDispatcher. There is no InputDispatcher here to
    //                produce or check one.
    // ADDED at the end: unicodeChar, which AOSP does not store (it derives the
    // character from the KeyCharacterMap) but this tree does, because the host
    // key bridge hands us an already shift/option-resolved character. It is
    // appended AFTER the AOSP-ordered fields so the shared prefix keeps AOSP's
    // sequence; dropping it would lose the resolved character on every round
    // trip, which is exactly the silent loss this change exists to remove.
    //
    // The leading token is AOSP's PARCEL_TOKEN_KEY_EVENT (1). It exists so a
    // reader holding only an InputEvent can tell a KeyEvent from a MotionEvent
    // (PARCEL_TOKEN_MOTION_EVENT is 2) before choosing a creator. InputEvent on
    // this tree has no CREATOR yet, so nothing consumes it today -- it is
    // written anyway because it costs 4 bytes and makes an InputEvent.CREATOR
    // a pure addition later instead of a format break.
    private static final int PARCEL_TOKEN_KEY_EVENT = 1;

    @Override public int describeContents() { return 0; }

    /** Parameter named parcelableFlags, not AOSP's `flags`: this class has its
     *  own `flags` field (the FLAG_* event bitmask) and shadowing it here would
     *  hide which of the two is being written. */
    @Override public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeInt(PARCEL_TOKEN_KEY_EVENT);

        dest.writeInt(deviceId);
        dest.writeInt(source);
        dest.writeInt(action);
        dest.writeInt(code);
        dest.writeInt(repeatCount);
        dest.writeInt(metaState);
        dest.writeInt(scanCode);
        dest.writeInt(this.flags);
        dest.writeLong(downTime);
        dest.writeLong(eventTime);
        dest.writeString(characters);
        dest.writeInt(unicodeChar);
    }

    /** AOSP's `private KeyEvent(Parcel in)`. The token has already been
     *  consumed by the CREATOR, matching AOSP's split. */
    private KeyEvent(Parcel in) {
        deviceId    = in.readInt();
        source      = in.readInt();
        action      = in.readInt();
        code        = in.readInt();
        repeatCount = in.readInt();
        metaState   = in.readInt();
        scanCode    = in.readInt();
        flags       = in.readInt();
        downTime    = in.readLong();
        eventTime   = in.readLong();
        characters  = in.readString();
        unicodeChar = in.readInt();
    }

    public static final Creator<KeyEvent> CREATOR = new Creator<KeyEvent>() {
        @Override public KeyEvent createFromParcel(Parcel source) {
            source.readInt();   // PARCEL_TOKEN_KEY_EVENT; we already know the type
            return new KeyEvent(source);
        }
        @Override public KeyEvent[] newArray(int size) { return new KeyEvent[size]; }
    };

    /** AOSP KeyEvent.dispatch(Callback):3441 -- the untracked form,
     *  `return dispatch(receiver, null, null)`. */
    public final boolean dispatch(Callback receiver) {
        return dispatch(receiver, null, null);
    }

    /** AOSP KeyEvent.setSource(int):2633 `mSource = source`. Used by an app
     *  that re-sources an event before re-injecting it (a remote-control or
     *  gamepad shim). */
    public final void setSource(int source) { this.source = source; }

    /** AOSP KeyEvent.isMediaSessionKey(int):2503 -- is this a key a
     *  MediaSession should get first? Exact key list transcribed. */
    public static final boolean isMediaSessionKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_MEDIA_PLAY:
            case KEYCODE_MEDIA_PAUSE:
            case KEYCODE_MEDIA_PLAY_PAUSE:
            case KEYCODE_HEADSETHOOK:
            case KEYCODE_MEDIA_STOP:
            case KEYCODE_MEDIA_NEXT:
            case KEYCODE_MEDIA_PREVIOUS:
            case KEYCODE_MEDIA_REWIND:
            case KEYCODE_MEDIA_RECORD:
            case KEYCODE_MEDIA_FAST_FORWARD:
                return true;
        }
        return false;
    }
}
