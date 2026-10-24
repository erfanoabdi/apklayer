// android.appwidget.AppWidgetProviderInfo -- metadata describing a widget
// provider. Returned by AppWidgetManager queries (empty in our env, because a
// single-app host has no home screen to place a widget on). Public fields per
// AOSP so reflective/field access resolves. v1.0.429.
//
// Source: frameworks/base core/java/android/appwidget/AppWidgetProviderInfo.java
// (android16-release).
package android.appwidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.UserHandle;

public class AppWidgetProviderInfo implements android.os.Parcelable {

    /** Widget is not resizable. */
    public static final int RESIZE_NONE = 0;
    /** Widget is resizable in the horizontal axis only. */
    public static final int RESIZE_HORIZONTAL = 1;
    /** Widget is resizable in the vertical axis only. */
    public static final int RESIZE_VERTICAL = 2;
    /** Widget is resizable in both axes. */
    public static final int RESIZE_BOTH = RESIZE_HORIZONTAL | RESIZE_VERTICAL;

    /** Indicates that the widget can be displayed on the home screen. */
    public static final int WIDGET_CATEGORY_HOME_SCREEN = 1;
    /** Indicates that the widget can be displayed on the keyguard. */
    public static final int WIDGET_CATEGORY_KEYGUARD = 2;
    /** Indicates that the widget can be displayed within a space reserved for
     *  the search box. */
    public static final int WIDGET_CATEGORY_SEARCHBOX = 4;
    /** Indicates that the widget can be displayed on any surface EXCEPT the
     *  keyguard. */
    public static final int WIDGET_CATEGORY_NOT_KEYGUARD = 8;

    /** The widget can be reconfigured any time after it is bound. */
    public static final int WIDGET_FEATURE_RECONFIGURABLE = 1;
    /** The widget is hidden from the picker (it is created by the app itself). */
    public static final int WIDGET_FEATURE_HIDE_FROM_PICKER = 2;
    /** The widget's configuration activity is optional. */
    public static final int WIDGET_FEATURE_CONFIGURATION_OPTIONAL = 4;

    public android.content.ComponentName provider;
    public int minWidth, minHeight, minResizeWidth, minResizeHeight;
    public int maxResizeWidth, maxResizeHeight;
    /** The number of cells this widget prefers horizontally (API 31). */
    public int targetCellWidth;
    /** The number of cells this widget prefers vertically (API 31). */
    public int targetCellHeight;
    public int updatePeriodMillis;
    public int initialLayout, initialKeyguardLayout;
    public android.content.ComponentName configure;
    /** @deprecated use loadLabel(PackageManager). */
    @Deprecated
    public String label;
    public int icon;
    public int previewImage;
    /** A layout resource that renders a preview of the widget, preferred over
     *  previewImage when present (API 31). */
    public int previewLayout;
    public int autoAdvanceViewId;
    public int resizeMode;
    public int widgetCategory;
    public int widgetFeatures;
    /** A string resource describing the widget (API 31). */
    public int descriptionRes;

    /** AOSP declares this public but hides it from the SDK; getActivityInfo()
     *  is the public reader. It is the ActivityInfo of the provider's
     *  BroadcastReceiver, which is where the label/icon/package actually live.
     *  Declared with AOSP's own modifiers so a reflective reader finds it. */
    public ActivityInfo providerInfo;

    /** @hide in AOSP: whether the provider extends AppWidgetProvider (as
     *  opposed to being a bare BroadcastReceiver). Kept because it is part of
     *  the parcel layout. */
    public boolean isExtendedFromAppWidgetProvider;

    public int generatedPreviewCategories;

    public AppWidgetProviderInfo() {}

    /** AOSP's public un-parcelling constructor -- the same field order as
     *  writeToParcel() below, which is android16's order verbatim. */
    public AppWidgetProviderInfo(Parcel in) {
        this.provider = in.readTypedObject(ComponentName.CREATOR);
        this.minWidth = in.readInt();
        this.minHeight = in.readInt();
        this.minResizeWidth = in.readInt();
        this.minResizeHeight = in.readInt();
        this.maxResizeWidth = in.readInt();
        this.maxResizeHeight = in.readInt();
        this.targetCellWidth = in.readInt();
        this.targetCellHeight = in.readInt();
        this.updatePeriodMillis = in.readInt();
        this.initialLayout = in.readInt();
        this.initialKeyguardLayout = in.readInt();
        this.configure = in.readTypedObject(ComponentName.CREATOR);
        this.label = in.readString();
        this.icon = in.readInt();
        this.previewImage = in.readInt();
        this.previewLayout = in.readInt();
        this.autoAdvanceViewId = in.readInt();
        this.resizeMode = in.readInt();
        this.widgetCategory = in.readInt();
        this.providerInfo = in.readTypedObject(ActivityInfo.CREATOR);
        this.widgetFeatures = in.readInt();
        this.descriptionRes = in.readInt();
        this.isExtendedFromAppWidgetProvider = in.readBoolean();
        this.generatedPreviewCategories = in.readInt();
    }

    /** AOSP returns String, not CharSequence, and the method is final.
     *
     *  VERIFIED against the android-30 stub jar apps actually compile against
     *  (`javap -classpath android-emulator/android-sdk/platforms/android-30/
     *  android.jar android.appwidget.AppWidgetProviderInfo`):
     *
     *      public final java.lang.String loadLabel(android.content.pm.PackageManager);
     *
     *  This used to return CharSequence, which is a DIFFERENT method
     *  descriptor -- `(...)Ljava/lang/CharSequence;` against the SDK's
     *  `(...)Ljava/lang/String;`. The return type is part of the descriptor,
     *  so an app's invokevirtual could not resolve to it at all: a
     *  NoSuchMethodError at the call site rather than a wrong value. Same
     *  defect class as the MediaRecorder.getActiveRecordingConfiguration bug.
     *
     *  AOSP's body is `providerInfo.loadLabel(pm)` then `.toString().trim()`,
     *  null-preserving. That is now reproduced exactly when providerInfo is
     *  present; the `label` field is the pre-resolved fallback for the case
     *  where it is not (which is every case on this host, since nothing
     *  populates providerInfo). */
    public final String loadLabel(android.content.pm.PackageManager pm) {
        if (providerInfo != null) {
            CharSequence l = providerInfo.loadLabel(pm);
            if (l != null) {
                return l.toString().trim();
            }
        }
        return label != null ? label.trim() : null;
    }

    /** AOSP loads descriptionRes out of the provider package's resources and
     *  trims it, returning null when the id is invalid.
     *
     *  AOSP reaches the resources via
     *  PackageManager.getText(providerInfo.packageName, descriptionRes,
     *  providerInfo.applicationInfo). Mac.apk hosts exactly ONE application, so
     *  that package is always the running one and context.getResources() is the
     *  same Resources object -- the lookup is used as the fallback whenever
     *  providerInfo is absent, which is the normal state here. */
    public final CharSequence loadDescription(Context context) {
        if (!isValidResourceId(descriptionRes) || context == null) {
            return null;
        }
        CharSequence description = null;
        PackageManager pm = context.getPackageManager();
        if (pm != null && providerInfo != null) {
            try {
                description = pm.getText(providerInfo.packageName, descriptionRes,
                        providerInfo.applicationInfo);
            } catch (RuntimeException ignored) {
                description = null;
            }
        }
        if (description == null && context.getResources() != null) {
            try {
                description = context.getResources().getText(descriptionRes);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return description != null ? description.toString().trim() : null;
    }

    /** AOSP: the provider's own icon resource, at the requested density, with
     *  the package's default application icon as the fallback. */
    public final Drawable loadIcon(Context context, int density) {
        int resId = providerInfo != null ? providerInfo.getIconResource() : icon;
        return loadDrawable(context, density, resId, true);
    }

    /** AOSP: previewImage at the requested density, and NO default fallback --
     *  a provider with no preview legitimately has none. */
    public final Drawable loadPreviewImage(Context context, int density) {
        return loadDrawable(context, density, previewImage, false);
    }

    /** AOSP loadDrawable(): resolve against the PROVIDER's resources at the
     *  given density, falling back to the application icon only for loadIcon.
     *  Single-app host, so the provider's resources are this app's resources
     *  (see loadDescription). A negative density is clamped to 0, which
     *  Resources reads as "the default density", exactly as AOSP does. */
    private Drawable loadDrawable(Context context, int density, int resourceId,
            boolean loadDefaultIcon) {
        if (context == null) return null;
        Resources resources = context.getResources();
        if (resources != null && isValidResourceId(resourceId)) {
            if (density < 0) {
                density = 0;
            }
            try {
                return resources.getDrawableForDensity(resourceId, density);
            } catch (RuntimeException ignored) {
                // fall through to the default icon
            }
        }
        if (loadDefaultIcon && providerInfo != null) {
            try {
                return providerInfo.loadIcon(context.getPackageManager());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    /** AOSP's ResourceId.isValid(): 0 and -1 are the two "no resource"
     *  sentinels. */
    private static boolean isValidResourceId(int id) {
        return id != 0 && id != -1;
    }

    /** AOSP: `return providerInfo;` -- the ActivityInfo of the provider's
     *  BroadcastReceiver. Annotated @NonNull in the SDK, but AOSP's body has no
     *  null check and nothing populates providerInfo on a host with no widget
     *  host, so null is what a caller sees here. Fabricating an empty
     *  ActivityInfo would be worse: it would claim a provider exists. */
    public ActivityInfo getActivityInfo() {
        return providerInfo;
    }

    /** AOSP derives the profile from the provider's uid. Mac.apk has a single
     *  user (UserHandle.getUserId always answers USER_SYSTEM), so this is the
     *  system user -- which is the truthful answer, not a placeholder. */
    public final UserHandle getProfile() {
        int uid = (providerInfo != null && providerInfo.applicationInfo != null)
                ? providerInfo.applicationInfo.uid : 0;
        return new UserHandle(UserHandle.getUserId(uid));
    }

    /** AOSP's clone(): a field-by-field copy with the two ComponentNames
     *  deep-copied (they are mutable through their public API) and providerInfo
     *  shared by reference, exactly as AOSP shares it. */
    @Override public AppWidgetProviderInfo clone() {
        AppWidgetProviderInfo that = new AppWidgetProviderInfo();
        that.provider = this.provider == null ? null : this.provider.clone();
        that.minWidth = this.minWidth;
        that.minHeight = this.minHeight;
        that.minResizeWidth = this.minResizeWidth;
        that.minResizeHeight = this.minResizeHeight;
        that.maxResizeWidth = this.maxResizeWidth;
        that.maxResizeHeight = this.maxResizeHeight;
        that.targetCellWidth = this.targetCellWidth;
        that.targetCellHeight = this.targetCellHeight;
        that.updatePeriodMillis = this.updatePeriodMillis;
        that.initialLayout = this.initialLayout;
        that.initialKeyguardLayout = this.initialKeyguardLayout;
        that.configure = this.configure == null ? null : this.configure.clone();
        that.label = this.label;
        that.icon = this.icon;
        that.previewImage = this.previewImage;
        that.previewLayout = this.previewLayout;
        that.autoAdvanceViewId = this.autoAdvanceViewId;
        that.resizeMode = this.resizeMode;
        that.widgetCategory = this.widgetCategory;
        that.providerInfo = this.providerInfo;
        that.widgetFeatures = this.widgetFeatures;
        that.descriptionRes = this.descriptionRes;
        that.isExtendedFromAppWidgetProvider = this.isExtendedFromAppWidgetProvider;
        that.generatedPreviewCategories = this.generatedPreviewCategories;
        return that;
    }

    @Override public int describeContents() { return 0; }

    // AOSP android16-release core/java/android/appwidget/AppWidgetProviderInfo
    // .java writeToParcel, now reproducible in full because this tree declares
    // every field it writes (targetCellWidth/Height, previewLayout,
    // providerInfo and descriptionRes were added alongside this).
    //
    // This used to follow android13's order and skip those five fields, with a
    // note explaining that they did not exist here. They do now, so the order
    // is android16's verbatim -- the read side below moved with it, so the pair
    // stays self-consistent.
    //
    // generatedPreviewCategories is guarded in AOSP by the
    // android.appwidget.flags.generated_previews build flag; this tree has no
    // flag infrastructure, so it is written unconditionally (as it was before).
    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        // AOSP uses writeTypedObject for the ComponentNames and the
        // ActivityInfo; the present-int it emits is what lets a null survive
        // the trip.
        out.writeTypedObject(this.provider, flags);
        out.writeInt(this.minWidth);
        out.writeInt(this.minHeight);
        out.writeInt(this.minResizeWidth);
        out.writeInt(this.minResizeHeight);
        out.writeInt(this.maxResizeWidth);
        out.writeInt(this.maxResizeHeight);
        out.writeInt(this.targetCellWidth);
        out.writeInt(this.targetCellHeight);
        out.writeInt(this.updatePeriodMillis);
        out.writeInt(this.initialLayout);
        out.writeInt(this.initialKeyguardLayout);
        out.writeTypedObject(this.configure, flags);
        out.writeString(this.label);
        out.writeInt(this.icon);
        out.writeInt(this.previewImage);
        out.writeInt(this.previewLayout);
        out.writeInt(this.autoAdvanceViewId);
        out.writeInt(this.resizeMode);
        out.writeInt(this.widgetCategory);
        out.writeTypedObject(this.providerInfo, flags);
        out.writeInt(this.widgetFeatures);
        out.writeInt(this.descriptionRes);
        out.writeBoolean(this.isExtendedFromAppWidgetProvider);
        out.writeInt(this.generatedPreviewCategories);
    }

    public static final android.os.Parcelable.Creator<AppWidgetProviderInfo> CREATOR =
        new android.os.Parcelable.Creator<AppWidgetProviderInfo>() {
            @Override public AppWidgetProviderInfo createFromParcel(android.os.Parcel in) {
                return new AppWidgetProviderInfo(in);
            }
            @Override public AppWidgetProviderInfo[] newArray(int size) {
                return new AppWidgetProviderInfo[size];
            }
        };
}
