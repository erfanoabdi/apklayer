// com.google.android.gms.maps.model.MapStyleOptions -- carries a JSON style
// string used to restyle the base map. The inert map does not render, so the
// JSON is stored but never applied; the type must exist so styled-map apps
// compile and GoogleMap.setMapStyle(...) accepts it.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/MapStyleOptions
package com.google.android.gms.maps.model;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

public final class MapStyleOptions implements Parcelable {
    private final String json;

    public MapStyleOptions(String json) {
        this.json = json;
    }

    public static MapStyleOptions loadRawResourceStyle(Context context, int resourceId) {
        // No raw-resource pipeline for a JSON style here; return an empty
        // style so callers get a non-null value (the inert map ignores it).
        return new MapStyleOptions("[]");
    }

    String getJson() { return json; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(json);
    }

    public static final Parcelable.Creator<MapStyleOptions> CREATOR =
            new Parcelable.Creator<MapStyleOptions>() {
        @Override public MapStyleOptions createFromParcel(Parcel in) {
            return new MapStyleOptions(in.readString());
        }
        @Override public MapStyleOptions[] newArray(int size) { return new MapStyleOptions[size]; }
    };
}
