// android.text.method.DialerKeyListener -- the key listener a PHONE-typed
// EditText installs (android:inputType="phone" goes through it). Was absent
// entirely, which is a NoClassDefFoundError on a very common field type.
// Mirrors frameworks/base/core/java/android/text/method/DialerKeyListener.java.
package android.text.method;

import android.text.InputType;
import android.text.Spannable;
import android.view.KeyEvent;

public class DialerKeyListener extends NumberKeyListener {

    /**
     * AOSP DialerKeyListener.CHARACTERS. Note it is deliberately wider than
     * "digits": DTMF ('#', '*'), pause/wait ',' and ';', the 'N' wildcard, and
     * the human separators a user pastes in.
     */
    public static final char[] CHARACTERS = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '*',
            '+', '-', '(', ')', ',', '/', 'N', '.', ' ', ';'
        };

    private static DialerKeyListener sInstance;

    public DialerKeyListener() {
    }

    @Override
    protected char[] getAcceptedChars() {
        return CHARACTERS;
    }

    public static DialerKeyListener getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
        sInstance = new DialerKeyListener();
        return sInstance;
    }

    @Override
    public int getInputType() {
        return InputType.TYPE_CLASS_PHONE;
    }

    /**
     * AOSP overrides lookup() to prefer the key's NUMBER field over its
     * character: on a phone keypad the '2' key's primary character may be 'a',
     * and a dialer field wants the digit. Only when the number field is not one
     * of our accepted characters does it fall back to the ordinary match.
     */
    @Override
    protected int lookup(KeyEvent event, Spannable content) {
        int meta = getMetaState(content, event);
        int number = event.getNumber();

        if (number != 0 && !(number == '#' || number == '*')) {
            // A digit or DTMF character from the key's number field.
            for (int i = 0; i < CHARACTERS.length; i++) {
                if (CHARACTERS[i] == number) {
                    return number;
                }
            }
        }
        return super.lookup(event, content);
    }
}
