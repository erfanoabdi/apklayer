// android.view.KeyCharacterMap -- maps key codes to characters for a keyboard
// device. Every KeyEvent built from a bare keyCode resolves its characters
// through here (the 2-arg KeyEvent constructor sets deviceId =
// VIRTUAL_KEYBOARD, AOSP KeyEvent.java:1370), so getUnicodeChar, getNumber,
// getDisplayLabel, getMatch and isPrintingKey all live or die together on this
// class. Unity's native input layer calls KeyCharacterMap.load(deviceId) then
// kcm.get(keyCode, metaState) directly.
//
// ---- WHERE THE DATA COMES FROM, AND WHY IT IS SHAPED LIKE THIS ----
//
// In AOSP this class is a thin JNI veneer: the real map is parsed from a .kcm
// text file by frameworks/native/libs/input/KeyCharacterMap.cpp, and
// KeyCharacterMap.load(VIRTUAL_KEYBOARD) loads
// frameworks/base/data/keyboards/Virtual.kcm. We cannot mirror an
// implementation that is "read a file we do not ship", so instead this class
// mirrors the OBSERVABLE behaviour by transcribing Virtual.kcm itself
// (android-11.0.0_r48) into the table below and porting the four native
// routines that interpret it:
//
//   matchesMetaState  <- KeyCharacterMap.cpp:430-459
//   getKeyBehavior    <- KeyCharacterMap.cpp:413-428
//   getMatch          <- KeyCharacterMap.cpp:268-297
//   the `number` derivation in Parser::finishKey <- KeyCharacterMap.cpp:1118-1137
//
// Deriving the answers ad-hoc instead (a charForKeyCode(code, shift) helper)
// was the previous approach and it got getDisplayLabel, getNumber,
// isPrintingKey, getMatch and the caps-lock case all wrong at once, because
// each of those is a DIFFERENT projection of the key's behavior list, not a
// variation on "shifted or not":
//   - the display label is its own `label:` property, NOT the shifted form
//     (COMMA's label is ',' while its shift is '<');
//   - `number` is derived by a digit/symbol scan over every behavior;
//   - isPrintingKey classifies the LABEL by Unicode category, so SPACE and
//     ENTER are NOT printing keys even though they produce characters;
//   - getMatch scans every behavior, so a shift-only character matches even
//     when the query meta state is 0.
//
// KNOWN, DELIBERATE GAP: Virtual.kcm's `alt:` layer (10 keys: C E I N S U 6
// SPACE GRAVE ESCAPE, mostly dead-key accents) is NOT transcribed, and of the
// `fallback` mechanism only the "### Gamepad buttons ###" block is (see
// getFallbackAction, 2026-10-02) -- the keyboard fallbacks (numpad keys,
// Ctrl/Alt+ESCAPE and friends) are not. The alt layer is unreachable on this host: our
// key bridge already knows the composed character from the NSEvent and stamps
// it onto the event (KeyEvent.macapkSetUnicodeChar), which short-circuits the
// lookup. Consequently this map produces no combining accents, which is why
// get() needs no combining-to-accent reverse mapping (AOSP does that at
// KeyCharacterMap.java:370-375).
package android.view;

public class KeyCharacterMap implements android.os.Parcelable {
    // Keyboard types.
    public static final int NUMERIC = 1;
    public static final int PREDICTIVE = 2;
    public static final int ALPHA = 3;
    public static final int FULL = 4;
    public static final int SPECIAL_FUNCTION = 5;

    // Device ids.
    public static final int BUILT_IN_KEYBOARD = 0;
    public static final int VIRTUAL_KEYBOARD = -1;

    // Modifier behavior.
    public static final int MODIFIER_BEHAVIOR_CHORDED = 0;
    public static final int MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED = 1;

    // Combining-accent sentinel bits.
    public static final int COMBINING_ACCENT = 0x80000000;
    public static final int COMBINING_ACCENT_MASK = 0x7FFFFFFF;

    // Printable placeholder characters for the dead keys, verbatim from AOSP
    // frameworks/base core/java/android/view/KeyCharacterMap.java:156-188.
    // These are what a caller passes as getDeadChar()'s `accent` argument
    // (the value get() returns masked with COMBINING_ACCENT_MASK).
    private static final int ACCENT_ACUTE = '\u00B4';
    private static final int ACCENT_BREVE = '\u02D8';
    private static final int ACCENT_CARON = '\u02C7';
    private static final int ACCENT_CEDILLA = '\u00B8';
    private static final int ACCENT_CIRCUMFLEX = '\u02C6';
    private static final int ACCENT_COMMA_ABOVE = '\u1FBD';
    private static final int ACCENT_COMMA_ABOVE_RIGHT = '\u02BC';
    private static final int ACCENT_DOT_ABOVE = '\u02D9';
    private static final int ACCENT_DOT_BELOW = '.';    // approximate
    private static final int ACCENT_DOUBLE_ACUTE = '\u02DD';
    private static final int ACCENT_GRAVE = '\u02CB';
    private static final int ACCENT_HOOK_ABOVE = '\u02C0';
    private static final int ACCENT_HORN = '\'';        // approximate
    private static final int ACCENT_MACRON = '\u00AF';
    private static final int ACCENT_MACRON_BELOW = '\u02CD';
    private static final int ACCENT_OGONEK = '\u02DB';
    private static final int ACCENT_REVERSED_COMMA_ABOVE = '\u02BD';
    private static final int ACCENT_RING_ABOVE = '\u02DA';
    private static final int ACCENT_STROKE = '-';       // approximate
    private static final int ACCENT_TILDE = '\u02DC';
    private static final int ACCENT_TURNED_COMMA_ABOVE = '\u02BB';
    private static final int ACCENT_UMLAUT = '\u00A8';
    private static final int ACCENT_VERTICAL_LINE_ABOVE = '\u02C8';
    private static final int ACCENT_VERTICAL_LINE_BELOW = '\u02CC';
    private static final int ACCENT_APOSTROPHE = '\'';
    private static final int ACCENT_QUOTATION_MARK = '"';

    // Legacy display characters older apps still pass in; AOSP keeps accepting
    // them by mapping each to the same combining mark as its modern form.
    private static final int ACCENT_GRAVE_LEGACY = '`';
    private static final int ACCENT_CIRCUMFLEX_LEGACY = '^';
    private static final int ACCENT_TILDE_LEGACY = '~';

    private static final int CHAR_SPACE = ' ';

    /** Printable dead-key accent -> the Unicode combining diacritical that
     *  composes with a base character. AOSP builds this alongside the reverse
     *  map (sCombiningToAccent, used only by the dead-key DISPLAY path in
     *  get(), which this map has no dead keys to feed -- see the class note on
     *  the untranscribed alt layer), so only the forward direction is kept. */
    private static final android.util.SparseIntArray sAccentToCombining =
            new android.util.SparseIntArray();
    static {
        sAccentToCombining.append(ACCENT_GRAVE, '\u0300');
        sAccentToCombining.append(ACCENT_ACUTE, '\u0301');
        sAccentToCombining.append(ACCENT_CIRCUMFLEX, '\u0302');
        sAccentToCombining.append(ACCENT_TILDE, '\u0303');
        sAccentToCombining.append(ACCENT_MACRON, '\u0304');
        sAccentToCombining.append(ACCENT_BREVE, '\u0306');
        sAccentToCombining.append(ACCENT_DOT_ABOVE, '\u0307');
        sAccentToCombining.append(ACCENT_UMLAUT, '\u0308');
        sAccentToCombining.append(ACCENT_HOOK_ABOVE, '\u0309');
        sAccentToCombining.append(ACCENT_RING_ABOVE, '\u030A');
        sAccentToCombining.append(ACCENT_DOUBLE_ACUTE, '\u030B');
        sAccentToCombining.append(ACCENT_CARON, '\u030C');
        sAccentToCombining.append(ACCENT_TURNED_COMMA_ABOVE, '\u0312');
        sAccentToCombining.append(ACCENT_COMMA_ABOVE, '\u0313');
        sAccentToCombining.append(ACCENT_REVERSED_COMMA_ABOVE, '\u0314');
        sAccentToCombining.append(ACCENT_COMMA_ABOVE_RIGHT, '\u0315');
        sAccentToCombining.append(ACCENT_HORN, '\u031B');
        sAccentToCombining.append(ACCENT_DOT_BELOW, '\u0323');
        sAccentToCombining.append(ACCENT_CEDILLA, '\u0327');
        sAccentToCombining.append(ACCENT_OGONEK, '\u0328');
        sAccentToCombining.append(ACCENT_VERTICAL_LINE_BELOW, '\u0329');
        sAccentToCombining.append(ACCENT_MACRON_BELOW, '\u0331');
        sAccentToCombining.append(ACCENT_STROKE, '\u0335');

        // One-way legacy mappings, kept so older apps keep working.
        sAccentToCombining.append(ACCENT_GRAVE_LEGACY, '\u0300');
        sAccentToCombining.append(ACCENT_CIRCUMFLEX_LEGACY, '\u0302');
        sAccentToCombining.append(ACCENT_TILDE_LEGACY, '\u0303');

        // One-way mappings onto the preferred accent.
        sAccentToCombining.append(ACCENT_APOSTROPHE, '\u0301');
        sAccentToCombining.append(ACCENT_QUOTATION_MARK, '\u0308');
    }

    /** Memo of (combining << 16 | base) -> composed character. AOSP seeds it
     *  with the stroke-modifier pairs that have NO Unicode NFC composition
     *  (a Finnish-multilingual-keyboard requirement) and fills the rest lazily
     *  from the NFC tables. */
    private static final android.util.SparseIntArray sDeadKeyCache =
            new android.util.SparseIntArray();
    static {
        addDeadKey(ACCENT_STROKE, 'D', '\u0110');
        addDeadKey(ACCENT_STROKE, 'G', '\u01E4');
        addDeadKey(ACCENT_STROKE, 'H', '\u0126');
        addDeadKey(ACCENT_STROKE, 'I', '\u0197');
        addDeadKey(ACCENT_STROKE, 'L', '\u0141');
        addDeadKey(ACCENT_STROKE, 'O', '\u00D8');
        addDeadKey(ACCENT_STROKE, 'T', '\u0166');
        addDeadKey(ACCENT_STROKE, 'd', '\u0111');
        addDeadKey(ACCENT_STROKE, 'g', '\u01E5');
        addDeadKey(ACCENT_STROKE, 'h', '\u0127');
        addDeadKey(ACCENT_STROKE, 'i', '\u0268');
        addDeadKey(ACCENT_STROKE, 'l', '\u0142');
        addDeadKey(ACCENT_STROKE, 'o', '\u00F8');
        addDeadKey(ACCENT_STROKE, 't', '\u0167');
    }

    private static void addDeadKey(int accent, int c, int result) {
        final int combining = sAccentToCombining.get(accent);
        if (combining == 0) {
            throw new IllegalStateException("Invalid dead key declaration.");
        }
        sDeadKeyCache.put((combining << 16) | c, result);
    }

    // Special input characters (AOSP private-use-area values).
    public static final char HEX_INPUT = '\uEF00';
    public static final char PICKER_DIALOG_INPUT = '\uEF01';

    // ---- the key table ---------------------------------------------------

    /** One `metaState -> character` mapping of a key, mirroring
     *  KeyCharacterMap::Behavior (KeyCharacterMap.cpp:684-694). A .kcm line
     *  such as `shift, capslock: 'A'` declares TWO behaviors sharing one
     *  character. */
    private static final class Behavior {
        final int metaState;
        final char character;
        Behavior(int metaState, char character) {
            this.metaState = metaState;
            this.character = character;
        }
    }

    /** One key. `behaviors` is in AOSP's list order -- most specific first,
     *  the unmodified `base` behavior LAST -- because getKeyBehavior returns
     *  the FIRST match and getMatch's own comment relies on base being last
     *  ("the base key behavior will usually be last in the list"). */
    private static final class Key {
        final char label;
        final char number;
        final Behavior[] behaviors;
        Key(char label, Behavior[] behaviors) {
            this.label = label;
            this.behaviors = behaviors;
            this.number = deriveNumber(behaviors);
        }
    }

    /** AOSP KeyCharacterMap.cpp:1118-1137 (Parser::finishKey). No .kcm in the
     *  virtual map declares an explicit `number:`, so every answer getNumber()
     *  gives is derived here: scan the behaviors, remember any digit and any
     *  member of a fixed punctuation set, and let a digit win. This is why
     *  getNumber(KEYCODE_COMMA) is ',' and getNumber(KEYCODE_A) is 0. */
    private static char deriveNumber(Behavior[] behaviors) {
        char digit = 0;
        char symbol = 0;
        for (Behavior b : behaviors) {
            char ch = b.character;
            if (ch == 0) continue;
            if (ch >= '0' && ch <= '9') {
                digit = ch;
            } else if (ch == '(' || ch == ')' || ch == '#' || ch == '*'
                    || ch == '-' || ch == '+' || ch == ',' || ch == '.'
                    || ch == '\'' || ch == ':' || ch == ';' || ch == '/') {
                symbol = ch;
            }
        }
        return digit != 0 ? digit : symbol;
    }

    private static final android.util.SparseArray<Key> sKeys =
            new android.util.SparseArray<Key>();

    private static void define(int keyCode, char label, Behavior... behaviors) {
        sKeys.put(keyCode, new Key(label, behaviors));
    }
    /** `label: 'A'  base: 'a'  shift, capslock: 'A'` */
    private static void letter(int keyCode, char upper, char lower) {
        define(keyCode, upper,
                new Behavior(KeyEvent.META_CAPS_LOCK_ON, upper),
                new Behavior(KeyEvent.META_SHIFT_ON, upper),
                new Behavior(0, lower));
    }
    /** `label: c  base: c  shift: shifted` -- the digit row and the
     *  punctuation keys. Note there is NO capslock behavior, which is why caps
     *  lock does not shift a digit. */
    private static void shifted(int keyCode, char base, char shift) {
        define(keyCode, base,
                new Behavior(KeyEvent.META_SHIFT_ON, shift),
                new Behavior(0, base));
    }
    /** `label: c  base: c` -- a key with no modified variant. */
    private static void plain(int keyCode, char c) {
        define(keyCode, c, new Behavior(0, c));
    }
    /** `label: c  base: fallback ...  numlock: c` -- the numeric keypad keys
     *  that act as navigation until num lock is on. The base behavior carries
     *  only a fallback keycode and no character, so it contributes nothing. */
    private static void numlock(int keyCode, char c) {
        define(keyCode, c, new Behavior(KeyEvent.META_NUM_LOCK_ON, c));
    }

    static {
        // ---- transcribed from AOSP frameworks/base/data/keyboards/Virtual.kcm
        // (android-11.0.0_r48). Keys that Virtual.kcm declares with neither a
        // label nor a character (ESCAPE, DEL, BUTTON_*) are omitted: they are
        // observationally identical to an absent key, since every getter would
        // read 0 from them.
        final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < 26; i++) {
            letter(KeyEvent.KEYCODE_A + i, upper.charAt(i), (char) ('a' + i));
        }
        final String shiftedDigits = ")!@#$%^&*(";   // US-QWERTY shift of 0..9
        for (int i = 0; i < 10; i++) {
            shifted(KeyEvent.KEYCODE_0 + i, (char) ('0' + i), shiftedDigits.charAt(i));
        }
        plain(KeyEvent.KEYCODE_SPACE, ' ');
        plain(KeyEvent.KEYCODE_ENTER, '\n');
        plain(KeyEvent.KEYCODE_TAB, '\t');
        shifted(KeyEvent.KEYCODE_COMMA, ',', '<');
        shifted(KeyEvent.KEYCODE_PERIOD, '.', '>');
        shifted(KeyEvent.KEYCODE_SLASH, '/', '?');
        shifted(KeyEvent.KEYCODE_GRAVE, '`', '~');
        shifted(KeyEvent.KEYCODE_MINUS, '-', '_');
        shifted(KeyEvent.KEYCODE_EQUALS, '=', '+');
        shifted(KeyEvent.KEYCODE_LEFT_BRACKET, '[', '{');
        shifted(KeyEvent.KEYCODE_RIGHT_BRACKET, ']', '}');
        shifted(KeyEvent.KEYCODE_BACKSLASH, '\\', '|');
        shifted(KeyEvent.KEYCODE_SEMICOLON, ';', ':');
        shifted(KeyEvent.KEYCODE_APOSTROPHE, '\'', '"');
        for (int i = 0; i < 10; i++) {
            numlock(KeyEvent.KEYCODE_NUMPAD_0 + i, (char) ('0' + i));
        }
        numlock(KeyEvent.KEYCODE_NUMPAD_DOT, '.');
        plain(KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN, '(');
        plain(KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN, ')');
        plain(KeyEvent.KEYCODE_NUMPAD_DIVIDE, '/');
        plain(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, '*');
        plain(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, '-');
        plain(KeyEvent.KEYCODE_NUMPAD_ADD, '+');
        plain(KeyEvent.KEYCODE_NUMPAD_COMMA, ',');
        plain(KeyEvent.KEYCODE_NUMPAD_EQUALS, '=');
        plain(KeyEvent.KEYCODE_NUMPAD_ENTER, '\n');
        plain(KeyEvent.KEYCODE_AT, '@');
        plain(KeyEvent.KEYCODE_STAR, '*');
        plain(KeyEvent.KEYCODE_POUND, '#');
        plain(KeyEvent.KEYCODE_PLUS, '+');
    }

    /** AOSP's native KeyboardType enum starts at KEYBOARD_TYPE_UNKNOWN = 0
     *  (frameworks/native/include/input/KeyCharacterMap.h). The Java class
     *  publishes only NUMERIC..SPECIAL_FUNCTION, so 0 has no public name -- but
     *  it is exactly what nativeGetKeyboardType returns for a device that has
     *  no key map at all, which is the case for a touchscreen. */
    private static final int KEYBOARD_TYPE_UNKNOWN = 0;

    private final int mDeviceId;
    /** Lazily resolved keyboard type; -1 means "not asked yet". The lazy write
     *  is a benign race: the computation is idempotent and an int store is
     *  atomic, so two threads racing agree on the result. */
    private int mKeyboardType = -1;

    private KeyCharacterMap(int deviceId) { mDeviceId = deviceId; }
    public KeyCharacterMap() { mDeviceId = VIRTUAL_KEYBOARD; }

    /** AOSP factory. Returns the char map for the given device. Mac.apk has a
     *  single host keyboard whose layout is the same US-QWERTY that Virtual.kcm
     *  describes, so every device id gets that one map rather than a per-device
     *  file. MUST be non-null and non-throwing -- Unity calls this
     *  unconditionally while handling a key event. */
    public static KeyCharacterMap load(int deviceId) {
        return new KeyCharacterMap(deviceId);
    }

    // 2026-08-23 conformance sweep: AOSP's writeToParcel marshals a native
    // pointer to the C++-parsed .kcm map (KeyCharacterMap.java L299-340,
    // `nativeWriteToParcel(mPtr, out)`) -- a format we cannot reproduce
    // because this class is a Java transcription with no native peer, not a
    // JNI veneer over one. The only REAL per-instance state this class
    // carries is mDeviceId (mKeyboardType is a lazily-derived cache, not
    // identity); every device id currently resolves to the same transcribed
    // Virtual.kcm table regardless (see load()'s comment), so writing
    // mDeviceId and reconstructing via load() on read is a complete,
    // non-lossy round trip of everything this class actually stores.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeInt(mDeviceId);
    }

    public static final android.os.Parcelable.Creator<KeyCharacterMap> CREATOR =
            new android.os.Parcelable.Creator<KeyCharacterMap>() {
        @Override public KeyCharacterMap createFromParcel(android.os.Parcel in) {
            return KeyCharacterMap.load(in.readInt());
        }
        @Override public KeyCharacterMap[] newArray(int size) { return new KeyCharacterMap[size]; }
    };

    /** AOSP KeyCharacterMap.cpp:430-459 (matchesMetaState). A behavior applies
     *  when the event carries at least the behavior's own meta bits AND, for
     *  CTRL/ALT/META specifically, carries no EXTRA ones -- so ctrl+A does not
     *  silently fall through to plain A. SHIFT and the lock bits are
     *  deliberately absent from EXACT_META_STATES, which is why shift+capslock
     *  still resolves to the capslock behavior. */
    private static boolean matchesMetaState(int eventMetaState, int behaviorMetaState) {
        if ((eventMetaState & behaviorMetaState) != behaviorMetaState) return false;
        final int EXACT_META_STATES =
                KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_RIGHT_ON
                | KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_RIGHT_ON
                | KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON;
        int unmatchedMetaState = eventMetaState & ~behaviorMetaState & EXACT_META_STATES;
        if ((behaviorMetaState & KeyEvent.META_CTRL_ON) != 0) {
            unmatchedMetaState &= ~(KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_RIGHT_ON);
        } else if ((behaviorMetaState & (KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_RIGHT_ON)) != 0) {
            unmatchedMetaState &= ~KeyEvent.META_CTRL_ON;
        }
        if ((behaviorMetaState & KeyEvent.META_ALT_ON) != 0) {
            unmatchedMetaState &= ~(KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_RIGHT_ON);
        } else if ((behaviorMetaState & (KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_RIGHT_ON)) != 0) {
            unmatchedMetaState &= ~KeyEvent.META_ALT_ON;
        }
        if ((behaviorMetaState & KeyEvent.META_META_ON) != 0) {
            unmatchedMetaState &= ~(KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON);
        } else if ((behaviorMetaState & (KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON)) != 0) {
            unmatchedMetaState &= ~KeyEvent.META_META_ON;
        }
        return unmatchedMetaState == 0;
    }

    /** AOSP KeyCharacterMap.cpp:413-428 + 231-241 (getKeyBehavior/getCharacter):
     *  the first behavior whose meta state matches wins. */
    private static char getCharacter(int keyCode, int metaState) {
        Key key = sKeys.get(keyCode);
        if (key == null) return 0;
        for (Behavior b : key.behaviors) {
            if (matchesMetaState(metaState, b.metaState)) return b.character;
        }
        return 0;
    }

    /** Resolve the Unicode character produced by keyCode under metaState.
     *  Returns 0 for non-printing keys (AOSP semantics). AOSP normalizes the
     *  meta state first (KeyCharacterMap.java:368) so a caller passing only
     *  META_SHIFT_LEFT_ON still gets the shifted character. */
    public int get(int keyCode, int metaState) {
        return getCharacter(keyCode, KeyEvent.normalizeMetaState(metaState));
    }

    /** AOSP KeyCharacterMap.cpp:207-217: the key's own `label:` property. This
     *  is NOT the shifted character -- COMMA's label is ',' and its shift is
     *  '<' -- and it is what isPrintingKey classifies. */
    public char getDisplayLabel(int keyCode) {
        Key key = sKeys.get(keyCode);
        return key == null ? 0 : key.label;
    }

    /** AOSP KeyCharacterMap.cpp:219-228, over the derived `number` property. */
    public char getNumber(int keyCode) {
        Key key = sKeys.get(keyCode);
        return key == null ? 0 : key.number;
    }

    public char getMatch(int keyCode, char[] chars) { return getMatch(keyCode, chars, 0); }
    /** AOSP KeyCharacterMap.cpp:268-297. Scans EVERY behavior of the key, not
     *  just the one the meta state selects: any behavior whose character is in
     *  `chars` becomes the candidate answer, and the scan stops early only on a
     *  behavior whose meta requirement the query already satisfies. That is why
     *  getMatch(KEYCODE_A, {'A','b'}, 0) is 'A' -- the shift behavior produces
     *  it even though no shift was asked for. */
    public char getMatch(int keyCode, char[] chars, int metaState) {
        if (chars == null) {
            throw new IllegalArgumentException("chars must not be null.");
        }
        metaState = KeyEvent.normalizeMetaState(metaState);
        Key key = sKeys.get(keyCode);
        if (key == null) return 0;
        char result = 0;
        for (Behavior b : key.behaviors) {
            if (b.character == 0) continue;
            for (char want : chars) {
                if (want == b.character) {
                    result = b.character;
                    if ((b.metaState & metaState) == b.metaState) return result;
                    break;
                }
            }
        }
        return result;
    }

    /** Guards the load()-time cycle. android.hardware.input.InputManager builds
     *  its device table BY CALLING KeyCharacterMap.load() (InputManager.java:138
     *  and :155), so a query from here back into the registry while that table
     *  is still under construction would recurse forever. While the flag is set
     *  we answer as the keyboard, which is what AOSP's own load() falls back to
     *  for a device it cannot resolve (KeyCharacterMap.java:337-347). */
    private static final ThreadLocal<Boolean> sResolvingType = new ThreadLocal<Boolean>();

    /** AOSP answers the keyboard type PER DEVICE: it is a property of that
     *  device's own key map, so a touchscreen -- which has no key map at all --
     *  answers KEYBOARD_TYPE_UNKNOWN (0), NOT FULL. Answering FULL
     *  unconditionally told every app that our touchscreen was a full hardware
     *  keyboard, which is what the IDEV.dev.touch.kcmKeyboardType row caught.
     *
     *  Resolved from the device's advertised SOURCES rather than from a
     *  hardcoded id list, so it stays correct when the device registry gains a
     *  device; and resolved LAZILY rather than in load(), to stay clear of the
     *  construction cycle described on sResolvingType. The keyboard ids
     *  short-circuit before any query, so the common path (every KeyEvent
     *  carries VIRTUAL_KEYBOARD) never touches the registry. */
    public int getKeyboardType() {
        int type = mKeyboardType;
        if (type < 0) {
            type = resolveKeyboardType(mDeviceId);
            mKeyboardType = type;
        }
        return type;
    }

    private static int resolveKeyboardType(int deviceId) {
        // These two ids DENOTE the keyboard in AOSP's own vocabulary, and the
        // map this class ships is Virtual.kcm, which declares `type FULL`.
        if (deviceId == VIRTUAL_KEYBOARD || deviceId == BUILT_IN_KEYBOARD) return FULL;
        if (Boolean.TRUE.equals(sResolvingType.get())) return FULL;
        sResolvingType.set(Boolean.TRUE);
        try {
            InputDevice dev = InputDevice.getDevice(deviceId);
            // AOSP load() falls back to the virtual keyboard's map for a device
            // id it cannot resolve, so an unknown id is a keyboard, not a void.
            if (dev == null) return FULL;
            return producesKeys(dev.getSources()) ? FULL : KEYBOARD_TYPE_UNKNOWN;
        } catch (Throwable t) {
            // This class is called inline from Unity's key handling; it must
            // never throw out of a getter.
            return FULL;
        } finally {
            sResolvingType.remove();
        }
    }

    /** Tested with `(sources & X) == X` rather than `!= 0` because every
     *  SOURCE_* is a composite of a class bit and a device bit: SOURCE_KEYBOARD
     *  is 0x101, and a `!= 0` test would match any device sharing the 0x1
     *  button-class bit. */
    private static boolean producesKeys(int sources) {
        return (sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                || (sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
                || (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
    }
    /** AOSP KeyCharacterMap.FallbackAction (KeyCharacterMap.java:780-830,
     *  hidden): the key a key falls back to when the focused window does not
     *  handle it. PhoneWindowManager.dispatchUnhandledKey builds the fallback
     *  KeyEvent (FLAG_FALLBACK) from it. Not pooled here: the recycle bin is an
     *  allocation optimisation with no observable behaviour. */
    public static final class FallbackAction {
        public int keyCode;
        public int metaState;
        private FallbackAction() {}
        public static FallbackAction obtain() { return new FallbackAction(); }
        public void recycle() {}
    }

    /** AOSP KeyCharacterMap.getFallbackAction (KeyCharacterMap.java:420-429):
     *  the `fallback` behavior the key's map declares, or null.
     *
     *  Transcribes the "### Gamepad buttons ###" block of Virtual.kcm, which
     *  is line-for-line the same block in Generic.kcm, the map a game
     *  controller loads (android14-release data/keyboards/Virtual.kcm:508-,
     *  Generic.kcm:511-): A, C, Z, THUMBL, THUMBR, START and BUTTON_1..16 ->
     *  DPAD_CENTER; B -> BACK; X -> DEL; Y -> SPACE; SELECT -> MENU;
     *  MODE -> HOME; L1/R1/L2/R2 declare `none`. Every one is a `base:`
     *  behavior, which matches any meta state, and the fallback keeps the
     *  caller's meta state (native getFallbackAction:
     *  `metaState & ~behavior->metaState`, behavior->metaState == 0 for base).
     *  This is what makes B leave a screen and A press the focused button on a
     *  controller in an app that never reads gamepad keys itself. */
    public FallbackAction getFallbackAction(int keyCode, int metaState) {
        final int fallback;
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_C:
            case KeyEvent.KEYCODE_BUTTON_Z:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
            case KeyEvent.KEYCODE_BUTTON_START:
                fallback = KeyEvent.KEYCODE_DPAD_CENTER; break;
            case KeyEvent.KEYCODE_BUTTON_B:      fallback = KeyEvent.KEYCODE_BACK; break;
            case KeyEvent.KEYCODE_BUTTON_X:      fallback = KeyEvent.KEYCODE_DEL; break;
            case KeyEvent.KEYCODE_BUTTON_Y:      fallback = KeyEvent.KEYCODE_SPACE; break;
            case KeyEvent.KEYCODE_BUTTON_SELECT: fallback = KeyEvent.KEYCODE_MENU; break;
            case KeyEvent.KEYCODE_BUTTON_MODE:   fallback = KeyEvent.KEYCODE_HOME; break;
            default:
                if (keyCode >= KeyEvent.KEYCODE_BUTTON_1 && keyCode <= KeyEvent.KEYCODE_BUTTON_16) {
                    fallback = KeyEvent.KEYCODE_DPAD_CENTER; break;
                }
                return null;
        }
        FallbackAction a = FallbackAction.obtain();
        a.keyCode = fallback;
        a.metaState = KeyEvent.normalizeMetaState(metaState);
        return a;
    }

    /** AOSP KeyCharacterMap.java:683-690: derived from the keyboard type. */
    public int getModifierBehavior() {
        switch (getKeyboardType()) {
            case FULL:
            case SPECIAL_FUNCTION:
                return MODIFIER_BEHAVIOR_CHORDED;
            default:
                return MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED;
        }
    }

    /** AOSP KeyCharacterMap.java:620-634: classify the DISPLAY LABEL by Unicode
     *  category. Consequence worth knowing: SPACE, ENTER and TAB are NOT
     *  printing keys (space separator / control), while every letter, digit and
     *  punctuation key is. A `label >= 0x20` test gets SPACE wrong. */
    public boolean isPrintingKey(int keyCode) {
        int type = Character.getType(getDisplayLabel(keyCode));
        switch (type) {
            case Character.SPACE_SEPARATOR:
            case Character.LINE_SEPARATOR:
            case Character.PARAGRAPH_SEPARATOR:
            case Character.CONTROL:
            case Character.FORMAT:
                return false;
            default:
                return true;
        }
    }

    /** AOSP KeyCharacterMap.java:564-585. Returns false and leaves `results`
     *  UNTOUCHED when the key has no display label, which is the signal an
     *  older app uses to mean "this key types nothing". */
    @Deprecated
    public boolean getKeyData(int keyCode, KeyData results) {
        if (results == null) return false;
        if (results.meta.length < KeyData.META_LENGTH) {
            throw new IndexOutOfBoundsException(
                    "results.meta.length must be >= " + KeyData.META_LENGTH);
        }
        char displayLabel = getDisplayLabel(keyCode);
        if (displayLabel == 0) return false;
        results.displayLabel = displayLabel;
        results.number = getNumber(keyCode);
        results.meta[0] = getCharacter(keyCode, 0);
        results.meta[1] = getCharacter(keyCode, KeyEvent.META_SHIFT_ON);
        results.meta[2] = getCharacter(keyCode, KeyEvent.META_ALT_ON);
        results.meta[3] = getCharacter(keyCode,
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
        return true;
    }

    /** AOSP: synthesize the key events that would type `chars`. Mac.apk
     *  returns null (callers treat null as "cannot be typed"), which is
     *  safe -- the host keyboard path injects events directly. */
    public KeyEvent[] getEvents(char[] chars) { return null; }

    /** AOSP asks InputManager which ATTACHED devices can produce the keycode
     *  (KeyCharacterMap.java:700-702). Mac.apk answers for the host: there is
     *  always a keyboard, and no other input device class exists here.
     *
     *  KNOWN DIVERGENCE from the API-30 emulator golden, left deliberately:
     *  the AVD answers false even for KEYCODE_A and KEYCODE_BACK, because its
     *  virtual input devices declare no key map. Matching that would mean
     *  claiming this machine has no keyboard, which is the opposite of true.
     *  The honest answer for KEYCODE_BUTTON_* is nonetheless "no" (there is no
     *  gamepad), and that needs the InputDevice layer to enumerate real
     *  devices -- see the note in the session report. */
    /** ⚠ v1.0.938: these were `return true` for EVERY keycode, and the comment
     *  above already admitted the honest answer is "no".
     *
     *  AOSP (KeyCharacterMap.java:1038-1062) makes both a pure delegation to
     *  InputManager.deviceHasKeys, which is backed by the kernel key bitmap:
     *
     *      public static boolean deviceHasKey(int keyCode) {
     *          int[] codeArray = new int[1];
     *          codeArray[0] = keyCode;
     *          boolean[] ret = deviceHasKeys(codeArray);
     *          return ret[0];
     *      }
     *      public static boolean[] deviceHasKeys(int[] keyCodes) {
     *          return InputManager.getInstance().deviceHasKeys(keyCodes);
     *      }
     *
     *  Three reasons this was a real bug and not golden-chasing:
     *  (1) it CONTRADICTED our own InputDevice.hasKeys(), which already answers
     *      false and already matched the reference on all 8 hasKey rows. AOSP
     *      defines the static form as the union of the per-device form, so
     *      "false everywhere, true for the union" is a state no device can be in;
     *  (2) it is the exact failure InputManager.deviceHasKeys' own comment warns
     *      about -- claiming a key makes an app wait for input that can never
     *      arrive (the classic hasKeys(KEYCODE_BUTTON_A) gamepad probe), and
     *      engines use it to decide whether to draw an on-screen dpad;
     *  (3) false is what a device like the one we publish actually answers: the
     *      reference's own Virtual keyboard reports 0 despite producing every
     *      keycode, because the query is kernel-bitmap-backed. */
    public static boolean deviceHasKey(int keyCode) {
        return deviceHasKeys(new int[] { keyCode })[0];
    }
    public static boolean[] deviceHasKeys(int[] keyCodes) {
        return android.hardware.input.InputManager.getInstance().deviceHasKeys(keyCodes);
    }

    /** AOSP dead-key combiner: getDeadChar('`', 'e') is 'e-grave', and 0 when
     *  the two characters do not combine. Despite the rest of KeyCharacterMap
     *  being native-backed, AOSP implements this one in pure Java
     *  (frameworks/base core/java/android/view/KeyCharacterMap.java): map the
     *  printable accent to its Unicode combining diacritical, append that mark
     *  AFTER the base character, and NFC-normalize the pair. If the result is
     *  a single code point the two composed; otherwise they do not. Verbatim
     *  from AOSP including the identity cases (accent + itself, or accent +
     *  space, yields the bare accent) and the memo of resolved pairs. */
    public static int getDeadChar(int accent, int c) {
        if (c == accent || CHAR_SPACE == c) {
            return accent;
        }
        int combining = sAccentToCombining.get(accent);
        if (combining == 0) {
            return 0;
        }
        final int combination = (combining << 16) | c;
        int combined;
        synchronized (sDeadKeyCache) {
            combined = sDeadKeyCache.get(combination, -1);
            if (combined == -1) {
                String result = java.text.Normalizer.normalize(
                        new String(new char[] { (char) c, (char) combining }),
                        java.text.Normalizer.Form.NFC);
                combined = result.codePointCount(0, result.length()) == 1
                        ? result.codePointAt(0) : 0;
                sDeadKeyCache.put(combination, combined);
            }
        }
        return combined;
    }

    // AOSP inner record describing a key's character data.
    public static class KeyData {
        public static final int META_LENGTH = 4;
        public char displayLabel;
        public char number;
        public char[] meta = new char[META_LENGTH];
    }

    /** AOSP KeyCharacterMap.UnavailableException: thrown by
     *  KeyCharacterMap.load(int) when the requested device's character map
     *  cannot be loaded. Nothing here throws it (the built-in virtual-keyboard
     *  map always loads), but an app that catches it by name needs the type. */
    public static class UnavailableException extends android.util.AndroidRuntimeException {
        public UnavailableException(String msg) { super(msg); }
    }
}
