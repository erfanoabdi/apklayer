// android.view.XrWindowProperties -- the manifest <property> names an app uses
// to declare how it wants to start in an XR (Android XR) environment.
// Source: frameworks/base core/java/android/view/XrWindowProperties.java
// (android16-release). Constants only -- there is nothing to implement.
//
// Mac.apk targets macOS windows, not an XR shell, so nothing reads these; the
// class exists so an app that names one resolves.
package android.view;

public final class XrWindowProperties {
    public static final String PROPERTY_XR_ACTIVITY_START_MODE =
            "android.window.PROPERTY_XR_ACTIVITY_START_MODE";
    public static final String XR_ACTIVITY_START_MODE_UNDEFINED =
            "XR_ACTIVITY_START_MODE_UNDEFINED";
    public static final String XR_ACTIVITY_START_MODE_HOME_SPACE =
            "XR_ACTIVITY_START_MODE_HOME_SPACE";
    public static final String XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED =
            "XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED";
    public static final String XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED =
            "XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED";
    public static final String PROPERTY_XR_USES_CUSTOM_FULL_SPACE_MANAGED_ANIMATION =
            "android.window.PROPERTY_XR_USES_CUSTOM_FULL_SPACE_MANAGED_ANIMATION";
    public static final String PROPERTY_XR_BOUNDARY_TYPE_RECOMMENDED =
            "android.window.PROPERTY_XR_BOUNDARY_TYPE_RECOMMENDED";
    public static final String XR_BOUNDARY_TYPE_NO_RECOMMENDATION =
            "XR_BOUNDARY_TYPE_NO_RECOMMENDATION";
    public static final String XR_BOUNDARY_TYPE_LARGE = "XR_BOUNDARY_TYPE_LARGE";

    private XrWindowProperties() {}
}
