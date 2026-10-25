package android.app;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
/** The representative colors of a wallpaper (primary/secondary/tertiary). Mac.apk
 *  sets no live wallpaper, so instances are only constructed explicitly; getters
 *  mirror AOSP. API 27. */
public final class WallpaperColors implements Parcelable {
    public static final int HINT_SUPPORTS_DARK_TEXT = 1 << 0;
    public static final int HINT_SUPPORTS_DARK_THEME = 1 << 1;
    private final Color primary, secondary, tertiary;
    private final int colorHints;
    public WallpaperColors(Color primary, Color secondary, Color tertiary) {
        this(primary, secondary, tertiary, 0);
    }
    /** AOSP WallpaperColors(Parcel) -- a PUBLIC constructor, not just a CREATOR
     *  hook (WallpaperColors.java:118). AOSP reads the main-colour list, then the
     *  all-colours histogram, then the hints. This class stores three named
     *  colours rather than AOSP's lists, so the wire shape it can honour is the
     *  one writeToParcel below produces -- which is the hints int. Reading it
     *  back keeps the pair symmetric; a parcel written by a real device is not
     *  something this process can receive (there is no Binder to receive it
     *  from). */
    public WallpaperColors(Parcel parcel) {
        this.primary = null;
        this.secondary = null;
        this.tertiary = null;
        this.colorHints = parcel != null ? parcel.readInt() : 0;
    }

    public WallpaperColors(Color primary, Color secondary, Color tertiary, int colorHints) {
        this.primary = primary; this.secondary = secondary; this.tertiary = tertiary; this.colorHints = colorHints;
    }
    public Color getPrimaryColor() { return primary; }
    public Color getSecondaryColor() { return secondary; }
    public Color getTertiaryColor() { return tertiary; }
    public int getColorHints() { return colorHints; }
    public static WallpaperColors fromBitmap(android.graphics.Bitmap bitmap) {
        return new WallpaperColors(Color.valueOf(Color.GRAY), null, null);
    }
    public static WallpaperColors fromDrawable(android.graphics.drawable.Drawable drawable) {
        return new WallpaperColors(Color.valueOf(Color.GRAY), null, null);
    }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeInt(colorHints); }
    public static final Parcelable.Creator<WallpaperColors> CREATOR = new Parcelable.Creator<WallpaperColors>() {
        @Override public WallpaperColors createFromParcel(Parcel in) {
            return new WallpaperColors(null, null, null, in.readInt());
        }
        @Override public WallpaperColors[] newArray(int size) { return new WallpaperColors[size]; }
    };
}
