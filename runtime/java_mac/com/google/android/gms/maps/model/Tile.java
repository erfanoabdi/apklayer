// com.google.android.gms.maps.model.Tile -- a single map tile's pixel
// dimensions plus its encoded image bytes, returned by a TileProvider.
// Pure value object; the inert map never requests tiles, but the type
// must exist so apps that implement a custom TileProvider compile.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/Tile
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class Tile implements Parcelable {
    public final int width;
    public final int height;
    public final byte[] data;

    public Tile(int width, int height, byte[] data) {
        this.width = width;
        this.height = height;
        this.data = data;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeByteArray(data);
    }

    public static final Parcelable.Creator<Tile> CREATOR = new Parcelable.Creator<Tile>() {
        @Override public Tile createFromParcel(Parcel in) {
            int w = in.readInt();
            int h = in.readInt();
            return new Tile(w, h, in.createByteArray());
        }
        @Override public Tile[] newArray(int size) { return new Tile[size]; }
    };
}
