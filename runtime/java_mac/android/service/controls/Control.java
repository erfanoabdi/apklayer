package android.service.controls;

import android.app.PendingIntent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.controls.templates.ControlTemplate;
import android.text.TextUtils;

/**
 * One device a ControlsProviderService offers to the system's device-controls
 * panel: its identity and appearance (the "stateless" half) plus its current
 * status and interactive template (the "stateful" half).
 *
 * The two builders exist because AOSP publishes controls in two passes -- a
 * cheap list of what exists, then a live-updating stream of state. See the
 * package note on DeviceTypes.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   STATUS_UNKNOWN=0, STATUS_OK=1, STATUS_NOT_FOUND=2, STATUS_ERROR=3,
 *   STATUS_DISABLED=4.
 *
 * Source: frameworks/base/core/java/android/service/controls/Control.java
 */
public final class Control implements Parcelable {

    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_OK = 1;
    public static final int STATUS_NOT_FOUND = 2;
    public static final int STATUS_ERROR = 3;
    public static final int STATUS_DISABLED = 4;

    private final String mControlId;
    private final int mDeviceType;
    private final CharSequence mTitle;
    private final CharSequence mSubtitle;
    private final CharSequence mStructure;
    private final CharSequence mZone;
    private final PendingIntent mAppIntent;
    private final Icon mCustomIcon;
    private final ColorStateList mCustomColor;
    private final int mStatus;
    private final ControlTemplate mControlTemplate;
    private final CharSequence mStatusText;
    private final boolean mAuthRequired;

    private Control(String controlId, int deviceType, CharSequence title,
            CharSequence subtitle, CharSequence structure, CharSequence zone,
            PendingIntent appIntent, Icon customIcon, ColorStateList customColor,
            int status, ControlTemplate controlTemplate, CharSequence statusText,
            boolean authRequired) {
        mControlId = controlId;
        mDeviceType = deviceType;
        mTitle = title;
        mSubtitle = subtitle;
        mStructure = structure;
        mZone = zone;
        mAppIntent = appIntent;
        mCustomIcon = customIcon;
        mCustomColor = customColor;
        mStatus = status;
        mControlTemplate = controlTemplate;
        mStatusText = statusText;
        mAuthRequired = authRequired;
    }

    public String getControlId() { return mControlId; }

    public int getDeviceType() { return mDeviceType; }

    public CharSequence getTitle() { return mTitle; }

    public CharSequence getSubtitle() { return mSubtitle; }

    public CharSequence getStructure() { return mStructure; }

    public CharSequence getZone() { return mZone; }

    public PendingIntent getAppIntent() { return mAppIntent; }

    public Icon getCustomIcon() { return mCustomIcon; }

    public ColorStateList getCustomColor() { return mCustomColor; }

    public int getStatus() { return mStatus; }

    public ControlTemplate getControlTemplate() { return mControlTemplate; }

    public CharSequence getStatusText() { return mStatusText; }

    /** API 31. True when the panel must authenticate the user before acting. */
    public boolean isAuthRequired() { return mAuthRequired; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mControlId);
        dest.writeInt(mDeviceType);
        TextUtils.writeToParcel(mTitle, dest, flags);
        TextUtils.writeToParcel(mSubtitle, dest, flags);
        TextUtils.writeToParcel(mStructure, dest, flags);
        TextUtils.writeToParcel(mZone, dest, flags);
        dest.writeTypedObject(mAppIntent, flags);
        dest.writeTypedObject(mCustomIcon, flags);
        dest.writeTypedObject(mCustomColor, flags);
        dest.writeInt(mStatus);
        // ControlTemplate is NOT Parcelable in AOSP's public API; the panel
        // reconstructs it from a Bundle. Only the type crosses here, which
        // keeps the wire shape self-consistent without inventing a public
        // template parcel format.
        dest.writeInt(mControlTemplate == null
                ? ControlTemplate.TYPE_NO_TEMPLATE : mControlTemplate.getTemplateType());
        TextUtils.writeToParcel(mStatusText, dest, flags);
        dest.writeBoolean(mAuthRequired);
    }

    @Override public String toString() {
        return "Control[id=" + mControlId + ", status=" + mStatus + "]";
    }

    public static final Creator<Control> CREATOR = new Creator<Control>() {
        @Override public Control createFromParcel(Parcel source) {
            final String id = source.readString();
            final int deviceType = source.readInt();
            final CharSequence title = readCs(source);
            final CharSequence subtitle = readCs(source);
            final CharSequence structure = readCs(source);
            final CharSequence zone = readCs(source);
            final PendingIntent appIntent = source.readTypedObject(PendingIntent.CREATOR);
            final Icon icon = source.readTypedObject(Icon.CREATOR);
            final ColorStateList color = source.readTypedObject(ColorStateList.CREATOR);
            final int status = source.readInt();
            source.readInt();
            final CharSequence statusText = readCs(source);
            final boolean authRequired = source.readBoolean();
            return new Control(id, deviceType, title, subtitle, structure, zone,
                    appIntent, icon, color, status,
                    ControlTemplate.getNoTemplateObject(), statusText, authRequired);
        }
        @Override public Control[] newArray(int size) { return new Control[size]; }
    };

    private static CharSequence readCs(Parcel source) {
        return TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
    }

    /** Identity + appearance only; used for the initial "what exists" pass. */
    public static final class StatelessBuilder {
        private String mControlId;
        private int mDeviceType = DeviceTypes.TYPE_UNKNOWN;
        private CharSequence mTitle = "";
        private CharSequence mSubtitle = "";
        private CharSequence mStructure;
        private CharSequence mZone;
        private PendingIntent mAppIntent;
        private Icon mCustomIcon;
        private ColorStateList mCustomColor;

        public StatelessBuilder(String controlId, PendingIntent appIntent) {
            if (controlId == null) throw new NullPointerException("controlId");
            if (appIntent == null) throw new NullPointerException("appIntent");
            mControlId = controlId;
            mAppIntent = appIntent;
        }

        /** AOSP's copy constructor. */
        public StatelessBuilder(Control control) {
            if (control == null) throw new NullPointerException("control");
            mControlId = control.mControlId;
            mDeviceType = control.mDeviceType;
            mTitle = control.mTitle;
            mSubtitle = control.mSubtitle;
            mStructure = control.mStructure;
            mZone = control.mZone;
            mAppIntent = control.mAppIntent;
            mCustomIcon = control.mCustomIcon;
            mCustomColor = control.mCustomColor;
        }

        public StatelessBuilder setControlId(String controlId) {
            if (controlId == null) throw new NullPointerException("controlId");
            mControlId = controlId;
            return this;
        }

        /** AOSP silently coerces an unrecognised type to TYPE_UNKNOWN rather
         *  than throwing, so an app built against a newer taxonomy still
         *  publishes its control. */
        public StatelessBuilder setDeviceType(int deviceType) {
            mDeviceType = DeviceTypes.validDeviceType(deviceType)
                    ? deviceType : DeviceTypes.TYPE_UNKNOWN;
            return this;
        }

        public StatelessBuilder setTitle(CharSequence title) {
            if (title == null) throw new NullPointerException("title");
            mTitle = title;
            return this;
        }

        public StatelessBuilder setSubtitle(CharSequence subtitle) {
            if (subtitle == null) throw new NullPointerException("subtitle");
            mSubtitle = subtitle;
            return this;
        }

        public StatelessBuilder setStructure(CharSequence structure) {
            mStructure = structure;
            return this;
        }

        public StatelessBuilder setZone(CharSequence zone) {
            mZone = zone;
            return this;
        }

        public StatelessBuilder setAppIntent(PendingIntent appIntent) {
            if (appIntent == null) throw new NullPointerException("appIntent");
            mAppIntent = appIntent;
            return this;
        }

        public StatelessBuilder setCustomIcon(Icon customIcon) {
            mCustomIcon = customIcon;
            return this;
        }

        public StatelessBuilder setCustomColor(ColorStateList customColor) {
            mCustomColor = customColor;
            return this;
        }

        public Control build() {
            return new Control(mControlId, mDeviceType, mTitle, mSubtitle, mStructure,
                    mZone, mAppIntent, mCustomIcon, mCustomColor, STATUS_UNKNOWN,
                    ControlTemplate.getNoTemplateObject(), "", false);
        }
    }

    /** Everything the stateless builder carries, plus the live state. */
    public static final class StatefulBuilder {
        private String mControlId;
        private int mDeviceType = DeviceTypes.TYPE_UNKNOWN;
        private CharSequence mTitle = "";
        private CharSequence mSubtitle = "";
        private CharSequence mStructure;
        private CharSequence mZone;
        private PendingIntent mAppIntent;
        private Icon mCustomIcon;
        private ColorStateList mCustomColor;
        private int mStatus = STATUS_UNKNOWN;
        private ControlTemplate mControlTemplate = ControlTemplate.getNoTemplateObject();
        private CharSequence mStatusText = "";
        private boolean mAuthRequired = true;

        public StatefulBuilder(String controlId, PendingIntent appIntent) {
            if (controlId == null) throw new NullPointerException("controlId");
            if (appIntent == null) throw new NullPointerException("appIntent");
            mControlId = controlId;
            mAppIntent = appIntent;
        }

        public StatefulBuilder(Control control) {
            if (control == null) throw new NullPointerException("control");
            mControlId = control.mControlId;
            mDeviceType = control.mDeviceType;
            mTitle = control.mTitle;
            mSubtitle = control.mSubtitle;
            mStructure = control.mStructure;
            mZone = control.mZone;
            mAppIntent = control.mAppIntent;
            mCustomIcon = control.mCustomIcon;
            mCustomColor = control.mCustomColor;
            mStatus = control.mStatus;
            mControlTemplate = control.mControlTemplate;
            mStatusText = control.mStatusText;
            mAuthRequired = control.mAuthRequired;
        }

        public StatefulBuilder setControlId(String controlId) {
            if (controlId == null) throw new NullPointerException("controlId");
            mControlId = controlId;
            return this;
        }

        public StatefulBuilder setDeviceType(int deviceType) {
            mDeviceType = DeviceTypes.validDeviceType(deviceType)
                    ? deviceType : DeviceTypes.TYPE_UNKNOWN;
            return this;
        }

        public StatefulBuilder setTitle(CharSequence title) {
            if (title == null) throw new NullPointerException("title");
            mTitle = title;
            return this;
        }

        public StatefulBuilder setSubtitle(CharSequence subtitle) {
            if (subtitle == null) throw new NullPointerException("subtitle");
            mSubtitle = subtitle;
            return this;
        }

        public StatefulBuilder setStructure(CharSequence structure) {
            mStructure = structure;
            return this;
        }

        public StatefulBuilder setZone(CharSequence zone) {
            mZone = zone;
            return this;
        }

        public StatefulBuilder setAppIntent(PendingIntent appIntent) {
            if (appIntent == null) throw new NullPointerException("appIntent");
            mAppIntent = appIntent;
            return this;
        }

        public StatefulBuilder setCustomIcon(Icon customIcon) {
            mCustomIcon = customIcon;
            return this;
        }

        public StatefulBuilder setCustomColor(ColorStateList customColor) {
            mCustomColor = customColor;
            return this;
        }

        /** AOSP coerces an out-of-range status to STATUS_UNKNOWN. */
        public StatefulBuilder setStatus(int status) {
            mStatus = (status < STATUS_UNKNOWN || status > STATUS_DISABLED)
                    ? STATUS_UNKNOWN : status;
            return this;
        }

        public StatefulBuilder setControlTemplate(ControlTemplate controlTemplate) {
            if (controlTemplate == null) throw new NullPointerException("controlTemplate");
            mControlTemplate = controlTemplate;
            return this;
        }

        public StatefulBuilder setStatusText(CharSequence statusText) {
            if (statusText == null) throw new NullPointerException("statusText");
            mStatusText = statusText;
            return this;
        }

        /** API 31. */
        public StatefulBuilder setAuthRequired(boolean authRequired) {
            mAuthRequired = authRequired;
            return this;
        }

        public Control build() {
            return new Control(mControlId, mDeviceType, mTitle, mSubtitle, mStructure,
                    mZone, mAppIntent, mCustomIcon, mCustomColor, mStatus,
                    mControlTemplate, mStatusText, mAuthRequired);
        }
    }
}
