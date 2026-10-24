// android.widget.DialerFilter -- deprecated RelativeLayout used by the legacy
// dialer to filter contacts as digits are typed. Was Absent. We expose the
// mode + filter-text surface. AOSP:
//   frameworks/base/core/java/android/widget/DialerFilter.java
//   @Deprecated public class DialerFilter extends RelativeLayout
package android.widget;

import android.content.Context;

@Deprecated
public class DialerFilter extends RelativeLayout {
    // Values per AOSP frameworks/base core/java/android/widget/DialerFilter.java
    // (android15-release, ~lines 402-412) -- the full and ONLY public mode set;
    // there is no NO_FILTER / LETTERS_AND_DIGITS / LETTERS_NO_DIGITS / NO_DIGITS
    // in the real API (those four were invented here and referenced nowhere else
    // in this tree, so no guest bytecode could possibly depend on them -- an app
    // can only reference a constant its OWN compiler saw in the real android.jar).
    // DIGITS_ONLY/LETTERS_ONLY were also off by one (3/4 instead of 4/5) because
    // DIGITS_AND_LETTERS_NO_LETTERS was missing entirely, found by
    // debug/framework_lint.py's constant-value diff against android.jar.
    public static final int DIGITS_AND_LETTERS = 1;
    public static final int DIGITS_AND_LETTERS_NO_DIGITS = 2;
    public static final int DIGITS_AND_LETTERS_NO_LETTERS = 3;
    public static final int DIGITS_ONLY = 4;
    public static final int LETTERS_ONLY = 5;

    private int mFilterMode = DIGITS_AND_LETTERS;
    private final StringBuilder mLetters = new StringBuilder();
    private final StringBuilder mDigits = new StringBuilder();

    public DialerFilter(Context context) { super(context); }
    public DialerFilter(Context context, android.util.AttributeSet attrs) { super(context, attrs); }

    public boolean isQwertyKeyboard() { return false; }

    public int getMode() { return mFilterMode; }
    public void setMode(int newMode) { mFilterMode = newMode; }

    public CharSequence getLetters() { return mLetters.toString(); }
    public CharSequence getDigits() { return mDigits.toString(); }
    public CharSequence getFilterText() { return getDigits(); }

    public void append(String text) { mDigits.append(text); }
    public void clearText() { mLetters.setLength(0); mDigits.setLength(0); }

    public void setLettersWatcher(android.text.TextWatcher watcher) {}
    public void setDigitsWatcher(android.text.TextWatcher watcher) {}
    public void setFilterWatcher(android.text.TextWatcher watcher) {}

    /** AOSP DialerFilter.java:398 -- protected, and empty in AOSP too: "Called
     *  right after the mode changes to give subclasses the option to restyle,
     *  etc." Mirroring the empty body exactly. */
    protected void onModeChange(int oldMode, int newMode) { }

    /** AOSP :384 -- removes the watcher SPAN from whichever of the two text
     *  buffers the current mode is filtering. This port keeps the two buffers
     *  as plain StringBuilders rather than Spannables (see the fields above),
     *  so there is no span to remove -- which is exactly why its counterpart
     *  setFilterWatcher() above is also inert. The pair stays symmetric: a
     *  watcher that was never installed is not removable either. */
    public void removeFilterWatcher(android.text.TextWatcher watcher) { }
}
