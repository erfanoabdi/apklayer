package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * AOSP-faithful android.graphics.ParcelableColorSpace (API 29). A thin
 * Parcelable wrapper around a ColorSpace. Real Android parcels named spaces by
 * id and RGB spaces by their transfer-function parameters; our ColorSpace is
 * sRGB-centric, so we parcel the name and rehydrate via ColorSpace.get(SRGB).
 * Mirrors frameworks/base/graphics/java/android/graphics/ParcelableColorSpace.java.
 */
public final class ParcelableColorSpace implements Parcelable {
    private final ColorSpace mColorSpace;

    public ParcelableColorSpace(ColorSpace colorSpace) {
        if (colorSpace == null) {
            throw new IllegalArgumentException("colorSpace must be non-null");
        }
        if (!isParcelable(colorSpace)) {
            throw new IllegalArgumentException(
                    "The color space must be either a named color space or a color "
                    + "space backed by an ICC profile");
        }
        mColorSpace = colorSpace;
    }

    public ColorSpace getColorSpace() {
        return mColorSpace;
    }

    public static boolean isParcelable(ColorSpace colorSpace) {
        // Our ColorSpace surface is limited to the Named spaces, all of which
        // are parcelable by id/name.
        return colorSpace != null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mColorSpace.getName());
    }

    public static final Parcelable.Creator<ParcelableColorSpace> CREATOR =
            new Parcelable.Creator<ParcelableColorSpace>() {
        @Override
        public ParcelableColorSpace createFromParcel(Parcel in) {
            in.readString();
            // We can only faithfully reconstruct the sRGB family our ColorSpace
            // exposes; return the canonical sRGB instance.
            return new ParcelableColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        }

        @Override
        public ParcelableColorSpace[] newArray(int size) {
            return new ParcelableColorSpace[size];
        }
    };
}
