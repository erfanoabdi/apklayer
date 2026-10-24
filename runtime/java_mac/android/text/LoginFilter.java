// android.text.LoginFilter -- an InputFilter that rejects characters not
// allowed in a username / password field, with hooks for the caller to learn
// that a character was dropped. Deprecated upstream but still public API, and
// absent here entirely (a NoClassDefFoundError for any app that installs one).
//
// Mirrors frameworks/base/core/java/android/text/LoginFilter.java: filter()
// brackets the scan with onStart()/onStop(), calls isAllowed() per character,
// reports each rejection through onInvalidCharacter(), and returns either null
// ("keep the source unchanged", the common case) or a SpannableStringBuilder
// with the offending characters removed and the source's spans preserved.
package android.text;

@Deprecated
public abstract class LoginFilter implements InputFilter {

    // Whether an invalid character is APPENDED anyway (the onInvalidCharacter
    // hook still fires) or dropped. AOSP's field of the same name.
    private boolean mAppendInvalid;

    @Deprecated
    LoginFilter(boolean appendInvalid) {
        mAppendInvalid = appendInvalid;
    }

    @Deprecated
    LoginFilter() {
        this(false);
    }

    /**
     * AOSP LoginFilter.filter(...). Note the two distinct returns:
     * null means "the source is acceptable as-is" and the editor inserts it
     * unchanged; a non-null CharSequence replaces it. AOSP only builds the
     * replacement once it has actually seen a rejected character, so the
     * all-valid path allocates nothing -- reproduced, because a filter that
     * always returned a new CharSequence would defeat every caller's
     * "did anything change" check.
     */
    @Deprecated
    public CharSequence filter(CharSequence source, int start, int end,
            Spanned dest, int dstart, int dend) {
        onStart();

        // Scan through beginning characters in dest, calling
        // onInvalidCharacter() for each invalid character.
        for (int i = 0; i < dstart; i++) {
            char c = dest.charAt(i);
            if (!isAllowed(c)) onInvalidCharacter(c);
        }

        // Scan through changed characters rejecting disallowed chars.
        SpannableStringBuilder modification = null;
        int modoff = 0;

        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (isAllowed(c)) {
                // Character allowed.
                modoff++;
            } else {
                if (mAppendInvalid) {
                    modoff++;
                } else {
                    if (modification == null) {
                        modification = new SpannableStringBuilder(source, start, end);
                        modoff = i - start;
                    }
                    modification.delete(modoff, modoff + 1);
                }
                onInvalidCharacter(c);
            }
        }

        // Scan through remaining characters in dest, calling
        // onInvalidCharacter() for each invalid character.
        for (int i = dend; i < dest.length(); i++) {
            char c = dest.charAt(i);
            if (!isAllowed(c)) onInvalidCharacter(c);
        }

        onStop();

        // Either null (we made no changes) or the filtered text.
        return modification;
    }

    /** AOSP: called before the filter scan begins. Default no-op. */
    @Deprecated
    public void onStart() {
    }

    /** AOSP: called for each character the filter rejects. Default no-op. */
    @Deprecated
    public void onInvalidCharacter(char c) {
    }

    /** AOSP: called after the filter scan ends. Default no-op. */
    @Deprecated
    public void onStop() {
    }

    /** AOSP: the one method a subclass must implement. */
    @Deprecated
    public abstract boolean isAllowed(char c);

    /**
     * AOSP LoginFilter.UsernameFilterGMail: only letters, digits and '.' are
     * allowed in a GMail username.
     */
    @Deprecated
    public static class UsernameFilterGMail extends LoginFilter {
        @Deprecated
        public UsernameFilterGMail() {
            super(false);
        }

        @Deprecated
        public UsernameFilterGMail(boolean appendInvalid) {
            super(appendInvalid);
        }

        @Deprecated
        @Override
        public boolean isAllowed(char c) {
            // Allow [a-zA-Z0-9.]
            if ('0' <= c && c <= '9') return true;
            if ('a' <= c && c <= 'z') return true;
            if ('A' <= c && c <= 'Z') return true;
            if ('.' == c) return true;
            return false;
        }
    }

    /**
     * AOSP LoginFilter.UsernameFilterGeneric: letters and digits plus the
     * additional set "@_-+." that AOSP spells out in a field of its own.
     */
    @Deprecated
    public static class UsernameFilterGeneric extends LoginFilter {
        private static final String mAllowed = "@_-+.";

        @Deprecated
        public UsernameFilterGeneric() {
            super(false);
        }

        @Deprecated
        public UsernameFilterGeneric(boolean appendInvalid) {
            super(appendInvalid);
        }

        @Deprecated
        @Override
        public boolean isAllowed(char c) {
            // Allow [a-zA-Z0-9@._+-]
            if ('0' <= c && c <= '9') return true;
            if ('a' <= c && c <= 'z') return true;
            if ('A' <= c && c <= 'Z') return true;
            if (mAllowed.indexOf(c) != -1) return true;
            return false;
        }
    }

    /**
     * AOSP LoginFilter.PasswordFilterGMail: every printable ASCII character
     * except the whitespace / quote / backslash set AOSP lists.
     */
    @Deprecated
    public static class PasswordFilterGMail extends LoginFilter {
        private static final String mDisallowed = " \n\r\"'\\";

        @Deprecated
        public PasswordFilterGMail() {
            super(false);
        }

        @Deprecated
        public PasswordFilterGMail(boolean appendInvalid) {
            super(appendInvalid);
        }

        @Deprecated
        @Override
        public boolean isAllowed(char c) {
            if (' ' > c || c > '~') return false;
            if (mDisallowed.indexOf(c) != -1) return false;
            return true;
        }
    }
}
