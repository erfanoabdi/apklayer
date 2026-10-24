// android.icu.util.IllformedLocaleException -- what ULocale.Builder throws for an
// ill-formed tag or subtag. Published API:
//   developer.android.com/reference/android/icu/util/IllformedLocaleException
//   public class IllformedLocaleException extends RuntimeException
//
// ULocale.Builder delegates to java.util.Locale.Builder (the JDK class was
// contributed from ICU's ULocale.Builder and keeps its grammar), which throws
// java.util.IllformedLocaleException. The builder translates that into this type
// so a caller catching the ICU type -- or RuntimeException, as Hermes'
// LocaleObjectICU does -- sees what it would see on a device.
package android.icu.util;

public class IllformedLocaleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private int mErrIdx = -1;

    public IllformedLocaleException() {
        super();
    }

    public IllformedLocaleException(String message) {
        super(message);
    }

    public IllformedLocaleException(String message, int errorIndex) {
        super(message + ((errorIndex < 0) ? "" : " [at index " + errorIndex + "]"));
        mErrIdx = errorIndex;
    }

    // The JDK exception's message already carries the " [at index N]" suffix
    // (same formatting as ICU's), so keep it verbatim and copy the index.
    static IllformedLocaleException fromJdk(java.util.IllformedLocaleException e) {
        IllformedLocaleException x = new IllformedLocaleException(e.getMessage());
        x.mErrIdx = e.getErrorIndex();
        x.initCause(e);
        return x;
    }

    public int getErrorIndex() {
        return mErrIdx;
    }
}
