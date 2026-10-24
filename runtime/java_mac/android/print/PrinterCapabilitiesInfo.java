// android.print.PrinterCapabilitiesInfo
//
// What a printer can do: the media sizes and resolutions it supports, its
// colour and duplex mode masks, its minimum margins, and which of each is the
// default. Built by a print service through PrinterInfo.Builder.setCapabilities
// and read by the print UI to constrain the PrintAttributes a job may use.
// Mirrors frameworks/base/core/java/android/print/PrinterCapabilitiesInfo.java
// (android16-release).
package android.print;

import android.os.Parcel;
import android.os.Parcelable;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PrinterCapabilitiesInfo implements Parcelable {

    /** @hide */
    public static final int DEFAULT_UNDEFINED = -1;

    private static final int PROPERTY_MEDIA_SIZE = 0;
    private static final int PROPERTY_RESOLUTION = 1;
    private static final int PROPERTY_COLOR_MODE = 2;
    private static final int PROPERTY_DUPLEX_MODE = 3;
    private static final int PROPERTY_COUNT = 4;

    private static final Margins DEFAULT_MARGINS = new Margins(0, 0, 0, 0);

    private Margins mMinMargins = DEFAULT_MARGINS;
    private List<MediaSize> mMediaSizes;
    private List<Resolution> mResolutions;

    private int mColorModes;
    private int mDuplexModes;

    private final int[] mDefaults = new int[PROPERTY_COUNT];

    /** @hide */
    public PrinterCapabilitiesInfo() {
        Arrays.fill(mDefaults, DEFAULT_UNDEFINED);
    }

    /** @hide */
    public PrinterCapabilitiesInfo(PrinterCapabilitiesInfo prototype) {
        Arrays.fill(mDefaults, DEFAULT_UNDEFINED);
        copyFrom(prototype);
    }

    /** @hide */
    public void copyFrom(PrinterCapabilitiesInfo other) {
        if (this == other) return;
        mMinMargins = other.mMinMargins;
        if (other.mMediaSizes != null) {
            if (mMediaSizes != null) {
                mMediaSizes.clear();
                mMediaSizes.addAll(other.mMediaSizes);
            } else {
                mMediaSizes = new ArrayList<MediaSize>(other.mMediaSizes);
            }
        } else {
            mMediaSizes = null;
        }
        if (other.mResolutions != null) {
            if (mResolutions != null) {
                mResolutions.clear();
                mResolutions.addAll(other.mResolutions);
            } else {
                mResolutions = new ArrayList<Resolution>(other.mResolutions);
            }
        } else {
            mResolutions = null;
        }
        mColorModes = other.mColorModes;
        mDuplexModes = other.mDuplexModes;
        final int defaultCount = other.mDefaults.length;
        for (int i = 0; i < defaultCount; i++) {
            mDefaults[i] = other.mDefaults[i];
        }
    }

    public List<MediaSize> getMediaSizes() {
        return Collections.unmodifiableList(mMediaSizes);
    }

    public List<Resolution> getResolutions() {
        return Collections.unmodifiableList(mResolutions);
    }

    public Margins getMinMargins() { return mMinMargins; }

    public int getColorModes() { return mColorModes; }

    public int getDuplexModes() { return mDuplexModes; }

    public PrintAttributes getDefaults() {
        PrintAttributes.Builder builder = new PrintAttributes.Builder();
        builder.setMinMargins(mMinMargins);
        final int mediaSizeIndex = mDefaults[PROPERTY_MEDIA_SIZE];
        if (mediaSizeIndex >= 0) {
            builder.setMediaSize(mMediaSizes.get(mediaSizeIndex));
        }
        final int resolutionIndex = mDefaults[PROPERTY_RESOLUTION];
        if (resolutionIndex >= 0) {
            builder.setResolution(mResolutions.get(resolutionIndex));
        }
        final int colorMode = mDefaults[PROPERTY_COLOR_MODE];
        if (colorMode > 0) {
            builder.setColorMode(colorMode);
        }
        final int duplexMode = mDefaults[PROPERTY_DUPLEX_MODE];
        if (duplexMode > 0) {
            builder.setDuplexMode(duplexMode);
        }
        return builder.build();
    }

    // AOSP validates a MASK by walking its set bits and validating each one on
    // its own -- a mask is legal, a multi-bit VALUE is not.
    private static void enforceValidColorModeMask(int mask) {
        int current = mask;
        while (current != 0) {
            final int currentMode = (1 << Integer.numberOfTrailingZeros(current));
            current &= ~currentMode;
            PrintAttributes.enforceValidColorMode(currentMode);
        }
    }

    private static void enforceValidDuplexModeMask(int mask) {
        int current = mask;
        while (current != 0) {
            final int currentMode = (1 << Integer.numberOfTrailingZeros(current));
            current &= ~currentMode;
            PrintAttributes.enforceValidDuplexMode(currentMode);
        }
    }

    private PrinterCapabilitiesInfo(Parcel parcel) {
        Arrays.fill(mDefaults, DEFAULT_UNDEFINED);
        mMinMargins = readMargins(parcel);
        if (mMinMargins == null) {
            throw new IllegalArgumentException("margins cannot be null");
        }
        readMediaSizes(parcel);
        readResolutions(parcel);
        mColorModes = parcel.readInt();
        enforceValidColorModeMask(mColorModes);
        mDuplexModes = parcel.readInt();
        enforceValidDuplexModeMask(mDuplexModes);
        readDefaults(parcel);
        if (mMediaSizes == null || mMediaSizes.size() <= mDefaults[PROPERTY_MEDIA_SIZE]) {
            throw new IllegalArgumentException("default media size out of range");
        }
        if (mResolutions == null || mResolutions.size() <= mDefaults[PROPERTY_RESOLUTION]) {
            throw new IllegalArgumentException("default resolution out of range");
        }
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        writeMargins(mMinMargins, parcel);
        writeMediaSizes(parcel);
        writeResolutions(parcel);
        parcel.writeInt(mColorModes);
        parcel.writeInt(mDuplexModes);
        writeDefaults(parcel);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mMinMargins == null) ? 0 : mMinMargins.hashCode());
        result = prime * result + ((mMediaSizes == null) ? 0 : mMediaSizes.hashCode());
        result = prime * result + ((mResolutions == null) ? 0 : mResolutions.hashCode());
        result = prime * result + mColorModes;
        result = prime * result + mDuplexModes;
        result = prime * result + Arrays.hashCode(mDefaults);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PrinterCapabilitiesInfo other = (PrinterCapabilitiesInfo) obj;
        if (mMinMargins == null) {
            if (other.mMinMargins != null) return false;
        } else if (!mMinMargins.equals(other.mMinMargins)) {
            return false;
        }
        if (mMediaSizes == null) {
            if (other.mMediaSizes != null) return false;
        } else if (!mMediaSizes.equals(other.mMediaSizes)) {
            return false;
        }
        if (mResolutions == null) {
            if (other.mResolutions != null) return false;
        } else if (!mResolutions.equals(other.mResolutions)) {
            return false;
        }
        if (mColorModes != other.mColorModes) return false;
        if (mDuplexModes != other.mDuplexModes) return false;
        if (!Arrays.equals(mDefaults, other.mDefaults)) return false;
        return true;
    }

    // AOSP really does open this with "PrinterInfo{" rather than
    // "PrinterCapabilitiesInfo{". Kept verbatim -- a log-scraping app or test
    // sees the same text a device produces.
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PrinterInfo{");
        builder.append("minMargins=").append(mMinMargins);
        builder.append(", mediaSizes=").append(mMediaSizes);
        builder.append(", resolutions=").append(mResolutions);
        builder.append(", colorModes=").append(colorModesToString());
        builder.append(", duplexModes=").append(duplexModesToString());
        builder.append("\"}");
        return builder.toString();
    }

    private String colorModesToString() {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        int colorModes = mColorModes;
        while (colorModes != 0) {
            final int colorMode = 1 << Integer.numberOfTrailingZeros(colorModes);
            colorModes &= ~colorMode;
            if (builder.length() > 1) builder.append(", ");
            builder.append(PrintAttributes.colorModeToString(colorMode));
        }
        builder.append(']');
        return builder.toString();
    }

    private String duplexModesToString() {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        int duplexModes = mDuplexModes;
        while (duplexModes != 0) {
            final int duplexMode = 1 << Integer.numberOfTrailingZeros(duplexModes);
            duplexModes &= ~duplexMode;
            if (builder.length() > 1) builder.append(", ");
            builder.append(PrintAttributes.duplexModeToString(duplexMode));
        }
        builder.append(']');
        return builder.toString();
    }

    private void writeMediaSizes(Parcel parcel) {
        if (mMediaSizes == null) {
            parcel.writeInt(0);
            return;
        }
        final int mediaSizeCount = mMediaSizes.size();
        parcel.writeInt(mediaSizeCount);
        for (int i = 0; i < mediaSizeCount; i++) {
            mMediaSizes.get(i).writeToParcel(parcel);
        }
    }

    private void readMediaSizes(Parcel parcel) {
        final int mediaSizeCount = parcel.readInt();
        if (mediaSizeCount > 0 && mMediaSizes == null) {
            mMediaSizes = new ArrayList<MediaSize>();
        }
        for (int i = 0; i < mediaSizeCount; i++) {
            mMediaSizes.add(MediaSize.createFromParcel(parcel));
        }
    }

    private void writeResolutions(Parcel parcel) {
        if (mResolutions == null) {
            parcel.writeInt(0);
            return;
        }
        final int resolutionCount = mResolutions.size();
        parcel.writeInt(resolutionCount);
        for (int i = 0; i < resolutionCount; i++) {
            mResolutions.get(i).writeToParcel(parcel);
        }
    }

    private void readResolutions(Parcel parcel) {
        final int resolutionCount = parcel.readInt();
        if (resolutionCount > 0 && mResolutions == null) {
            mResolutions = new ArrayList<Resolution>();
        }
        for (int i = 0; i < resolutionCount; i++) {
            mResolutions.add(Resolution.createFromParcel(parcel));
        }
    }

    private void writeMargins(Margins margins, Parcel parcel) {
        if (margins == null) {
            parcel.writeInt(0);
        } else {
            parcel.writeInt(1);
            margins.writeToParcel(parcel);
        }
    }

    private Margins readMargins(Parcel parcel) {
        return (parcel.readInt() == 1) ? Margins.createFromParcel(parcel) : null;
    }

    private void readDefaults(Parcel parcel) {
        final int defaultCount = parcel.readInt();
        for (int i = 0; i < defaultCount && i < mDefaults.length; i++) {
            mDefaults[i] = parcel.readInt();
        }
    }

    private void writeDefaults(Parcel parcel) {
        final int defaultCount = mDefaults.length;
        parcel.writeInt(defaultCount);
        for (int i = 0; i < defaultCount; i++) {
            parcel.writeInt(mDefaults[i]);
        }
    }

    public static final class Builder {
        private final PrinterCapabilitiesInfo mPrototype;

        /**
         * AOSP validates printerId is non-null and then does NOT store it --
         * the capabilities travel inside a PrinterInfo that already carries the
         * id. Reproduced, including the unused parameter.
         */
        public Builder(PrinterId printerId) {
            if (printerId == null) {
                throw new IllegalArgumentException("printerId cannot be null.");
            }
            mPrototype = new PrinterCapabilitiesInfo();
        }

        public Builder addMediaSize(MediaSize mediaSize, boolean isDefault) {
            if (mPrototype.mMediaSizes == null) {
                mPrototype.mMediaSizes = new ArrayList<MediaSize>();
            }
            final int insertionIndex = mPrototype.mMediaSizes.size();
            mPrototype.mMediaSizes.add(mediaSize);
            if (isDefault) {
                throwIfDefaultAlreadySpecified(PROPERTY_MEDIA_SIZE);
                mPrototype.mDefaults[PROPERTY_MEDIA_SIZE] = insertionIndex;
            }
            return this;
        }

        public Builder addResolution(Resolution resolution, boolean isDefault) {
            if (mPrototype.mResolutions == null) {
                mPrototype.mResolutions = new ArrayList<Resolution>();
            }
            final int insertionIndex = mPrototype.mResolutions.size();
            mPrototype.mResolutions.add(resolution);
            if (isDefault) {
                throwIfDefaultAlreadySpecified(PROPERTY_RESOLUTION);
                mPrototype.mDefaults[PROPERTY_RESOLUTION] = insertionIndex;
            }
            return this;
        }

        public Builder setMinMargins(Margins margins) {
            if (margins == null) {
                throw new IllegalArgumentException("margins cannot be null");
            }
            mPrototype.mMinMargins = margins;
            return this;
        }

        public Builder setColorModes(int colorModes, int defaultColorMode) {
            enforceValidColorModeMask(colorModes);
            PrintAttributes.enforceValidColorMode(defaultColorMode);
            mPrototype.mColorModes = colorModes;
            mPrototype.mDefaults[PROPERTY_COLOR_MODE] = defaultColorMode;
            return this;
        }

        public Builder setDuplexModes(int duplexModes, int defaultDuplexMode) {
            enforceValidDuplexModeMask(duplexModes);
            PrintAttributes.enforceValidDuplexMode(defaultDuplexMode);
            mPrototype.mDuplexModes = duplexModes;
            mPrototype.mDefaults[PROPERTY_DUPLEX_MODE] = defaultDuplexMode;
            return this;
        }

        public PrinterCapabilitiesInfo build() {
            if (mPrototype.mMediaSizes == null || mPrototype.mMediaSizes.isEmpty()) {
                throw new IllegalStateException("No media size specified.");
            }
            if (mPrototype.mDefaults[PROPERTY_MEDIA_SIZE] == DEFAULT_UNDEFINED) {
                throw new IllegalStateException("No default media size specified.");
            }
            if (mPrototype.mResolutions == null || mPrototype.mResolutions.isEmpty()) {
                throw new IllegalStateException("No resolution specified.");
            }
            if (mPrototype.mDefaults[PROPERTY_RESOLUTION] == DEFAULT_UNDEFINED) {
                throw new IllegalStateException("No default resolution specified.");
            }
            if (mPrototype.mColorModes == 0) {
                throw new IllegalStateException("No color mode specified.");
            }
            if (mPrototype.mDefaults[PROPERTY_COLOR_MODE] == DEFAULT_UNDEFINED) {
                throw new IllegalStateException("No default color mode specified.");
            }
            if (mPrototype.mDuplexModes == 0) {
                setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE,
                        PrintAttributes.DUPLEX_MODE_NONE);
            }
            if (mPrototype.mMinMargins == null) {
                throw new IllegalArgumentException("margins cannot be null");
            }
            return mPrototype;
        }

        private void throwIfDefaultAlreadySpecified(int propertyIndex) {
            if (mPrototype.mDefaults[propertyIndex] != DEFAULT_UNDEFINED) {
                throw new IllegalArgumentException("Default already specified.");
            }
        }
    }

    public static final Parcelable.Creator<PrinterCapabilitiesInfo> CREATOR =
            new Parcelable.Creator<PrinterCapabilitiesInfo>() {
        @Override
        public PrinterCapabilitiesInfo createFromParcel(Parcel parcel) {
            return new PrinterCapabilitiesInfo(parcel);
        }

        @Override
        public PrinterCapabilitiesInfo[] newArray(int size) {
            return new PrinterCapabilitiesInfo[size];
        }
    };
}
