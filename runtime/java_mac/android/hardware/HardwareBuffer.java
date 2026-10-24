package android.hardware;
/** A cross-process graphics buffer handle (AHardwareBuffer). Mac.apk bitmaps and
 *  images are software/Skia-backed rather than GPU HardwareBuffers, so this is a
 *  DESCRIPTOR (format + dimensions + usage) rather than a mappable buffer. Apps
 *  that query getFormat()/dimensions or pass the handle through work; genuine
 *  zero-copy GPU access (an IOSurface-backed buffer) is future work.
 *  Source: frameworks/base/core/java/android/hardware/HardwareBuffer.java */
public final class HardwareBuffer implements android.os.Parcelable, AutoCloseable {
    public static final int RGBA_8888    = 1;
    public static final int RGBX_8888    = 2;
    public static final int RGB_888      = 3;
    public static final int RGB_565      = 4;
    public static final int RGBA_FP16    = 0x16;
    public static final int RGBA_1010102 = 0x2b;
    public static final int BLOB         = 0x21;
    public static final int D_16         = 0x30;
    public static final int D_24         = 0x31;
    public static final int D_24_UINT8   = 0x32;
    public static final int D_FP32       = 0x33;
    public static final int D_FP32_UINT8 = 0x34;
    public static final int S_UINT8      = 0x35;
    public static final int YCBCR_420_888 = 0x23;
    public static final int YCBCR_P010   = 0x36;

    // debug/framework_lint.py caught these against the real android-30 SDK
    // jar's constant pool: USAGE_CPU_READ_RARELY was declared 0 here (AOSP
    // reserves 0 for USAGE_CPU_READ_NEVER, a value this class does not
    // separately declare) but AOSP's actual value is 2; USAGE_CPU_WRITE_RARELY
    // has the matching shift-by-4 shape (AOSP = 2L << 4 = 32), not 0L << 4.
    // static final long is a compile-time constant, so any app reading these
    // symbolically already got the real inlined value from javac against the
    // SDK; the wrong value here only mattered to code inside this runtime
    // that compares a usage bitmask against these constants directly.
    public static final long USAGE_CPU_READ_RARELY   = 2L;
    public static final long USAGE_CPU_READ_OFTEN    = 3L;
    public static final long USAGE_CPU_WRITE_RARELY  = 2L << 4;
    public static final long USAGE_CPU_WRITE_OFTEN   = 3L << 4;
    public static final long USAGE_GPU_SAMPLED_IMAGE = 1L << 8;
    public static final long USAGE_GPU_COLOR_OUTPUT  = 1L << 9;
    public static final long USAGE_COMPOSER_OVERLAY  = 1L << 11;
    public static final long USAGE_PROTECTED_CONTENT = 1L << 14;
    public static final long USAGE_VIDEO_ENCODE      = 1L << 16;
    public static final long USAGE_GPU_DATA_BUFFER   = 1L << 24;
    public static final long USAGE_SENSOR_DIRECT_DATA = 1L << 23;
    public static final long USAGE_GPU_CUBE_MAP      = 1L << 25;
    public static final long USAGE_GPU_MIPMAP_COMPLETE = 1L << 26;

    private final int mWidth, mHeight, mFormat, mLayers;
    private final long mUsage;
    private final long mId;
    private volatile boolean mClosed;

    /** AOSP's id is the gralloc buffer's system-wide unique id (nGetId on the
     *  native AHardwareBuffer). We have no gralloc, so the id is minted here
     *  from a process-wide counter. What matters to every documented use of it
     *  -- "a cache key for associating additional objects with a given
     *  HardwareBuffer" (AOSP HardwareBuffer.java:366-377) -- is that it is
     *  stable for the life of one buffer and never collides with another live
     *  one, and a monotonic counter satisfies both. It is NOT stable across
     *  processes, which AOSP's cross-process caching paragraph assumes; that
     *  half needs real shared buffers and is the same future work the class
     *  header already flags. Starts at 1 so 0 stays available as "no buffer",
     *  the value a caller that got a null handle would carry. */
    private static final java.util.concurrent.atomic.AtomicLong sNextId =
        new java.util.concurrent.atomic.AtomicLong(1);

    private HardwareBuffer(int width, int height, int format, int layers, long usage) {
        mWidth = width; mHeight = height; mFormat = format; mLayers = layers; mUsage = usage;
        mId = sNextId.getAndIncrement();
    }
    public static HardwareBuffer create(int width, int height, int format, int layers, long usage) {
        if (width <= 0 || height <= 0 || layers <= 0)
            throw new IllegalArgumentException("Invalid HardwareBuffer dimensions");
        return new HardwareBuffer(width, height, format, layers, usage);
    }
    public static boolean isSupported(int width, int height, int format, int layers, long usage) {
        return true;
    }
    public int getWidth()  { checkOpen(); return mWidth; }
    public int getHeight() { checkOpen(); return mHeight; }
    public int getFormat() { checkOpen(); return mFormat; }
    public int getLayers() { checkOpen(); return mLayers; }
    public long getUsage() { checkOpen(); return mUsage; }
    /** AOSP HardwareBuffer.java:378 getId() -- checkClosed() first, same as
     *  every other accessor here, so a use-after-close is reported rather than
     *  silently answering a stale id. */
    public long getId() { checkOpen(); return mId; }
    public boolean isClosed() { return mClosed; }
    @Override public void close() { mClosed = true; }
    private void checkOpen() { if (mClosed) throw new IllegalStateException("HardwareBuffer has been closed"); }

    @Override public int describeContents() { return CONTENTS_FILE_DESCRIPTOR; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mWidth); dest.writeInt(mHeight); dest.writeInt(mFormat);
        dest.writeInt(mLayers); dest.writeLong(mUsage);
    }
    public static final Creator<HardwareBuffer> CREATOR = new Creator<HardwareBuffer>() {
        @Override public HardwareBuffer createFromParcel(android.os.Parcel in) {
            return new HardwareBuffer(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readLong());
        }
        @Override public HardwareBuffer[] newArray(int size) { return new HardwareBuffer[size]; }
    };
}
