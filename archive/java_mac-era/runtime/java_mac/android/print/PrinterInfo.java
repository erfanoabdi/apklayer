// android.print.PrinterInfo
//
// A description of one printer: its PrinterId, display name, status,
// description, optional per-printer icon and its PrinterCapabilitiesInfo. A
// print service reports these through PrinterDiscoverySession.addPrinters.
// Mirrors frameworks/base/core/java/android/print/PrinterInfo.java
// (android16-release).
package android.print;

import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;

public final class PrinterInfo implements Parcelable {

    public static final int STATUS_IDLE = 1;
    public static final int STATUS_BUSY = 2;
    public static final int STATUS_UNAVAILABLE = 3;

    private final PrinterId mId;
    private final int mIconResourceId;
    private final boolean mHasCustomPrinterIcon;
    private final int mCustomPrinterIconGen;
    private final PendingIntent mInfoIntent;
    private final String mName;
    private final int mStatus;
    private final String mDescription;
    private final PrinterCapabilitiesInfo mCapabilities;

    private PrinterInfo(PrinterId printerId, String name, int status,
            int iconResourceId, boolean hasCustomPrinterIcon, String description,
            PendingIntent infoIntent, PrinterCapabilitiesInfo capabilities,
            int customPrinterIconGen) {
        mId = printerId;
        mName = name;
        mStatus = status;
        mIconResourceId = iconResourceId;
        mHasCustomPrinterIcon = hasCustomPrinterIcon;
        mDescription = description;
        mInfoIntent = infoIntent;
        mCapabilities = capabilities;
        mCustomPrinterIconGen = customPrinterIconGen;
    }

    public PrinterId getId() { return mId; }

    /**
     * @hide
     * AOSP's exact resolution order: a custom icon supplied by the print
     * service, else the per-printer drawable resource, else the print service
     * application's own icon.
     */
    public Drawable loadIcon(Context context) {
        Drawable drawable = null;
        PackageManager packageManager = context.getPackageManager();
        if (mHasCustomPrinterIcon) {
            PrintManager printManager = (PrintManager) context
                    .getSystemService(Context.PRINT_SERVICE);
            Icon icon = printManager.getCustomPrinterIcon(mId);
            if (icon != null) {
                drawable = icon.loadDrawable(context);
            }
        }
        if (drawable == null) {
            try {
                String packageName = mId.getServiceName().getPackageName();
                PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
                ApplicationInfo appInfo = packageInfo.applicationInfo;
                if (mIconResourceId != 0) {
                    drawable = packageManager.getDrawable(packageName, mIconResourceId, appInfo);
                }
                if (drawable == null && appInfo != null) {
                    drawable = appInfo.loadIcon(packageManager);
                }
            } catch (PackageManager.NameNotFoundException e) {
                /* ignore, as AOSP does */
            }
        }
        return drawable;
    }

    /** @hide */
    public boolean getHasCustomPrinterIcon() { return mHasCustomPrinterIcon; }

    public String getName() { return mName; }

    public int getStatus() { return mStatus; }

    public String getDescription() { return mDescription; }

    /** @hide */
    public PendingIntent getInfoIntent() { return mInfoIntent; }

    public PrinterCapabilitiesInfo getCapabilities() { return mCapabilities; }

    private static PrinterId checkPrinterId(PrinterId printerId) {
        if (printerId == null) {
            throw new NullPointerException("printerId cannot be null.");
        }
        return printerId;
    }

    private static int checkStatus(int status) {
        if (!(status == STATUS_IDLE
                || status == STATUS_BUSY
                || status == STATUS_UNAVAILABLE)) {
            throw new IllegalArgumentException("status is invalid.");
        }
        return status;
    }

    private static String checkName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be empty.");
        }
        return name;
    }

    private PrinterInfo(Parcel parcel) {
        mId = checkPrinterId(parcel.readParcelable(null, PrinterId.class));
        mName = checkName(parcel.readString());
        mStatus = checkStatus(parcel.readInt());
        mDescription = parcel.readString();
        mCapabilities = parcel.readParcelable(null, PrinterCapabilitiesInfo.class);
        mIconResourceId = parcel.readInt();
        mHasCustomPrinterIcon = parcel.readByte() != 0;
        mCustomPrinterIconGen = parcel.readInt();
        mInfoIntent = parcel.readParcelable(null, PendingIntent.class);
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(mId, flags);
        parcel.writeString(mName);
        parcel.writeInt(mStatus);
        parcel.writeString(mDescription);
        parcel.writeParcelable(mCapabilities, flags);
        parcel.writeInt(mIconResourceId);
        parcel.writeByte((byte) (mHasCustomPrinterIcon ? 1 : 0));
        parcel.writeInt(mCustomPrinterIconGen);
        parcel.writeParcelable(mInfoIntent, flags);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mId.hashCode();
        result = prime * result + mName.hashCode();
        result = prime * result + mStatus;
        result = prime * result + ((mDescription != null) ? mDescription.hashCode() : 0);
        result = prime * result + ((mCapabilities != null) ? mCapabilities.hashCode() : 0);
        result = prime * result + mIconResourceId;
        result = prime * result + (mHasCustomPrinterIcon ? 1 : 0);
        result = prime * result + mCustomPrinterIconGen;
        result = prime * result + ((mInfoIntent != null) ? mInfoIntent.hashCode() : 0);
        return result;
    }

    /** @hide Everything but the status, which changes constantly during discovery. */
    public boolean equalsIgnoringStatus(PrinterInfo other) {
        if (!mId.equals(other.mId)) return false;
        if (!mName.equals(other.mName)) return false;
        if (mDescription == null ? other.mDescription != null
                : !mDescription.equals(other.mDescription)) {
            return false;
        }
        if (mCapabilities == null) {
            if (other.mCapabilities != null) return false;
        } else if (!mCapabilities.equals(other.mCapabilities)) {
            return false;
        }
        if (mIconResourceId != other.mIconResourceId) return false;
        if (mHasCustomPrinterIcon != other.mHasCustomPrinterIcon) return false;
        if (mCustomPrinterIconGen != other.mCustomPrinterIconGen) return false;
        if (mInfoIntent == null) {
            if (other.mInfoIntent != null) return false;
        } else if (!mInfoIntent.equals(other.mInfoIntent)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PrinterInfo other = (PrinterInfo) obj;
        if (!equalsIgnoringStatus(other)) return false;
        if (mStatus != other.mStatus) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PrinterInfo{");
        builder.append("id=").append(mId);
        builder.append(", name=").append(mName);
        builder.append(", status=").append(mStatus);
        builder.append(", description=").append(mDescription);
        builder.append(", capabilities=").append(mCapabilities);
        builder.append(", iconResId=").append(mIconResourceId);
        builder.append(", hasCustomPrinterIcon=").append(mHasCustomPrinterIcon);
        builder.append(", customPrinterIconGen=").append(mCustomPrinterIconGen);
        builder.append(", infoIntent=").append(mInfoIntent);
        builder.append("\"}");
        return builder.toString();
    }

    public static final class Builder {
        private PrinterId mPrinterId;
        private String mName;
        private int mStatus;
        private int mIconResourceId;
        private boolean mHasCustomPrinterIcon;
        private String mDescription;
        private PendingIntent mInfoIntent;
        private PrinterCapabilitiesInfo mCapabilities;
        private int mCustomPrinterIconGen;

        public Builder(PrinterId printerId, String name, int status) {
            mPrinterId = checkPrinterId(printerId);
            mName = checkName(name);
            mStatus = checkStatus(status);
        }

        public Builder(PrinterInfo other) {
            mPrinterId = other.mId;
            mName = other.mName;
            mStatus = other.mStatus;
            mIconResourceId = other.mIconResourceId;
            mHasCustomPrinterIcon = other.mHasCustomPrinterIcon;
            mDescription = other.mDescription;
            mInfoIntent = other.mInfoIntent;
            mCapabilities = other.mCapabilities;
            mCustomPrinterIconGen = other.mCustomPrinterIconGen;
        }

        public Builder setStatus(int status) {
            mStatus = checkStatus(status);
            return this;
        }

        public Builder setIconResourceId(int iconResourceId) {
            if (iconResourceId < 0) {
                throw new IllegalArgumentException("iconResourceId can't be negative");
            }
            mIconResourceId = iconResourceId;
            return this;
        }

        public Builder setHasCustomPrinterIcon(boolean hasCustomPrinterIcon) {
            mHasCustomPrinterIcon = hasCustomPrinterIcon;
            return this;
        }

        public Builder setName(String name) {
            mName = checkName(name);
            return this;
        }

        public Builder setDescription(String description) {
            mDescription = description;
            return this;
        }

        public Builder setInfoIntent(PendingIntent infoIntent) {
            mInfoIntent = infoIntent;
            return this;
        }

        public Builder setCapabilities(PrinterCapabilitiesInfo capabilities) {
            mCapabilities = capabilities;
            return this;
        }

        public PrinterInfo build() {
            return new PrinterInfo(mPrinterId, mName, mStatus, mIconResourceId,
                    mHasCustomPrinterIcon, mDescription, mInfoIntent, mCapabilities,
                    mCustomPrinterIconGen);
        }

        /** @hide Bumps the generation so a re-fetch of the custom icon is forced. */
        public Builder incCustomPrinterIconGen() {
            mCustomPrinterIconGen++;
            return this;
        }
    }

    public static final Parcelable.Creator<PrinterInfo> CREATOR =
            new Parcelable.Creator<PrinterInfo>() {
        @Override
        public PrinterInfo createFromParcel(Parcel parcel) {
            return new PrinterInfo(parcel);
        }

        @Override
        public PrinterInfo[] newArray(int size) {
            return new PrinterInfo[size];
        }
    };
}
