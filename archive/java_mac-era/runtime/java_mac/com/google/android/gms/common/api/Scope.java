package com.google.android.gms.common.api;

import android.os.Parcel;
import android.os.Parcelable;

// com.google.android.gms.common.api.Scope -- an OAuth scope string requested by
// a GMS API (e.g. GoogleSignInOptions.requestScopes). Value-typed and
// Parcelable, matching the play-services public type.
public final class Scope implements Parcelable {

    private final String scopeUri;

    public Scope(String scopeUri) {
        if (scopeUri == null) throw new IllegalArgumentException("scopeUri must not be null or empty");
        this.scopeUri = scopeUri;
    }

    public String getScopeUri() { return scopeUri; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Scope)) return false;
        return scopeUri.equals(((Scope) o).scopeUri);
    }
    @Override public int hashCode() { return scopeUri.hashCode(); }
    @Override public String toString() { return scopeUri; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeString(scopeUri); }

    public static final Parcelable.Creator<Scope> CREATOR =
            new Parcelable.Creator<Scope>() {
        @Override public Scope createFromParcel(Parcel in) { return new Scope(in.readString()); }
        @Override public Scope[] newArray(int size) { return new Scope[size]; }
    };
}
