// android.security.AppUriAuthenticationPolicy -- the app+URI -> key-alias map a
// credential management app declares when it asks to manage the user's KeyChain
// credentials (KeyChain.createManageCredentialsIntent).
//
// AOSP source: frameworks/base/keystore/java/android/security/
// AppUriAuthenticationPolicy.java (android16-release). Public surface mirrored
// exactly: Builder.addAppAndUriMapping(pkg, uri, alias), build(),
// getAppAndUriMappings() returning pkg -> (uri -> alias), and Parcelable.
//
// AOSP stores the inner map in a @hide UrisToAliases helper and parcels it with
// writeMap. We keep the same public shape and parcel the nested map explicitly,
// which needs no hidden class and is symmetric by construction. The policy is a
// pure data object -- it authorises nothing by itself; on a device the SYSTEM
// decides whether to honour it, and here nothing does (see KeyChain).
package android.security;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AppUriAuthenticationPolicy implements Parcelable {

    private final Map<String, Map<Uri, String>> mAppToUris;

    private AppUriAuthenticationPolicy(Map<String, Map<Uri, String>> appToUris) {
        Objects.requireNonNull(appToUris);
        mAppToUris = appToUris;
    }

    /** pkg -> uri -> alias. A defensive copy, as on AOSP. */
    public Map<String, Map<Uri, String>> getAppAndUriMappings() {
        Map<String, Map<Uri, String>> copy = new HashMap<>();
        for (Map.Entry<String, Map<Uri, String>> e : mAppToUris.entrySet()) {
            copy.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return copy;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAppToUris.size());
        for (Map.Entry<String, Map<Uri, String>> e : mAppToUris.entrySet()) {
            dest.writeString(e.getKey());
            Map<Uri, String> uris = e.getValue();
            dest.writeInt(uris.size());
            for (Map.Entry<Uri, String> u : uris.entrySet()) {
                dest.writeTypedObject(u.getKey(), flags);
                dest.writeString(u.getValue());
            }
        }
    }

    @Override public String toString() {
        return "AppUriAuthenticationPolicy{mPackageNameToUris=" + mAppToUris + '}';
    }

    @Override public int hashCode() { return mAppToUris.hashCode(); }

    @Override public boolean equals(Object obj) {
        if (!(obj instanceof AppUriAuthenticationPolicy)) return false;
        return mAppToUris.equals(((AppUriAuthenticationPolicy) obj).mAppToUris);
    }

    public static final Parcelable.Creator<AppUriAuthenticationPolicy> CREATOR =
            new Parcelable.Creator<AppUriAuthenticationPolicy>() {
                @Override public AppUriAuthenticationPolicy createFromParcel(Parcel in) {
                    int apps = in.readInt();
                    Map<String, Map<Uri, String>> appToUris = new LinkedHashMap<>();
                    for (int i = 0; i < apps; i++) {
                        String pkg = in.readString();
                        int n = in.readInt();
                        Map<Uri, String> uris = new LinkedHashMap<>();
                        for (int j = 0; j < n; j++) {
                            Uri uri = in.readTypedObject(Uri.CREATOR);
                            uris.put(uri, in.readString());
                        }
                        appToUris.put(pkg, uris);
                    }
                    return new AppUriAuthenticationPolicy(appToUris);
                }
                @Override public AppUriAuthenticationPolicy[] newArray(int size) {
                    return new AppUriAuthenticationPolicy[size];
                }
            };

    public static final class Builder {
        private final Map<String, Map<Uri, String>> mPackageNameToUris = new LinkedHashMap<>();

        public Builder() {}

        /** AOSP: adding the same (pkg, uri) again OVERWRITES the previous alias. */
        public Builder addAppAndUriMapping(String appPackageName, Uri uri, String alias) {
            Objects.requireNonNull(appPackageName);
            Objects.requireNonNull(uri);
            Objects.requireNonNull(alias);
            Map<Uri, String> uris = mPackageNameToUris.get(appPackageName);
            if (uris == null) {
                uris = new LinkedHashMap<>();
                mPackageNameToUris.put(appPackageName, uris);
            }
            uris.put(uri, alias);
            return this;
        }

        public AppUriAuthenticationPolicy build() {
            return new AppUriAuthenticationPolicy(
                    Collections.unmodifiableMap(new LinkedHashMap<>(mPackageNameToUris)));
        }
    }
}
