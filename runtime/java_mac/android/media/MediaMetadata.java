// android.media.MediaMetadata -- immutable metadata for a media item, keyed by
// METADATA_KEY_* strings. AOSP frameworks/base media/java/android/media/MediaMetadata.java.
// A pure data holder; fully serviceable on Mac.apk. Backed by a Bundle, built
// via Builder, Parcelable.
package android.media;

// AOSP fidelity: the unchecked usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("unchecked")
public final class MediaMetadata implements android.os.Parcelable {
    public static final String METADATA_KEY_TITLE = "android.media.metadata.TITLE";
    public static final String METADATA_KEY_ARTIST = "android.media.metadata.ARTIST";
    public static final String METADATA_KEY_DURATION = "android.media.metadata.DURATION";
    public static final String METADATA_KEY_ALBUM = "android.media.metadata.ALBUM";
    public static final String METADATA_KEY_AUTHOR = "android.media.metadata.AUTHOR";
    public static final String METADATA_KEY_WRITER = "android.media.metadata.WRITER";
    public static final String METADATA_KEY_COMPOSER = "android.media.metadata.COMPOSER";
    public static final String METADATA_KEY_COMPILATION = "android.media.metadata.COMPILATION";
    public static final String METADATA_KEY_DATE = "android.media.metadata.DATE";
    public static final String METADATA_KEY_YEAR = "android.media.metadata.YEAR";
    public static final String METADATA_KEY_GENRE = "android.media.metadata.GENRE";
    public static final String METADATA_KEY_TRACK_NUMBER = "android.media.metadata.TRACK_NUMBER";
    public static final String METADATA_KEY_NUM_TRACKS = "android.media.metadata.NUM_TRACKS";
    public static final String METADATA_KEY_DISC_NUMBER = "android.media.metadata.DISC_NUMBER";
    public static final String METADATA_KEY_ALBUM_ARTIST = "android.media.metadata.ALBUM_ARTIST";
    public static final String METADATA_KEY_ART = "android.media.metadata.ART";
    public static final String METADATA_KEY_ART_URI = "android.media.metadata.ART_URI";
    public static final String METADATA_KEY_ALBUM_ART = "android.media.metadata.ALBUM_ART";
    public static final String METADATA_KEY_ALBUM_ART_URI = "android.media.metadata.ALBUM_ART_URI";
    public static final String METADATA_KEY_USER_RATING = "android.media.metadata.USER_RATING";
    public static final String METADATA_KEY_RATING = "android.media.metadata.RATING";
    public static final String METADATA_KEY_DISPLAY_TITLE = "android.media.metadata.DISPLAY_TITLE";
    public static final String METADATA_KEY_DISPLAY_SUBTITLE = "android.media.metadata.DISPLAY_SUBTITLE";
    public static final String METADATA_KEY_DISPLAY_DESCRIPTION = "android.media.metadata.DISPLAY_DESCRIPTION";
    public static final String METADATA_KEY_DISPLAY_ICON = "android.media.metadata.DISPLAY_ICON";
    public static final String METADATA_KEY_DISPLAY_ICON_URI = "android.media.metadata.DISPLAY_ICON_URI";
    public static final String METADATA_KEY_MEDIA_ID = "android.media.metadata.MEDIA_ID";
    public static final String METADATA_KEY_MEDIA_URI = "android.media.metadata.MEDIA_URI";
    public static final String METADATA_KEY_BT_FOLDER_TYPE = "android.media.metadata.BT_FOLDER_TYPE";
    public static final String METADATA_KEY_ADVERTISEMENT = "android.media.metadata.ADVERTISEMENT";
    public static final String METADATA_KEY_DOWNLOAD_STATUS = "android.media.metadata.DOWNLOAD_STATUS";

    // BACKED BY A BUNDLE, as AOSP is (android14-release MediaMetadata.java:424-431,
    // 553-554), and PARCELLED AS ONE:
    //     private MediaMetadata(Parcel in) {
    //         mBundle = in.readBundle();
    //         mBitmapDimensionLimit = Math.max(in.readInt(), 1); }
    //     writeToParcel: dest.writeBundle(mBundle); dest.writeInt(mBitmapDimensionLimit);
    // The wire format is a CONTRACT with code we do not own: androidx
    // MediaMetadataCompat.getMediaMetadata() writes its own Bundle
    // (`dest.writeBundle(mBundle)`) into a Parcel and hands it to THIS class's
    // CREATOR, and fromMediaMetadata() does the reverse. This class used to be a
    // plain Map parcelled with writeMap/readHashMap, on the premise that a Bundle
    // here could not hold a Bitmap or a Rating; both are Parcelable now, and the
    // mismatch meant readHashMap met the Bundle's 'BNDL' magic as a value tag:
    //     RuntimeException: Unmarshalling unknown type code 1279544898 at offset 4
    // at MediaMetadataCompat.getMediaMetadata <- MediaSessionCompat.setMetadata.
    // AntennaPod's PlaybackService calls exactly that on every play, caught it,
    // logged "Playable was not found. Stopping service." and never played
    // (lanes19 media-playback, 2026-09-29; debug/mediasession_gate.sh).
    private final android.os.Bundle mBundle;
    private final int mBitmapDimensionLimit;

    private MediaMetadata(android.os.Bundle bundle, int bitmapDimensionLimit) {
        mBundle = new android.os.Bundle(bundle);
        mBitmapDimensionLimit = bitmapDimensionLimit;
    }

    private MediaMetadata(android.os.Parcel in) {
        mBundle = in.readBundle();
        mBitmapDimensionLimit = Math.max(in.readInt(), 1);
    }

    /**
     * API 33. The maximum width/height any bitmap in this metadata was scaled
     * to. AOSP's Builder scales a bitmap down when it exceeds the limit, so the
     * two halves have to agree; Integer.MAX_VALUE is AOSP's own "no limit was
     * set" value (MediaMetadata.java, getBitmapDimensionLimit).
     */
    public int getBitmapDimensionLimit() { return mBitmapDimensionLimit; }

    public boolean containsKey(String key) { return mBundle.containsKey(key); }
    public CharSequence getText(String key) { return mBundle.getCharSequence(key); }
    public String getString(String key) {
        CharSequence t = getText(key);
        return t != null ? t.toString() : null;
    }
    public long getLong(String key) { return mBundle.getLong(key, 0); }
    /** AOSP :518-528: a value that is not a Bitmap (or cannot be unparcelled)
     *  answers null, it does not throw. */
    public android.graphics.Bitmap getBitmap(String key) {
        try {
            Object v = mBundle.getParcelable(key);
            return v instanceof android.graphics.Bitmap ? (android.graphics.Bitmap) v : null;
        } catch (Exception e) {
            return null;
        }
    }
    /** AOSP :500-510, same contract as getBitmap. */
    public Rating getRating(String key) {
        try {
            Object v = mBundle.getParcelable(key);
            return v instanceof Rating ? (Rating) v : null;
        } catch (Exception e) {
            return null;
        }
    }
    public java.util.Set<String> keySet() { return mBundle.keySet(); }
    public int size() { return mBundle.size(); }
    /** Not AOSP public API; kept for existing callers. A copy of the backing
     *  Bundle, every value type included. */
    public android.os.Bundle getBundle() { return new android.os.Bundle(mBundle); }

    public MediaDescription getDescription() {
        return new MediaDescription.Builder()
            .setMediaId(getString(METADATA_KEY_MEDIA_ID))
            .setTitle(getText(METADATA_KEY_DISPLAY_TITLE) != null
                ? getText(METADATA_KEY_DISPLAY_TITLE) : getText(METADATA_KEY_TITLE))
            .setSubtitle(getText(METADATA_KEY_DISPLAY_SUBTITLE))
            .setDescription(getText(METADATA_KEY_DISPLAY_DESCRIPTION))
            .setIconBitmap(getBitmap(METADATA_KEY_DISPLAY_ICON))
            .build();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeBundle(mBundle);
        dest.writeInt(mBitmapDimensionLimit);
    }

    public static final android.os.Parcelable.Creator<MediaMetadata> CREATOR =
        new android.os.Parcelable.Creator<MediaMetadata>() {
            @Override public MediaMetadata createFromParcel(android.os.Parcel in) {
                return new MediaMetadata(in);
            }
            @Override public MediaMetadata[] newArray(int size) {
                return new MediaMetadata[size];
            }
        };

    public static final class Builder {
        private final android.os.Bundle mBundle;
        private int bitmapDimensionLimit = Integer.MAX_VALUE;
        public Builder() { mBundle = new android.os.Bundle(); }
        public Builder(MediaMetadata source) {
            mBundle = source != null ? new android.os.Bundle(source.mBundle)
                                     : new android.os.Bundle();
            if (source != null) bitmapDimensionLimit = source.mBitmapDimensionLimit;
        }

        /**
         * API 33. AOSP: "Set the maximum bitmap dimension limit ... Bitmaps will
         * be scaled down if their width or height exceeds this value", and it
         * rejects a limit below 1.
         *
         * The scaling is really performed in build() below, not just recorded --
         * an app sets this precisely so an over-large album art does not cross a
         * binder (or, here, sit in memory at full size), and a limit that was
         * stored but never applied would report a bitmap smaller than the one it
         * actually hands back.
         */
        public Builder setBitmapDimensionLimit(int bitmapDimensionLimit) {
            if (bitmapDimensionLimit < 1) {
                throw new IllegalArgumentException(
                        "bitmapDimensionLimit shall be greater than zero");
            }
            this.bitmapDimensionLimit = bitmapDimensionLimit;
            return this;
        }
        // AOSP :800-930 store each type the way these do (putCharSequence for
        // text AND strings, putLong, putParcelable for Rating/Bitmap).
        public Builder putText(String key, CharSequence value) { mBundle.putCharSequence(key, value); return this; }
        public Builder putString(String key, String value) { mBundle.putCharSequence(key, value); return this; }
        public Builder putLong(String key, long value) { mBundle.putLong(key, value); return this; }
        public Builder putBitmap(String key, android.graphics.Bitmap value) { mBundle.putParcelable(key, value); return this; }
        public Builder putRating(String key, Rating value) { mBundle.putParcelable(key, value); return this; }
        public MediaMetadata build() {
            if (bitmapDimensionLimit != Integer.MAX_VALUE) scaleBitmapsToLimit();
            return new MediaMetadata(mBundle, bitmapDimensionLimit);
        }

        /** AOSP's scaleBitmapIfTooBig: preserve the aspect ratio and shrink the
         *  LONGER edge to the limit (MediaMetadata.java, Builder.build). */
        private void scaleBitmapsToLimit() {
            for (String key : new java.util.ArrayList<String>(mBundle.keySet())) {
                Object v;
                try { v = mBundle.getParcelable(key); } catch (Exception e) { continue; }
                if (!(v instanceof android.graphics.Bitmap)) continue;
                android.graphics.Bitmap bmp = (android.graphics.Bitmap) v;
                int w = bmp.getWidth();
                int h = bmp.getHeight();
                if (w <= bitmapDimensionLimit && h <= bitmapDimensionLimit) continue;
                float scale = bitmapDimensionLimit / (float) Math.max(w, h);
                try {
                    mBundle.putParcelable(key, android.graphics.Bitmap.createScaledBitmap(bmp,
                            Math.max(1, Math.round(w * scale)),
                            Math.max(1, Math.round(h * scale)), true));
                } catch (Throwable ignore) {
                    // A bitmap that cannot be scaled is left at its original
                    // size rather than dropped: AOSP would have scaled it, but
                    // losing the art entirely is strictly worse for the app.
                }
            }
        }
    }
}
