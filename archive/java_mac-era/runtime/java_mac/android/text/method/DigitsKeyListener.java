// android.text.method.DigitsKeyListener -- AOSP key listener that restricts a
// text field to digits (optionally a leading sign and/or a decimal point).
// AOSP:
//   frameworks/base/core/java/android/text/method/DigitsKeyListener.java
//   public class DigitsKeyListener extends NumberKeyListener
// Used as both a KeyListener and an InputFilter on numeric EditTexts. The
// accepted-character set + InputType reflect the sign/decimal flags exactly as
// AOSP defines them, so a number-only field filters letters and reports the
// right soft-keyboard class.
package android.text.method;

import android.text.InputType;

import java.util.Locale;

public class DigitsKeyListener extends NumberKeyListener {
    private final boolean mSign;
    private final boolean mDecimal;
    private final char[] mAccepted;

    // AOSP CHARACTERS table indexed by (sign?1:0) | (decimal?2:0).
    private static final char[] CHARACTERS_NONE =
        { '0','1','2','3','4','5','6','7','8','9' };
    private static final char[] CHARACTERS_SIGN =
        { '0','1','2','3','4','5','6','7','8','9','-','+' };
    private static final char[] CHARACTERS_DECIMAL =
        { '0','1','2','3','4','5','6','7','8','9','.' };
    private static final char[] CHARACTERS_SIGN_DECIMAL =
        { '0','1','2','3','4','5','6','7','8','9','-','+','.' };

    public DigitsKeyListener() {
        this(false, false);
    }

    public DigitsKeyListener(boolean sign, boolean decimal) {
        mSign = sign;
        mDecimal = decimal;
        int kind = (sign ? 1 : 0) | (decimal ? 2 : 0);
        switch (kind) {
            case 1:  mAccepted = CHARACTERS_SIGN; break;
            case 2:  mAccepted = CHARACTERS_DECIMAL; break;
            case 3:  mAccepted = CHARACTERS_SIGN_DECIMAL; break;
            default: mAccepted = CHARACTERS_NONE; break;
        }
    }

    public DigitsKeyListener(Locale locale) {
        this(false, false);
    }

    public DigitsKeyListener(Locale locale, boolean sign, boolean decimal) {
        this(sign, decimal);
    }

    @Override
    protected char[] getAcceptedChars() { return mAccepted; }

    @Override
    public int getInputType() {
        int contentType = InputType.TYPE_CLASS_NUMBER;
        if (mSign) contentType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
        if (mDecimal) contentType |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
        return contentType;
    }

    // ---- AOSP singleton getInstance() factories --------------------------
    private static DigitsKeyListener[] sInstance = new DigitsKeyListener[4];

    public static DigitsKeyListener getInstance() {
        return getInstance(false, false);
    }
    public static DigitsKeyListener getInstance(boolean sign, boolean decimal) {
        int kind = (sign ? 1 : 0) | (decimal ? 2 : 0);
        if (sInstance[kind] == null) sInstance[kind] = new DigitsKeyListener(sign, decimal);
        return sInstance[kind];
    }
    public static DigitsKeyListener getInstance(Locale locale) {
        return new DigitsKeyListener(locale);
    }
    public static DigitsKeyListener getInstance(Locale locale, boolean sign, boolean decimal) {
        return new DigitsKeyListener(locale, sign, decimal);
    }

    // AOSP getInstance(String accepted): build a listener from an explicit set.
    public static DigitsKeyListener getInstance(String accepted) {
        DigitsKeyListener dkl = new DigitsKeyListener(false, false) {
            private final char[] explicit = accepted.toCharArray();
            @Override protected char[] getAcceptedChars() { return explicit; }
        };
        return dkl;
    }
}
