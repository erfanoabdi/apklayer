// android.media.MediaDescription -- a lightweight description of a media item
// (title, subtitle, art, media id). AOSP frameworks/base
// media/java/android/media/MediaDescription.java. Immutable bag built via
// Builder; Parcelable. Fully serviceable as a pure data holder on Mac.apk.
package android.media;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class MediaDescription implements android.os.Parcelable {
    private final String mMediaId;
    private final CharSequence mTitle;
    private final CharSequence mSubtitle;
    private final CharSequence mDescription;
    private final android.graphics.Bitmap mIcon;
    private final android.net.Uri mIconUri;
    private final android.os.Bundle mExtras;
    private final android.net.Uri mMediaUri;

    private MediaDescription(String mediaId, CharSequence title, CharSequence subtitle,
                             CharSequence description, android.graphics.Bitmap icon,
                             android.net.Uri iconUri, android.os.Bundle extras,
                             android.net.Uri mediaUri) {
        mMediaId = mediaId;
        mTitle = title;
        mSubtitle = subtitle;
        mDescription = description;
        mIcon = icon;
        mIconUri = iconUri;
        mExtras = extras;
        mMediaUri = mediaUri;
    }

    public String getMediaId() { return mMediaId; }
    public CharSequence getTitle() { return mTitle; }
    public CharSequence getSubtitle() { return mSubtitle; }
    public CharSequence getDescription() { return mDescription; }
    public android.graphics.Bitmap getIconBitmap() { return mIcon; }
    public android.net.Uri getIconUri() { return mIconUri; }
    public android.os.Bundle getExtras() { return mExtras; }
    public android.net.Uri getMediaUri() { return mMediaUri; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mMediaId);
        dest.writeString(mTitle != null ? mTitle.toString() : null);
        dest.writeString(mSubtitle != null ? mSubtitle.toString() : null);
        dest.writeString(mDescription != null ? mDescription.toString() : null);
    }
    @Override public String toString() {
        return mTitle + ", " + mSubtitle + ", " + mDescription;
    }
    public static final android.os.Parcelable.Creator<MediaDescription> CREATOR =
        new android.os.Parcelable.Creator<MediaDescription>() {
            @Override public MediaDescription createFromParcel(android.os.Parcel in) {
                return new Builder()
                    .setMediaId(in.readString())
                    .setTitle(in.readString())
                    .setSubtitle(in.readString())
                    .setDescription(in.readString())
                    .build();
            }
            @Override public MediaDescription[] newArray(int size) {
                return new MediaDescription[size];
            }
        };

    // AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
    // mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
    // app compiled against real Android that subclasses this fails to resolve
    // against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
    public static class Builder {
        private String mediaId;
        private CharSequence title;
        private CharSequence subtitle;
        private CharSequence description;
        private android.graphics.Bitmap icon;
        private android.net.Uri iconUri;
        private android.os.Bundle extras;
        private android.net.Uri mediaUri;
        public Builder() {}
        public Builder setMediaId(String mediaId) { this.mediaId = mediaId; return this; }
        public Builder setTitle(CharSequence title) { this.title = title; return this; }
        public Builder setSubtitle(CharSequence subtitle) { this.subtitle = subtitle; return this; }
        public Builder setDescription(CharSequence description) { this.description = description; return this; }
        public Builder setIconBitmap(android.graphics.Bitmap icon) { this.icon = icon; return this; }
        public Builder setIconUri(android.net.Uri iconUri) { this.iconUri = iconUri; return this; }
        public Builder setExtras(android.os.Bundle extras) { this.extras = extras; return this; }
        public Builder setMediaUri(android.net.Uri mediaUri) { this.mediaUri = mediaUri; return this; }
        public MediaDescription build() {
            return new MediaDescription(mediaId, title, subtitle, description,
                                        icon, iconUri, extras, mediaUri);
        }
    }
}
