// android.graphics.drawable.Icon -- API 23+ helper for representing
// an image source as either a resource id, a bitmap, a content URI,
// raw bytes, or a tinted variant. Per AOSP source at
// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/Icon.java
// each create*() returns an Icon configured with the source data;
// loadDrawable(Context) builds the corresponding Drawable.
package android.graphics.drawable;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

public class Icon implements android.os.Parcelable {
    public static final int TYPE_BITMAP   = 1;
    public static final int TYPE_RESOURCE = 2;
    public static final int TYPE_DATA     = 3;
    public static final int TYPE_URI      = 4;
    public static final int TYPE_ADAPTIVE_BITMAP = 5;
    public static final int TYPE_URI_ADAPTIVE_BITMAP = 6;

    private int type;
    private Bitmap bitmap;
    private int resId;
    private String resPackage;
    private byte[] data;
    private int dataOffset, dataLength;
    private Uri uri;
    private android.content.res.ColorStateList tintList;
    private android.graphics.PorterDuff.Mode tintMode;

    // ---- Parcelable (v1.0.943) -------------------------------------------
    // AOSP's Icon IS Parcelable (graphics/java/android/graphics/drawable/Icon.java);
    // ours was not, and that absence rippled: FIVE slots across Notification,
    // Notification.Action, Person, RemoteAction and BubbleMetadata had to write
    // an absent-marker instead of the icon, and the telecom lane had to do the
    // same in StatusHints / PhoneAccount / ChooserTarget. It also broke a build
    // outright when a lane wrote `writeParcelable(mIcon, 0)` against a type that
    // is not Parcelable here.
    //
    // AOSP's own wire shape (writeToParcel): the TYPE first, then a per-type
    // body, then the tint. We mirror that, with two honest deviations noted at
    // their sites.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(type);
        switch (type) {
            case TYPE_BITMAP:
            case TYPE_ADAPTIVE_BITMAP:
                // ⚠ Bitmap travels as a live reference in this tree (that is
                // Bitmap.writeToParcel's existing behaviour, not something this
                // change introduces), so an Icon carrying one does not survive a
                // cross-process hop. It survives an in-process round trip, which
                // is the only kind Mac.apk has.
                dest.writeParcelable(bitmap, flags);
                break;
            case TYPE_RESOURCE:
                dest.writeString(resPackage);
                dest.writeInt(resId);
                break;
            case TYPE_DATA:
                dest.writeInt(dataLength);
                dest.writeByteArray(data, dataOffset, dataLength);
                break;
            case TYPE_URI:
            case TYPE_URI_ADAPTIVE_BITMAP:
                dest.writeString(uri != null ? uri.toString() : null);
                break;
            default:
                break;   // an unset Icon writes its type and nothing else
        }
        // ⚠ ColorStateList is not Parcelable in this tree (it is on AOSP), so
        // the tint LIST cannot travel. We carry its default colour, which is the
        // part every consumer of an Icon tint actually reads, and a present-flag
        // so a null stays null. REMOVAL CONDITION: make ColorStateList
        // Parcelable, then swap these two lines for writeParcelable.
        dest.writeInt(tintList != null ? 1 : 0);
        if (tintList != null) dest.writeInt(tintList.getDefaultColor());
        dest.writeString(tintMode != null ? tintMode.name() : null);
    }

    public static final android.os.Parcelable.Creator<Icon> CREATOR =
            new android.os.Parcelable.Creator<Icon>() {
        @Override public Icon createFromParcel(android.os.Parcel in) {
            Icon i = new Icon(in.readInt());
            switch (i.type) {
                case TYPE_BITMAP:
                case TYPE_ADAPTIVE_BITMAP:
                    i.bitmap = in.readParcelable(Bitmap.class.getClassLoader());
                    break;
                case TYPE_RESOURCE:
                    i.resPackage = in.readString();
                    i.resId = in.readInt();
                    break;
                case TYPE_DATA: {
                    i.dataLength = in.readInt();
                    i.data = in.createByteArray();
                    // The reader always receives the bytes starting at 0, because
                    // the writer emitted only the [offset, offset+length) window.
                    i.dataOffset = 0;
                    break;
                }
                case TYPE_URI:
                case TYPE_URI_ADAPTIVE_BITMAP: {
                    String s = in.readString();
                    i.uri = (s != null) ? Uri.parse(s) : null;
                    break;
                }
                default: break;
            }
            if (in.readInt() != 0) {
                i.tintList = android.content.res.ColorStateList.valueOf(in.readInt());
            }
            String mode = in.readString();
            if (mode != null) {
                try { i.tintMode = android.graphics.PorterDuff.Mode.valueOf(mode); }
                catch (IllegalArgumentException ignored) { }
            }
            return i;
        }
        @Override public Icon[] newArray(int size) { return new Icon[size]; }
    };

    public Icon() {}
    private Icon(int type) { this.type = type; }

    public static Icon createWithBitmap(Bitmap bits) {
        Icon i = new Icon(TYPE_BITMAP); i.bitmap = bits; return i;
    }
    public static Icon createWithAdaptiveBitmap(Bitmap bits) {
        Icon i = new Icon(TYPE_ADAPTIVE_BITMAP); i.bitmap = bits; return i;
    }
    public static Icon createWithAdaptiveBitmapContentUri(String uri) {
        Icon i = new Icon(TYPE_URI_ADAPTIVE_BITMAP); i.uri = Uri.parse(uri); return i;
    }
    public static Icon createWithAdaptiveBitmapContentUri(Uri uri) {
        Icon i = new Icon(TYPE_URI_ADAPTIVE_BITMAP); i.uri = uri; return i;
    }
    public static Icon createWithResource(Context context, int resId) {
        Icon i = new Icon(TYPE_RESOURCE);
        i.resId = resId;
        if (context != null) i.resPackage = context.getPackageName();
        return i;
    }
    public static Icon createWithResource(String resPackage, int resId) {
        Icon i = new Icon(TYPE_RESOURCE); i.resId = resId; i.resPackage = resPackage; return i;
    }
    public static Icon createWithData(byte[] data, int offset, int length) {
        Icon i = new Icon(TYPE_DATA); i.data = data; i.dataOffset = offset; i.dataLength = length;
        return i;
    }
    public static Icon createWithContentUri(String uri) {
        Icon i = new Icon(TYPE_URI); i.uri = Uri.parse(uri); return i;
    }
    public static Icon createWithContentUri(Uri uri) {
        Icon i = new Icon(TYPE_URI); i.uri = uri; return i;
    }
    /** AOSP Icon.createWithFilePath(String) (Icon.java:990). Note it produces a
     *  TYPE_URI icon whose string is the PATH, not a content:// uri, and it
     *  throws IllegalArgumentException (not NPE) on null. */
    public static Icon createWithFilePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null.");
        }
        Icon i = new Icon(TYPE_URI);
        i.uri = Uri.parse("file://" + path);
        return i;
    }

    public int    getType()       { return type; }
    public int    getResId()      { return resId; }
    public String getResPackage() { return resPackage; }
    public Uri    getUri()        { return uri; }
    public Bitmap getBitmap()     { return bitmap; }
    public byte[] getDataBytes()  { return data; }
    public int    getDataOffset() { return dataOffset; }
    public int    getDataLength() { return dataLength; }

    public Icon setTint(int color) {
        this.tintList = android.content.res.ColorStateList.valueOf(color);
        return this;
    }
    public Icon setTintList(android.content.res.ColorStateList tintList) {
        this.tintList = tintList; return this;
    }
    public android.content.res.ColorStateList getTintList() { return tintList; }
    public Icon setTintMode(android.graphics.PorterDuff.Mode mode) {
        this.tintMode = mode; return this;
    }
    public android.graphics.PorterDuff.Mode getTintMode() { return tintMode; }
    /** AOSP Icon.setTintBlendMode(BlendMode) (API 29) -- the BlendMode-flavoured
     *  spelling of setTintMode. AOSP stores the BlendMode and converts when it
     *  applies the tint; we keep the PorterDuff slot as the single source of
     *  truth and convert HERE, so both setters and both getters stay coherent
     *  instead of one silently shadowing the other. Returns `this` for chaining,
     *  like every other Icon setter. */
    public Icon setTintBlendMode(android.graphics.BlendMode mode) {
        this.tintMode = (mode == null) ? null
                : mode.toPorterDuffModeOrNull();
        return this;
    }

    /** AOSP: synchronously load the resource into a Drawable.
     *  Used by NotificationCompat / ImageView.setImageIcon to
     *  resolve the underlying Drawable. */
    public Drawable loadDrawable(Context context) {
        switch (type) {
            case TYPE_BITMAP:
            case TYPE_ADAPTIVE_BITMAP:
                if (bitmap != null) {
                    BitmapDrawable bd = new BitmapDrawable(
                        context != null ? context.getResources() : null, bitmap);
                    applyTint(bd);
                    return bd;
                }
                return null;
            case TYPE_RESOURCE:
                try {
                    if (context != null && resId != 0) {
                        Drawable d = context.getResources().getDrawable(resId);
                        applyTint(d);
                        return d;
                    }
                } catch (Throwable ignored) {}
                return null;
            case TYPE_DATA:
                if (data != null) {
                    Bitmap bm = BitmapFactory.decodeByteArray(data, dataOffset, dataLength);
                    if (bm != null) {
                        BitmapDrawable bd = new BitmapDrawable(
                            context != null ? context.getResources() : null, bm);
                        applyTint(bd);
                        return bd;
                    }
                }
                return null;
            case TYPE_URI:
            case TYPE_URI_ADAPTIVE_BITMAP:
                if (uri != null && context != null) {
                    java.io.InputStream is = null;
                    try {
                        is = context.getContentResolver().openInputStream(uri);
                        if (is == null && uri.getPath() != null) {
                            is = new java.io.FileInputStream(uri.getPath());
                        }
                        if (is != null) {
                            Bitmap bm = BitmapFactory.decodeStream(is);
                            if (bm != null) {
                                BitmapDrawable bd = new BitmapDrawable(context.getResources(), bm);
                                applyTint(bd);
                                return bd;
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        try { if (is != null) is.close(); } catch (Throwable ignored) {}
                    }
                }
                return null;
            default:
                return null;
        }
    }

    private void applyTint(Drawable d) {
        if (d == null) return;
        if (tintList != null) try { d.setTintList(tintList); } catch (Throwable ignored) {}
        if (tintMode != null) try { d.setTintMode(tintMode); } catch (Throwable ignored) {}
    }

    public interface OnDrawableLoadedListener {
        void onDrawableLoaded(Drawable d);
    }
    /** AOSP loadDrawableAsync(Context, OnDrawableLoadedListener, Handler) --
     *  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/graphics/java/android/graphics/drawable/Icon.java
     *  declares the listener second and the Handler third. Java resolves
     *  overloads by the full parameter-type sequence, so a real app calling
     *  the AOSP-ordered descriptor does not bind to a (Context, Handler,
     *  Listener)-ordered method at all -- NoSuchMethodError, not a silent
     *  mismatch. We do the load synchronously on the current thread; close
     *  enough given there is no real async decode pipeline underneath. */
    public void loadDrawableAsync(Context context, final OnDrawableLoadedListener listener,
                                  android.os.Handler handler) {
        Drawable d = loadDrawable(context);
        if (listener != null) listener.onDrawableLoaded(d);
    }
    /** Old-order overload. Not an AOSP signature (real AOSP never declares
     *  Handler before Listener here) but a distinct parameter-type sequence
     *  from the one above, so keeping it alongside costs nothing and is not
     *  what any real APK's bytecode was compiled against either way; no
     *  caller in this tree depends on it (grepped). Left in place rather
     *  than removed to avoid touching call sites outside this file's
     *  ownership on the chance one exists elsewhere in a future edit. */
    public void loadDrawableAsync(Context context, android.os.Handler handler,
                                  final OnDrawableLoadedListener listener) {
        Drawable d = loadDrawable(context);
        if (listener != null) listener.onDrawableLoaded(d);
    }
    public void loadDrawableAsync(Context context, android.os.Message message) {}

    @Override public String toString() {
        return "Icon(type=" + type + ", resId=0x" + Integer.toHexString(resId) + ")";
    }
}
