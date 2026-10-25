// android.speech.RecognitionSupport (API 33) -- what a recognition service can
// do for a given request: which languages are supported on device, installed,
// pending download, or available online. AOSP:
// frameworks/base/core/java/android/speech/RecognitionSupport.java
package android.speech;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RecognitionSupport implements Parcelable {
    private final List<String> mInstalledOnDeviceLanguages;
    private final List<String> mPendingOnDeviceLanguages;
    private final List<String> mSupportedOnDeviceLanguages;
    private final List<String> mOnlineLanguages;

    private RecognitionSupport(List<String> installed, List<String> pending,
            List<String> supported, List<String> online) {
        mInstalledOnDeviceLanguages = Collections.unmodifiableList(new ArrayList<>(installed));
        mPendingOnDeviceLanguages = Collections.unmodifiableList(new ArrayList<>(pending));
        mSupportedOnDeviceLanguages = Collections.unmodifiableList(new ArrayList<>(supported));
        mOnlineLanguages = Collections.unmodifiableList(new ArrayList<>(online));
    }

    /** Languages whose on-device model is downloaded and ready. */
    public List<String> getInstalledOnDeviceLanguages() { return mInstalledOnDeviceLanguages; }

    /** Languages whose on-device model download has been requested. */
    public List<String> getPendingOnDeviceLanguages() { return mPendingOnDeviceLanguages; }

    /** Languages the service supports on device, downloaded or not. */
    public List<String> getSupportedOnDeviceLanguages() { return mSupportedOnDeviceLanguages; }

    /** Languages the service supports only with a network connection. */
    public List<String> getOnlineLanguages() { return mOnlineLanguages; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(mInstalledOnDeviceLanguages);
        dest.writeStringList(mPendingOnDeviceLanguages);
        dest.writeStringList(mSupportedOnDeviceLanguages);
        dest.writeStringList(mOnlineLanguages);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecognitionSupport)) return false;
        RecognitionSupport that = (RecognitionSupport) o;
        return mInstalledOnDeviceLanguages.equals(that.mInstalledOnDeviceLanguages)
                && mPendingOnDeviceLanguages.equals(that.mPendingOnDeviceLanguages)
                && mSupportedOnDeviceLanguages.equals(that.mSupportedOnDeviceLanguages)
                && mOnlineLanguages.equals(that.mOnlineLanguages);
    }

    @Override public int hashCode() {
        return Objects.hash(mInstalledOnDeviceLanguages, mPendingOnDeviceLanguages,
                mSupportedOnDeviceLanguages, mOnlineLanguages);
    }

    @Override public String toString() {
        return "RecognitionSupport { installedOnDeviceLanguages = " + mInstalledOnDeviceLanguages
                + ", pendingOnDeviceLanguages = " + mPendingOnDeviceLanguages
                + ", supportedOnDeviceLanguages = " + mSupportedOnDeviceLanguages
                + ", onlineLanguages = " + mOnlineLanguages + " }";
    }

    public static final Parcelable.Creator<RecognitionSupport> CREATOR =
            new Parcelable.Creator<RecognitionSupport>() {
        @Override public RecognitionSupport createFromParcel(Parcel in) {
            List<String> installed = new ArrayList<>();
            List<String> pending = new ArrayList<>();
            List<String> supported = new ArrayList<>();
            List<String> online = new ArrayList<>();
            in.readStringList(installed);
            in.readStringList(pending);
            in.readStringList(supported);
            in.readStringList(online);
            return new RecognitionSupport(installed, pending, supported, online);
        }
        @Override public RecognitionSupport[] newArray(int size) {
            return new RecognitionSupport[size];
        }
    };

    /** AOSP's builder. Every setter REPLACES the list; every adder appends. */
    public static final class Builder {
        private final List<String> mInstalled = new ArrayList<>();
        private final List<String> mPending = new ArrayList<>();
        private final List<String> mSupported = new ArrayList<>();
        private final List<String> mOnline = new ArrayList<>();

        public Builder() {}

        public Builder setInstalledOnDeviceLanguages(List<String> value) {
            Objects.requireNonNull(value);
            mInstalled.clear(); mInstalled.addAll(value); return this;
        }
        public Builder addInstalledOnDeviceLanguage(String value) {
            Objects.requireNonNull(value); mInstalled.add(value); return this;
        }
        public Builder setPendingOnDeviceLanguages(List<String> value) {
            Objects.requireNonNull(value);
            mPending.clear(); mPending.addAll(value); return this;
        }
        public Builder addPendingOnDeviceLanguage(String value) {
            Objects.requireNonNull(value); mPending.add(value); return this;
        }
        public Builder setSupportedOnDeviceLanguages(List<String> value) {
            Objects.requireNonNull(value);
            mSupported.clear(); mSupported.addAll(value); return this;
        }
        public Builder addSupportedOnDeviceLanguage(String value) {
            Objects.requireNonNull(value); mSupported.add(value); return this;
        }
        public Builder setOnlineLanguages(List<String> value) {
            Objects.requireNonNull(value);
            mOnline.clear(); mOnline.addAll(value); return this;
        }
        public Builder addOnlineLanguage(String value) {
            Objects.requireNonNull(value); mOnline.add(value); return this;
        }

        public RecognitionSupport build() {
            return new RecognitionSupport(mInstalled, mPending, mSupported, mOnline);
        }
    }
}
