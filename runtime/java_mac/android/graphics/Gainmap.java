package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * AOSP-faithful android.graphics.Gainmap (API 34, Ultra-HDR). Holds the
 * SDR->HDR recovery metadata plus the gainmap bitmap. We have no HDR display
 * path, so this is a faithful data holder: the values round-trip and the
 * gainmap bitmap is retained, but no tone mapping is applied at draw time.
 * Mirrors frameworks/base/graphics/java/android/graphics/Gainmap.java.
 */
public final class Gainmap implements Parcelable {
    /** AOSP Gainmap.GAINMAP_DIRECTION_SDR_TO_HDR -- the base image is SDR and
     *  the gainmap recovers the HDR rendition. AOSP's default. */
    public static final int GAINMAP_DIRECTION_SDR_TO_HDR = 0;
    /** AOSP Gainmap.GAINMAP_DIRECTION_HDR_TO_SDR. */
    public static final int GAINMAP_DIRECTION_HDR_TO_SDR = 1;

    private Bitmap mGainmapContents;
    private final float[] mRatioMin = { 1f, 1f, 1f };
    private final float[] mRatioMax = { 2f, 2f, 2f };
    private final float[] mGamma    = { 1f, 1f, 1f };
    private final float[] mEpsilonSdr = { 0f, 0f, 0f };
    private final float[] mEpsilonHdr = { 0f, 0f, 0f };
    private float mDisplayRatioForFullHdr = 2f;
    private float mMinDisplayRatioForHdrTransition = 1f;

    public Gainmap(Bitmap gainmapContents) {
        mGainmapContents = gainmapContents;
    }

    public Gainmap(Gainmap gainmap, Bitmap gainmapContents) {
        this(gainmapContents);
        if (gainmap != null) {
            System.arraycopy(gainmap.mRatioMin, 0, mRatioMin, 0, 3);
            System.arraycopy(gainmap.mRatioMax, 0, mRatioMax, 0, 3);
            System.arraycopy(gainmap.mGamma, 0, mGamma, 0, 3);
            System.arraycopy(gainmap.mEpsilonSdr, 0, mEpsilonSdr, 0, 3);
            System.arraycopy(gainmap.mEpsilonHdr, 0, mEpsilonHdr, 0, 3);
            mDisplayRatioForFullHdr = gainmap.mDisplayRatioForFullHdr;
            mMinDisplayRatioForHdrTransition = gainmap.mMinDisplayRatioForHdrTransition;
        }
    }

    public Bitmap getGainmapContents() { return mGainmapContents; }
    public void setGainmapContents(Bitmap bitmap) { mGainmapContents = bitmap; }

    public void setRatioMin(float r, float g, float b) { set3(mRatioMin, r, g, b); }
    public float[] getRatioMin() { return mRatioMin.clone(); }
    public void setRatioMax(float r, float g, float b) { set3(mRatioMax, r, g, b); }
    public float[] getRatioMax() { return mRatioMax.clone(); }

    public void setGamma(float r, float g, float b) { set3(mGamma, r, g, b); }
    public float[] getGamma() { return mGamma.clone(); }

    public void setEpsilonSdr(float r, float g, float b) { set3(mEpsilonSdr, r, g, b); }
    public float[] getEpsilonSdr() { return mEpsilonSdr.clone(); }
    public void setEpsilonHdr(float r, float g, float b) { set3(mEpsilonHdr, r, g, b); }
    public float[] getEpsilonHdr() { return mEpsilonHdr.clone(); }

    public void setDisplayRatioForFullHdr(float max) { mDisplayRatioForFullHdr = max; }
    public float getDisplayRatioForFullHdr() { return mDisplayRatioForFullHdr; }
    public void setMinDisplayRatioForHdrTransition(float min) {
        mMinDisplayRatioForHdrTransition = min;
    }
    public float getMinDisplayRatioForHdrTransition() {
        return mMinDisplayRatioForHdrTransition;
    }

    /** AOSP Gainmap.setAlternativeImagePrimaries(ColorSpace) (API 36) -- the
     *  color primaries of the ALTERNATE (usually HDR) rendition. Null means
     *  "same as the base image". Data-only here, like the rest of this class. */
    public void setAlternativeImagePrimaries(ColorSpace colorSpace) {
        mAlternativeImagePrimaries = colorSpace;
    }

    /** AOSP Gainmap.getAlternativeImagePrimaries(). */
    public ColorSpace getAlternativeImagePrimaries() { return mAlternativeImagePrimaries; }
    private ColorSpace mAlternativeImagePrimaries;

    /** AOSP Gainmap.setGainmapDirection(int) (API 36) -- one of the
     *  GAINMAP_DIRECTION_* constants. */
    public void setGainmapDirection(int direction) { mGainmapDirection = direction; }

    /** AOSP Gainmap.getGainmapDirection(). Defaults to SDR_TO_HDR. */
    public int getGainmapDirection() { return mGainmapDirection; }
    private int mGainmapDirection = GAINMAP_DIRECTION_SDR_TO_HDR;

    private static void set3(float[] a, float r, float g, float b) {
        a[0] = r; a[1] = g; a[2] = b;
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloatArray(mRatioMin);
        dest.writeFloatArray(mRatioMax);
        dest.writeFloatArray(mGamma);
        dest.writeFloatArray(mEpsilonSdr);
        dest.writeFloatArray(mEpsilonHdr);
        dest.writeFloat(mDisplayRatioForFullHdr);
        dest.writeFloat(mMinDisplayRatioForHdrTransition);
    }

    public static final Parcelable.Creator<Gainmap> CREATOR =
            new Parcelable.Creator<Gainmap>() {
        @Override
        public Gainmap createFromParcel(Parcel source) {
            // The gainmap bitmap is not serialized here (we have no Bitmap
            // parceling); construct with a null contents and rehydrate the
            // metadata so the value round-trips for apps that only read it.
            Gainmap g = new Gainmap((Bitmap) null);
            System.arraycopy(source.createFloatArray(), 0, g.mRatioMin, 0, 3);
            System.arraycopy(source.createFloatArray(), 0, g.mRatioMax, 0, 3);
            System.arraycopy(source.createFloatArray(), 0, g.mGamma, 0, 3);
            System.arraycopy(source.createFloatArray(), 0, g.mEpsilonSdr, 0, 3);
            System.arraycopy(source.createFloatArray(), 0, g.mEpsilonHdr, 0, 3);
            g.mDisplayRatioForFullHdr = source.readFloat();
            g.mMinDisplayRatioForHdrTransition = source.readFloat();
            return g;
        }

        @Override
        public Gainmap[] newArray(int size) { return new Gainmap[size]; }
    };
}
