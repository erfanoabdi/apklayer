// android.text.AlteredCharSequence -- a CharSequence view over another one with
// a run of characters SUBSTITUTED, without copying the whole source. Deprecated
// upstream (AOSP's own javadoc says "the functionality this provides is easily
// achieved using other means"), but it is still public API and older text code
// -- particularly password/masking helpers -- constructs one, which was a
// NoClassDefFoundError here.
//
// Mirrors frameworks/base/core/java/android/text/AlteredCharSequence.java: the
// substitution is a char[] that replaces source[substart, subend), and every
// read either falls through to the source or lands in the substitution array.
package android.text;

@Deprecated
public class AlteredCharSequence implements CharSequence, GetChars {

    private final CharSequence mSource;
    private final char[] mChars;
    private final int mStart;
    private final int mEnd;

    /**
     * AOSP AlteredCharSequence.make(CharSequence source, char[] sub, int
     * substart, int subend): a CharSequence that reads like `source` except
     * that [substart, subend) reads from `sub` instead.
     *
     * AOSP has a Spanned fast path returning an AlteredSpanned; we have no
     * such subclass (it is not public API), so a Spanned source is wrapped the
     * same way as any other -- the substitution behaviour is identical, the
     * result simply does not itself implement Spanned. AOSP's own
     * documentation makes no promise about the returned type beyond
     * AlteredCharSequence.
     */
    @Deprecated
    public static AlteredCharSequence make(CharSequence source, char[] sub,
            int substart, int subend) {
        return new AlteredCharSequence(source, sub, substart, subend);
    }

    private AlteredCharSequence(CharSequence source, char[] sub, int substart, int subend) {
        mSource = source;
        mChars = sub;
        mStart = substart;
        mEnd = subend;
    }

    @Deprecated
    @Override
    public char charAt(int off) {
        if (off >= mStart && off < mEnd) {
            return mChars[off - mStart];
        }
        return mSource.charAt(off);
    }

    @Deprecated
    @Override
    public int length() {
        return mSource.length();
    }

    @Deprecated
    @Override
    public CharSequence subSequence(int start, int end) {
        // AOSP: a subSequence that does not overlap the substitution is just
        // the source's own subSequence; one that does is a fresh
        // AlteredCharSequence over the sliced source with the overlapping part
        // of the substitution.
        char[] c = new char[end - start];
        getChars(start, end, c, 0);
        return new String(c);
    }

    @Deprecated
    @Override
    public void getChars(int start, int end, char[] dest, int destoff) {
        TextUtils.getChars(mSource, start, end, dest, destoff);
        int off = start;
        while (off < end) {
            if (off >= mStart && off < mEnd) {
                dest[destoff + off - start] = mChars[off - mStart];
            }
            off++;
        }
    }

    @Deprecated
    @Override
    public String toString() {
        char[] c = new char[length()];
        getChars(0, length(), c, 0);
        return new String(c);
    }
}
