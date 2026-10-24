package com.google.firebase.auth;

import android.net.Uri;

// UserProfileChangeRequest: builder the app uses to set displayName /
// photoUri on FirebaseUser.updateProfile. Faithful surface from the
// Firebase ref. We accept and store the values; updateProfile completes
// cleanly without mutating our stable anonymous user.
public final class UserProfileChangeRequest {
    private final String displayName;
    private final Uri photoUri;

    private UserProfileChangeRequest(Builder b) {
        this.displayName = b.displayName;
        this.photoUri = b.photoUri;
    }

    public String getDisplayName() { return displayName; }
    public Uri getPhotoUri() { return photoUri; }

    public static final class Builder {
        private String displayName;
        private Uri photoUri;

        public Builder() {}

        public Builder setDisplayName(String displayName) {
            this.displayName = displayName; return this;
        }
        public Builder setPhotoUri(Uri photoUri) {
            this.photoUri = photoUri; return this;
        }
        public UserProfileChangeRequest build() {
            return new UserProfileChangeRequest(this);
        }
    }
}
