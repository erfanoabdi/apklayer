package android.view.textclassifier;

/**
 * Context (package + widget type) for a text-classification request.
 * Source: frameworks/base/core/java/android/view/textclassifier/TextClassificationContext.java
 * (android16-release). AOSP declares `implements Parcelable`; the missing
 * `implements` is invisible to a member-level API diff but fails every type
 * check the context passes through (it is carried inside TextClassifierEvent).
 */
public final class TextClassificationContext implements android.os.Parcelable {

    private final String mPackageName;
    private final String mWidgetType;
    private String mWidgetVersion;

    private TextClassificationContext(String packageName, String widgetType) {
        mPackageName = packageName; mWidgetType = widgetType;
    }

    public String getPackageName() { return mPackageName; }
    public String getWidgetType() { return mWidgetType; }
    public String getWidgetVersion() { return mWidgetVersion; }

    @Override public String toString() {
        return "TextClassificationContext{packageName=" + mPackageName
                + ", widgetType=" + mWidgetType + ", widgetVersion=" + mWidgetVersion + "}";
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeString(mWidgetType);
        dest.writeString(mWidgetVersion);
    }
    public static final android.os.Parcelable.Creator<TextClassificationContext> CREATOR =
            new android.os.Parcelable.Creator<TextClassificationContext>() {
        @Override public TextClassificationContext createFromParcel(android.os.Parcel in) {
            TextClassificationContext c =
                    new TextClassificationContext(in.readString(), in.readString());
            c.mWidgetVersion = in.readString();
            return c;
        }
        @Override public TextClassificationContext[] newArray(int size) {
            return new TextClassificationContext[size];
        }
    };

    public static final class Builder {
        private final String mPackageName;
        private final String mWidgetType;
        private String mWidgetVersion;
        public Builder(String packageName, String widgetType) {
            mPackageName = packageName; mWidgetType = widgetType;
        }
        public Builder setWidgetVersion(String widgetVersion) {
            mWidgetVersion = widgetVersion; return this;
        }
        public TextClassificationContext build() {
            TextClassificationContext c = new TextClassificationContext(mPackageName, mWidgetType);
            c.mWidgetVersion = mWidgetVersion;
            return c;
        }
    }
}
