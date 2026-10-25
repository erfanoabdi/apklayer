package android.graphics;
/** A (deprecated) animated-GIF holder. Mac.apk has no Movie/GIF decoder wired,
 *  so decode* return an empty Movie and draw is a no-op; dimensions/duration
 *  report 0. Callers that only query it (or fall back to a static drawable) work.
 *  Source: frameworks/base/graphics/java/android/graphics/Movie.java */
public class Movie {
    private int mWidth, mHeight, mDuration, mTime;
    public Movie() {}
    public int width() { return mWidth; }
    public int height() { return mHeight; }
    public int duration() { return mDuration; }
    public boolean isOpaque() { return false; }
    public boolean setTime(int relativeMilliseconds) { mTime = relativeMilliseconds; return mDuration > 0; }
    public void draw(Canvas canvas, float x, float y, Paint paint) {}
    public void draw(Canvas canvas, float x, float y) {}
    public static Movie decodeStream(java.io.InputStream is) { return new Movie(); }
    public static Movie decodeByteArray(byte[] data, int offset, int length) { return new Movie(); }
    public static Movie decodeFile(String pathName) { return new Movie(); }
}
