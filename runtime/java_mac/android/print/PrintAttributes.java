// android.print.PrintAttributes
//
// The page/media attributes for a print job: media size, resolution, minimum
// margins, colour mode and duplex mode. A pure value type, and the one apps
// touch most in the print API -- a PrintDocumentAdapter is handed one in
// onLayout and derives its page geometry from it.
//
// Mirrors frameworks/base/core/java/android/print/PrintAttributes.java
// (android16-release).
//
// THE MEDIA-SIZE CONSTANTS ARE DATA, TRANSCRIBED VERBATIM. Every width/height
// below is copied literally out of AOSP's PrintAttributes.java; none is
// computed, converted or rounded here. A wrong media size does not throw -- it
// silently produces the wrong page geometry, which is why they are transcribed
// rather than derived. (The previous revision of this file had ISO_A4 as
// 8268 x 11692 mils; AOSP says 8270 x 11690, and UNKNOWN_PORTRAIT /
// UNKNOWN_LANDSCAPE were 1x1 instead of 1 x MAX_VALUE / MAX_VALUE x 1.)
//
// LABELS. AOSP's predefined sizes carry a com.android.internal R.string id and
// resolve it through PackageManager against package "android". Those are
// framework-internal resource ids we do not have, so each constant instead
// carries the literal English string AOSP's core/res/values/strings.xml maps
// that id to (fetched from the same android16-release tag), with labelResId 0.
// getLabel(PackageManager) implements AOSP's logic unchanged, so it takes the
// resId == 0 branch and returns exactly the text a US-locale device shows.
// KNOWN LIMITATION: these labels are not localized, where a device would
// localize them. Wiring real framework string resources would fix it without
// touching this class's logic.
package android.print;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;

public final class PrintAttributes implements Parcelable {

    /** @hide */
    public static final int COLOR_MODE_MONOCHROME = 1 << 0;
    /** @hide */
    public static final int COLOR_MODE_COLOR = 1 << 1;

    private static final int VALID_COLOR_MODES =
            COLOR_MODE_MONOCHROME | COLOR_MODE_COLOR;

    public static final int DUPLEX_MODE_NONE = 1 << 0;
    public static final int DUPLEX_MODE_LONG_EDGE = 1 << 1;
    public static final int DUPLEX_MODE_SHORT_EDGE = 1 << 2;

    private static final int VALID_DUPLEX_MODES =
            DUPLEX_MODE_NONE | DUPLEX_MODE_LONG_EDGE | DUPLEX_MODE_SHORT_EDGE;

    private MediaSize mMediaSize;
    private Resolution mResolution;
    private Margins mMinMargins;

    private int mColorMode;
    private int mDuplexMode;

    PrintAttributes() {
        /* hide constructor */
    }

    private PrintAttributes(Parcel parcel) {
        mMediaSize = (parcel.readInt() == 1) ? MediaSize.createFromParcel(parcel) : null;
        mResolution = (parcel.readInt() == 1) ? Resolution.createFromParcel(parcel) : null;
        mMinMargins = (parcel.readInt() == 1) ? Margins.createFromParcel(parcel) : null;
        mColorMode = parcel.readInt();
        if (mColorMode != 0) {
            enforceValidColorMode(mColorMode);
        }
        mDuplexMode = parcel.readInt();
        if (mDuplexMode != 0) {
            enforceValidDuplexMode(mDuplexMode);
        }
    }

    public MediaSize getMediaSize() { return mMediaSize; }

    /** @hide */
    public void setMediaSize(MediaSize mediaSize) { mMediaSize = mediaSize; }

    public Resolution getResolution() { return mResolution; }

    /** @hide */
    public void setResolution(Resolution resolution) { mResolution = resolution; }

    public Margins getMinMargins() { return mMinMargins; }

    /** @hide */
    public void setMinMargins(Margins margins) { mMinMargins = margins; }

    public int getColorMode() { return mColorMode; }

    /** @hide */
    public void setColorMode(int colorMode) {
        enforceValidColorMode(colorMode);
        mColorMode = colorMode;
    }

    /** @hide Whether this attribute set has a colour mode. */
    public boolean isDefaultColorMode() { return mColorMode == 0; }

    public int getDuplexMode() { return mDuplexMode; }

    /** @hide */
    public void setDuplexMode(int duplexMode) {
        enforceValidDuplexMode(duplexMode);
        mDuplexMode = duplexMode;
    }

    /** @hide Returns a copy of this instance with the media size in portrait. */
    public PrintAttributes asPortrait() {
        if (mMediaSize == null) return this;
        PrintAttributes attributes = new PrintAttributes();
        attributes.setMediaSize(mMediaSize.asPortrait());
        attributes.setResolution(mResolution);
        // Rotate the margins with the media.
        if (mMinMargins != null) {
            if (mMediaSize.isPortrait()) {
                attributes.setMinMargins(mMinMargins);
            } else {
                attributes.setMinMargins(new Margins(mMinMargins.getBottomMils(),
                        mMinMargins.getLeftMils(), mMinMargins.getTopMils(),
                        mMinMargins.getRightMils()));
            }
        }
        attributes.mColorMode = mColorMode;
        attributes.mDuplexMode = mDuplexMode;
        return attributes;
    }

    /** @hide Returns a copy of this instance with the media size in landscape. */
    public PrintAttributes asLandscape() {
        if (mMediaSize == null) return this;
        PrintAttributes attributes = new PrintAttributes();
        attributes.setMediaSize(mMediaSize.asLandscape());
        attributes.setResolution(mResolution);
        if (mMinMargins != null) {
            if (mMediaSize.isPortrait()) {
                attributes.setMinMargins(new Margins(mMinMargins.getTopMils(),
                        mMinMargins.getRightMils(), mMinMargins.getBottomMils(),
                        mMinMargins.getLeftMils()));
            } else {
                attributes.setMinMargins(mMinMargins);
            }
        }
        attributes.mColorMode = mColorMode;
        attributes.mDuplexMode = mDuplexMode;
        return attributes;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (mMediaSize != null) {
            parcel.writeInt(1);
            mMediaSize.writeToParcel(parcel);
        } else {
            parcel.writeInt(0);
        }
        if (mResolution != null) {
            parcel.writeInt(1);
            mResolution.writeToParcel(parcel);
        } else {
            parcel.writeInt(0);
        }
        if (mMinMargins != null) {
            parcel.writeInt(1);
            mMinMargins.writeToParcel(parcel);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(mColorMode);
        parcel.writeInt(mDuplexMode);
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mColorMode;
        result = prime * result + mDuplexMode;
        result = prime * result + ((mMinMargins == null) ? 0 : mMinMargins.hashCode());
        result = prime * result + ((mMediaSize == null) ? 0 : mMediaSize.hashCode());
        result = prime * result + ((mResolution == null) ? 0 : mResolution.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PrintAttributes other = (PrintAttributes) obj;
        if (mColorMode != other.mColorMode) return false;
        if (mDuplexMode != other.mDuplexMode) return false;
        if (mMinMargins == null) {
            if (other.mMinMargins != null) return false;
        } else if (!mMinMargins.equals(other.mMinMargins)) {
            return false;
        }
        if (mMediaSize == null) {
            if (other.mMediaSize != null) return false;
        } else if (!mMediaSize.equals(other.mMediaSize)) {
            return false;
        }
        if (mResolution == null) {
            if (other.mResolution != null) return false;
        } else if (!mResolution.equals(other.mResolution)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PrintAttributes{");
        builder.append("mediaSize: ").append(mMediaSize);
        if (mMediaSize != null) {
            builder.append(", orientation: ").append(mMediaSize.isPortrait()
                    ? "portrait" : "landscape");
        } else {
            builder.append(", orientation: ").append("null");
        }
        builder.append(", resolution: ").append(mResolution);
        builder.append(", minMargins: ").append(mMinMargins);
        builder.append(", colorMode: ").append(colorModeToString(mColorMode));
        builder.append(", duplexMode: ").append(duplexModeToString(mDuplexMode));
        builder.append("}");
        return builder.toString();
    }

    /** @hide */
    public void clear() {
        mMediaSize = null;
        mResolution = null;
        mMinMargins = null;
        mColorMode = 0;
        mDuplexMode = 0;
    }

    /** @hide Copies the fields of the given attributes into this instance. */
    public void copyFrom(PrintAttributes other) {
        mMediaSize = other.mMediaSize;
        mResolution = other.mResolution;
        mMinMargins = other.mMinMargins;
        mColorMode = other.mColorMode;
        mDuplexMode = other.mDuplexMode;
    }

    /**
     * A media (page) size. The dimensions are in mils (thousandths of an inch).
     * Every predefined constant below is transcribed verbatim from AOSP.
     */
    public static final class MediaSize {
        private static final String LOG_TAG = "MediaSize";

        private static final java.util.Map<String, MediaSize> sIdToMediaSizeMap =
                new java.util.HashMap<String, MediaSize>();

        // Unknown / orientation-only
        public static final MediaSize UNKNOWN_PORTRAIT =
                predefined("UNKNOWN_PORTRAIT", "Unknown portrait", 1, Integer.MAX_VALUE);
        public static final MediaSize UNKNOWN_LANDSCAPE =
                predefined("UNKNOWN_LANDSCAPE", "Unknown landscape", Integer.MAX_VALUE, 1);
        // ISO sizes
        public static final MediaSize ISO_A0 =
                predefined("ISO_A0", "ISO A0", 33110, 46810);
        public static final MediaSize ISO_A1 =
                predefined("ISO_A1", "ISO A1", 23390, 33110);
        public static final MediaSize ISO_A2 =
                predefined("ISO_A2", "ISO A2", 16540, 23390);
        public static final MediaSize ISO_A3 =
                predefined("ISO_A3", "ISO A3", 11690, 16540);
        public static final MediaSize ISO_A4 =
                predefined("ISO_A4", "ISO A4", 8270, 11690);
        public static final MediaSize ISO_A5 =
                predefined("ISO_A5", "ISO A5", 5830, 8270);
        public static final MediaSize ISO_A6 =
                predefined("ISO_A6", "ISO A6", 4130, 5830);
        public static final MediaSize ISO_A7 =
                predefined("ISO_A7", "ISO A7", 2910, 4130);
        public static final MediaSize ISO_A8 =
                predefined("ISO_A8", "ISO A8", 2050, 2910);
        public static final MediaSize ISO_A9 =
                predefined("ISO_A9", "ISO A9", 1460, 2050);
        public static final MediaSize ISO_A10 =
                predefined("ISO_A10", "ISO A10", 1020, 1460);
        public static final MediaSize ISO_B0 =
                predefined("ISO_B0", "ISO B0", 39370, 55670);
        public static final MediaSize ISO_B1 =
                predefined("ISO_B1", "ISO B1", 27830, 39370);
        public static final MediaSize ISO_B2 =
                predefined("ISO_B2", "ISO B2", 19690, 27830);
        public static final MediaSize ISO_B3 =
                predefined("ISO_B3", "ISO B3", 13900, 19690);
        public static final MediaSize ISO_B4 =
                predefined("ISO_B4", "ISO B4", 9840, 13900);
        public static final MediaSize ISO_B5 =
                predefined("ISO_B5", "ISO B5", 6930, 9840);
        public static final MediaSize ISO_B6 =
                predefined("ISO_B6", "ISO B6", 4920, 6930);
        public static final MediaSize ISO_B7 =
                predefined("ISO_B7", "ISO B7", 3460, 4920);
        public static final MediaSize ISO_B8 =
                predefined("ISO_B8", "ISO B8", 2440, 3460);
        public static final MediaSize ISO_B9 =
                predefined("ISO_B9", "ISO B9", 1730, 2440);
        public static final MediaSize ISO_B10 =
                predefined("ISO_B10", "ISO B10", 1220, 1730);
        public static final MediaSize ISO_C0 =
                predefined("ISO_C0", "ISO C0", 36100, 51060);
        public static final MediaSize ISO_C1 =
                predefined("ISO_C1", "ISO C1", 25510, 36100);
        public static final MediaSize ISO_C2 =
                predefined("ISO_C2", "ISO C2", 18030, 25510);
        public static final MediaSize ISO_C3 =
                predefined("ISO_C3", "ISO C3", 12760, 18030);
        public static final MediaSize ISO_C4 =
                predefined("ISO_C4", "ISO C4", 9020, 12760);
        public static final MediaSize ISO_C5 =
                predefined("ISO_C5", "ISO C5", 6380, 9020);
        public static final MediaSize ISO_C6 =
                predefined("ISO_C6", "ISO C6", 4490, 6380);
        public static final MediaSize ISO_C7 =
                predefined("ISO_C7", "ISO C7", 3190, 4490);
        public static final MediaSize ISO_C8 =
                predefined("ISO_C8", "ISO C8", 2240, 3190);
        public static final MediaSize ISO_C9 =
                predefined("ISO_C9", "ISO C9", 1570, 2240);
        public static final MediaSize ISO_C10 =
                predefined("ISO_C10", "ISO C10", 1100, 1570);
        // North America sizes
        public static final MediaSize NA_LETTER =
                predefined("NA_LETTER", "Letter", 8500, 11000);
        public static final MediaSize NA_GOVT_LETTER =
                predefined("NA_GOVT_LETTER", "Government Letter", 8000, 10500);
        public static final MediaSize NA_LEGAL =
                predefined("NA_LEGAL", "Legal", 8500, 14000);
        public static final MediaSize NA_JUNIOR_LEGAL =
                predefined("NA_JUNIOR_LEGAL", "Junior Legal", 8000, 5000);
        public static final MediaSize NA_LEDGER =
                predefined("NA_LEDGER", "Ledger", 17000, 11000);
        public static final MediaSize NA_TABLOID =
                predefined("NA_TABLOID", "Tabloid", 11000, 17000);
        public static final MediaSize NA_INDEX_3X5 =
                predefined("NA_INDEX_3X5", "Index Card 3x5", 3000, 5000);
        public static final MediaSize NA_INDEX_4X6 =
                predefined("NA_INDEX_4X6", "Index Card 4x6", 4000, 6000);
        public static final MediaSize NA_INDEX_5X8 =
                predefined("NA_INDEX_5X8", "Index Card 5x8", 5000, 8000);
        public static final MediaSize NA_MONARCH =
                predefined("NA_MONARCH", "Monarch", 7250, 10500);
        public static final MediaSize NA_QUARTO =
                predefined("NA_QUARTO", "Quarto", 8000, 10000);
        public static final MediaSize NA_FOOLSCAP =
                predefined("NA_FOOLSCAP", "Foolscap", 8000, 13000);
        // ANSI sizes
        public static final MediaSize ANSI_C =
                predefined("ANSI_C", "ANSI C", 17000, 22000);
        public static final MediaSize ANSI_D =
                predefined("ANSI_D", "ANSI D", 22000, 34000);
        public static final MediaSize ANSI_E =
                predefined("ANSI_E", "ANSI E", 34000, 44000);
        public static final MediaSize ANSI_F =
                predefined("ANSI_F", "ANSI F", 28000, 40000);
        // Arch sizes
        public static final MediaSize NA_ARCH_A =
                predefined("NA_ARCH_A", "Arch A", 9000, 12000);
        public static final MediaSize NA_ARCH_B =
                predefined("NA_ARCH_B", "Arch B", 12000, 18000);
        public static final MediaSize NA_ARCH_C =
                predefined("NA_ARCH_C", "Arch C", 18000, 24000);
        public static final MediaSize NA_ARCH_D =
                predefined("NA_ARCH_D", "Arch D", 24000, 36000);
        public static final MediaSize NA_ARCH_E =
                predefined("NA_ARCH_E", "Arch E", 36000, 48000);
        public static final MediaSize NA_ARCH_E1 =
                predefined("NA_ARCH_E1", "Arch E1", 30000, 42000);
        public static final MediaSize NA_SUPER_B =
                predefined("NA_SUPER_B", "Super B", 13000, 19000);
        // Chinese sizes
        public static final MediaSize ROC_8K =
                predefined("ROC_8K", "ROC 8K", 10629, 15354);
        public static final MediaSize ROC_16K =
                predefined("ROC_16K", "ROC 16K", 7677, 10629);
        public static final MediaSize PRC_1 =
                predefined("PRC_1", "PRC 1", 4015, 6496);
        public static final MediaSize PRC_2 =
                predefined("PRC_2", "PRC 2", 4015, 6929);
        public static final MediaSize PRC_3 =
                predefined("PRC_3", "PRC 3", 4921, 6929);
        public static final MediaSize PRC_4 =
                predefined("PRC_4", "PRC 4", 4330, 8189);
        public static final MediaSize PRC_5 =
                predefined("PRC_5", "PRC 5", 4330, 8661);
        public static final MediaSize PRC_6 =
                predefined("PRC_6", "PRC 6", 4724, 12599);
        public static final MediaSize PRC_7 =
                predefined("PRC_7", "PRC 7", 6299, 9055);
        public static final MediaSize PRC_8 =
                predefined("PRC_8", "PRC 8", 4724, 12165);
        public static final MediaSize PRC_9 =
                predefined("PRC_9", "PRC 9", 9016, 12756);
        public static final MediaSize PRC_10 =
                predefined("PRC_10", "PRC 10", 12756, 18032);
        public static final MediaSize PRC_16K =
                predefined("PRC_16K", "PRC 16K", 5749, 8465);
        public static final MediaSize OM_PA_KAI =
                predefined("OM_PA_KAI", "Pa Kai", 10512, 15315);
        public static final MediaSize OM_DAI_PA_KAI =
                predefined("OM_DAI_PA_KAI", "Dai Pa Kai", 10827, 15551);
        public static final MediaSize OM_JUURO_KU_KAI =
                predefined("OM_JUURO_KU_KAI", "Jurro Ku Kai", 7796, 10827);
        // Japanese sizes
        public static final MediaSize JIS_B10 =
                predefined("JIS_B10", "JIS B10", 1259, 1772);
        public static final MediaSize JIS_B9 =
                predefined("JIS_B9", "JIS B9", 1772, 2520);
        public static final MediaSize JIS_B8 =
                predefined("JIS_B8", "JIS B8", 2520, 3583);
        public static final MediaSize JIS_B7 =
                predefined("JIS_B7", "JIS B7", 3583, 5049);
        public static final MediaSize JIS_B6 =
                predefined("JIS_B6", "JIS B6", 5049, 7165);
        public static final MediaSize JIS_B5 =
                predefined("JIS_B5", "JIS B5", 7165, 10118);
        public static final MediaSize JIS_B4 =
                predefined("JIS_B4", "JIS B4", 10118, 14331);
        public static final MediaSize JIS_B3 =
                predefined("JIS_B3", "JIS B3", 14331, 20276);
        public static final MediaSize JIS_B2 =
                predefined("JIS_B2", "JIS B2", 20276, 28661);
        public static final MediaSize JIS_B1 =
                predefined("JIS_B1", "JIS B1", 28661, 40551);
        public static final MediaSize JIS_B0 =
                predefined("JIS_B0", "JIS B0", 40551, 57323);
        public static final MediaSize JIS_EXEC =
                predefined("JIS_EXEC", "JIS Exec", 8504, 12992);
        public static final MediaSize JPN_CHOU4 =
                predefined("JPN_CHOU4", "Chou4", 3543, 8071);
        public static final MediaSize JPN_CHOU3 =
                predefined("JPN_CHOU3", "Chou3", 4724, 9252);
        public static final MediaSize JPN_CHOU2 =
                predefined("JPN_CHOU2", "Chou2", 4374, 5748);
        public static final MediaSize JPN_HAGAKI =
                predefined("JPN_HAGAKI", "Hagaki ", 3937, 5827);  // trailing space is AOSP's own string value
        public static final MediaSize JPN_OUFUKU =
                predefined("JPN_OUFUKU", "Oufuku ", 5827, 7874);  // trailing space is AOSP's own string value
        public static final MediaSize JPN_KAHU =
                predefined("JPN_KAHU", "Kahu", 9449, 12681);
        public static final MediaSize JPN_KAKU2 =
                predefined("JPN_KAKU2", "Kaku2", 9449, 13071);
        public static final MediaSize JPN_YOU4 =
                predefined("JPN_YOU4", "You4", 4134, 9252);
        public static final MediaSize JPN_OE_PHOTO_L =
                predefined("JPN_OE_PHOTO_L", "L", 3500, 5000);

        /**
         * Builds one of the predefined sizes above and registers it under its
         * id, mirroring what AOSP's package-private (id, packageName,
         * labelResId, w, h) constructor does. packageName is "android" to match
         * AOSP; labelResId is 0 because the AOSP ids are com.android.internal
         * resources we cannot resolve, so the label is stored directly.
         */
        private static MediaSize predefined(String id, String label,
                int widthMils, int heightMils) {
            MediaSize size = new MediaSize(id, label, "android", widthMils, heightMils, 0);
            sIdToMediaSizeMap.put(id, size);
            return size;
        }

        private final String mId;
        /** @hide */
        public final String mLabel;
        /** @hide */
        public final String mPackageName;
        /** @hide */
        public final int mLabelResId;
        private final int mWidthMils;
        private final int mHeightMils;

        /** @hide AOSP's resource-id form; kept so the constructor set matches. */
        public MediaSize(String id, String packageName, int labelResId,
                int widthMils, int heightMils) {
            this(id, null, packageName, widthMils, heightMils, labelResId);
            sIdToMediaSizeMap.put(mId, this);
        }

        /**
         * Public constructor: id plus an already-localized label.
         */
        public MediaSize(String id, String label, int widthMils, int heightMils) {
            this(id, label, null, widthMils, heightMils, 0);
        }

        /** @hide The full form; the predefined constants above use it. */
        public MediaSize(String id, String label, String packageName, int widthMils,
                int heightMils, int labelResId) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("id cannot be empty.");
            }
            if (widthMils <= 0) {
                throw new IllegalArgumentException("widthMils cannot be "
                        + "less than or equal to zero.");
            }
            if (heightMils <= 0) {
                throw new IllegalArgumentException("heightMils cannot be "
                        + "less than or equal to zero.");
            }
            // AOSP requires exactly one of (label) / (packageName + labelResId).
            final boolean hasLabel = label != null && label.length() > 0;
            final boolean hasResLabel = packageName != null && packageName.length() > 0
                    && labelResId != 0;
            if (hasLabel == hasResLabel) {
                throw new IllegalArgumentException("label cannot be empty.");
            }
            mId = id;
            mLabel = label;
            mPackageName = packageName;
            mLabelResId = labelResId;
            mWidthMils = widthMils;
            mHeightMils = heightMils;
        }

        /** @hide All predefined sizes except UNKNOWN_PORTRAIT / UNKNOWN_LANDSCAPE. */
        public static java.util.Set<MediaSize> getAllPredefinedSizes() {
            java.util.Set<MediaSize> definedMediaSizes =
                    new java.util.LinkedHashSet<MediaSize>(sIdToMediaSizeMap.values());
            definedMediaSizes.remove(UNKNOWN_PORTRAIT);
            definedMediaSizes.remove(UNKNOWN_LANDSCAPE);
            return definedMediaSizes;
        }

        /** @hide Looks up a predefined size by its id, or null. */
        public static MediaSize getStandardMediaSizeById(String id) {
            return sIdToMediaSizeMap.get(id);
        }

        public String getId() { return mId; }

        /**
         * AOSP's exact logic: resolve the label resource against its owning
         * package when there is one, else return the stored label. Our
         * predefined constants store the label directly (labelResId == 0), so
         * they take the second branch -- see the file header.
         */
        public String getLabel(PackageManager packageManager) {
            if (mPackageName != null && mPackageName.length() > 0 && mLabelResId > 0) {
                try {
                    return packageManager.getResourcesForApplication(
                            mPackageName).getString(mLabelResId);
                } catch (Resources.NotFoundException e) {
                    android.util.Log.w(LOG_TAG, "Could not load resouce" + mLabelResId
                            + " from package " + mPackageName);
                } catch (PackageManager.NameNotFoundException e) {
                    android.util.Log.w(LOG_TAG, "Could not load resouce" + mLabelResId
                            + " from package " + mPackageName);
                }
            }
            return mLabel;
        }

        public int getWidthMils() { return mWidthMils; }

        public int getHeightMils() { return mHeightMils; }

        public boolean isPortrait() { return mHeightMils >= mWidthMils; }

        public MediaSize asPortrait() {
            if (isPortrait()) return this;
            return new MediaSize(mId, mLabel, mPackageName,
                    Math.min(mWidthMils, mHeightMils),
                    Math.max(mWidthMils, mHeightMils),
                    mLabelResId);
        }

        public MediaSize asLandscape() {
            if (!isPortrait()) return this;
            return new MediaSize(mId, mLabel, mPackageName,
                    Math.max(mWidthMils, mHeightMils),
                    Math.min(mWidthMils, mHeightMils),
                    mLabelResId);
        }

        void writeToParcel(Parcel parcel) {
            parcel.writeString(mId);
            parcel.writeString(mLabel);
            parcel.writeString(mPackageName);
            parcel.writeInt(mWidthMils);
            parcel.writeInt(mHeightMils);
            parcel.writeInt(mLabelResId);
        }

        static MediaSize createFromParcel(Parcel parcel) {
            return new MediaSize(
                    parcel.readString(),
                    parcel.readString(),
                    parcel.readString(),
                    parcel.readInt(),
                    parcel.readInt(),
                    parcel.readInt());
        }

        // AOSP hashes/compares ONLY the dimensions, not the id or label. Two
        // sizes with the same dimensions are equal even under different ids.
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + mWidthMils;
            result = prime * result + mHeightMils;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            MediaSize other = (MediaSize) obj;
            if (mWidthMils != other.mWidthMils) return false;
            if (mHeightMils != other.mHeightMils) return false;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("MediaSize{");
            builder.append("id: ").append(mId);
            builder.append(", label: ").append(mLabel);
            builder.append(", packageName: ").append(mPackageName);
            builder.append(", heightMils: ").append(mHeightMils);
            builder.append(", widthMils: ").append(mWidthMils);
            builder.append(", labelResId: ").append(mLabelResId);
            builder.append("}");
            return builder.toString();
        }
    }

    /** A printer resolution in DPI. */
    public static final class Resolution {
        private final String mId;
        private final String mLabel;
        private final int mHorizontalDpi;
        private final int mVerticalDpi;

        public Resolution(String id, String label, int horizontalDpi, int verticalDpi) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("id cannot be empty.");
            }
            if (label == null || label.isEmpty()) {
                throw new IllegalArgumentException("label cannot be empty.");
            }
            if (horizontalDpi <= 0) {
                throw new IllegalArgumentException("horizontalDpi "
                        + "cannot be less than or equal to zero.");
            }
            if (verticalDpi <= 0) {
                throw new IllegalArgumentException("verticalDpi"
                        + " cannot be less than or equal to zero.");
            }
            mId = id;
            mLabel = label;
            mHorizontalDpi = horizontalDpi;
            mVerticalDpi = verticalDpi;
        }

        public String getId() { return mId; }

        /**
         * The human readable label. Unlike MediaSize this is NOT a resource
         * lookup in AOSP either -- Resolution has no resId form, the label is
         * whatever the constructor was given.
         */
        public String getLabel() { return mLabel; }

        public int getHorizontalDpi() { return mHorizontalDpi; }

        public int getVerticalDpi() { return mVerticalDpi; }

        void writeToParcel(Parcel parcel) {
            parcel.writeString(mId);
            parcel.writeString(mLabel);
            parcel.writeInt(mHorizontalDpi);
            parcel.writeInt(mVerticalDpi);
        }

        static Resolution createFromParcel(Parcel parcel) {
            return new Resolution(
                    parcel.readString(),
                    parcel.readString(),
                    parcel.readInt(),
                    parcel.readInt());
        }

        // AOSP hashes/compares ONLY the dpi pair, not the id or label.
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + mHorizontalDpi;
            result = prime * result + mVerticalDpi;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Resolution other = (Resolution) obj;
            if (mHorizontalDpi != other.mHorizontalDpi) return false;
            if (mVerticalDpi != other.mVerticalDpi) return false;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Resolution{");
            builder.append("id: ").append(mId);
            builder.append(", label: ").append(mLabel);
            builder.append(", horizontalDpi: ").append(mHorizontalDpi);
            builder.append(", verticalDpi: ").append(mVerticalDpi);
            builder.append("}");
            return builder.toString();
        }
    }

    /** The minimum margins, in mils, the printer cannot print inside. */
    public static final class Margins {
        public static final Margins NO_MARGINS = new Margins(0, 0, 0, 0);

        private final int mLeftMils;
        private final int mTopMils;
        private final int mRightMils;
        private final int mBottomMils;

        public Margins(int leftMils, int topMils, int rightMils, int bottomMils) {
            mTopMils = topMils;
            mLeftMils = leftMils;
            mRightMils = rightMils;
            mBottomMils = bottomMils;
        }

        public int getLeftMils() { return mLeftMils; }
        public int getTopMils() { return mTopMils; }
        public int getRightMils() { return mRightMils; }
        public int getBottomMils() { return mBottomMils; }

        void writeToParcel(Parcel parcel) {
            parcel.writeInt(mLeftMils);
            parcel.writeInt(mTopMils);
            parcel.writeInt(mRightMils);
            parcel.writeInt(mBottomMils);
        }

        static Margins createFromParcel(Parcel parcel) {
            return new Margins(
                    parcel.readInt(),
                    parcel.readInt(),
                    parcel.readInt(),
                    parcel.readInt());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + mBottomMils;
            result = prime * result + mLeftMils;
            result = prime * result + mRightMils;
            result = prime * result + mTopMils;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Margins other = (Margins) obj;
            if (mBottomMils != other.mBottomMils) return false;
            if (mLeftMils != other.mLeftMils) return false;
            if (mRightMils != other.mRightMils) return false;
            if (mTopMils != other.mTopMils) return false;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Margins{");
            builder.append("leftMils: ").append(mLeftMils);
            builder.append(", topMils: ").append(mTopMils);
            builder.append(", rightMils: ").append(mRightMils);
            builder.append(", bottomMils: ").append(mBottomMils);
            builder.append("}");
            return builder.toString();
        }
    }

    static String colorModeToString(int colorMode) {
        switch (colorMode) {
            case COLOR_MODE_MONOCHROME: return "COLOR_MODE_MONOCHROME";
            case COLOR_MODE_COLOR: return "COLOR_MODE_COLOR";
            default: return "COLOR_MODE_UNKNOWN";
        }
    }

    static String duplexModeToString(int duplexMode) {
        switch (duplexMode) {
            case DUPLEX_MODE_NONE: return "DUPLEX_MODE_NONE";
            case DUPLEX_MODE_LONG_EDGE: return "DUPLEX_MODE_LONG_EDGE";
            case DUPLEX_MODE_SHORT_EDGE: return "DUPLEX_MODE_SHORT_EDGE";
            default: return "DUPLEX_MODE_UNKNOWN";
        }
    }

    static void enforceValidColorMode(int colorMode) {
        if ((colorMode & VALID_COLOR_MODES) == 0 || Integer.bitCount(colorMode) != 1) {
            throw new IllegalArgumentException("invalid color mode: " + colorMode);
        }
    }

    static void enforceValidDuplexMode(int duplexMode) {
        if ((duplexMode & VALID_DUPLEX_MODES) == 0 || Integer.bitCount(duplexMode) != 1) {
            throw new IllegalArgumentException("invalid duplex mode: " + duplexMode);
        }
    }

    /**
     * Builder for PrintAttributes. AOSP's builder mutates ONE instance and
     * build() hands that same instance back -- so a Builder reused after
     * build() keeps mutating the object it already returned. Reproduced
     * exactly; an app that relies on that aliasing behaves the same here.
     */
    public static final class Builder {
        private final PrintAttributes mAttributes = new PrintAttributes();

        public Builder() {}

        public Builder setMediaSize(MediaSize mediaSize) {
            mAttributes.setMediaSize(mediaSize);
            return this;
        }

        public Builder setResolution(Resolution resolution) {
            mAttributes.setResolution(resolution);
            return this;
        }

        public Builder setMinMargins(Margins margins) {
            mAttributes.setMinMargins(margins);
            return this;
        }

        public Builder setColorMode(int colorMode) {
            mAttributes.setColorMode(colorMode);
            return this;
        }

        public Builder setDuplexMode(int duplexMode) {
            mAttributes.setDuplexMode(duplexMode);
            return this;
        }

        public PrintAttributes build() {
            return mAttributes;
        }
    }

    public static final Parcelable.Creator<PrintAttributes> CREATOR =
            new Parcelable.Creator<PrintAttributes>() {
        @Override
        public PrintAttributes createFromParcel(Parcel parcel) {
            return new PrintAttributes(parcel);
        }

        @Override
        public PrintAttributes[] newArray(int size) {
            return new PrintAttributes[size];
        }
    };
}
