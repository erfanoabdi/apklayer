// android.content.pm.ActivityInfo -- metadata for a single activity
// in an APK's manifest. AOSP populates this from PackageManagerService
// when an app calls getActivityInfo(ComponentName); Mac.apk populates
// the bare minimum (name, packageName, applicationInfo, theme=0).
package android.content.pm;

public class ActivityInfo extends ComponentInfo implements android.os.Parcelable {
    /** AOSP screen-orientation constants. Apps query
     *  activityInfo.screenOrientation to decide whether to rotate
     *  child views; F-Droid checks this for tablet vs phone layout. */
    /** AOSP @hide: "no value" -- never a manifest value. Activity keeps its
     *  client-side mLastRequestedOrientation here until setRequestedOrientation
     *  runs (android16-release Activity.java:1011), and an activity whose
     *  record orientation is UNSET cannot define the side of a BEHIND activity
     *  above it (ActivityRecord.java:7342-7349). */
    public static final int SCREEN_ORIENTATION_UNSET = -2;
    public static final int SCREEN_ORIENTATION_UNSPECIFIED = -1;
    public static final int SCREEN_ORIENTATION_LANDSCAPE   = 0;
    public static final int SCREEN_ORIENTATION_PORTRAIT    = 1;
    public static final int SCREEN_ORIENTATION_USER        = 2;
    public static final int SCREEN_ORIENTATION_BEHIND      = 3;
    public static final int SCREEN_ORIENTATION_SENSOR      = 4;
    public static final int SCREEN_ORIENTATION_NOSENSOR    = 5;
    public static final int SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6;
    public static final int SCREEN_ORIENTATION_SENSOR_PORTRAIT  = 7;
    public static final int SCREEN_ORIENTATION_REVERSE_LANDSCAPE= 8;
    public static final int SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9;
    public static final int SCREEN_ORIENTATION_FULL_SENSOR      = 10;
    public static final int SCREEN_ORIENTATION_USER_LANDSCAPE   = 11;
    public static final int SCREEN_ORIENTATION_USER_PORTRAIT    = 12;
    public static final int SCREEN_ORIENTATION_FULL_USER        = 13;
    public static final int SCREEN_ORIENTATION_LOCKED           = 14;

    /** AOSP ActivityInfo public constants that were absent from this class.
     *  javac inlines an int constant into the caller, so an app compiled
     *  against the real SDK never notices a missing one -- but our own code and
     *  any REFLECTIVE reader (a plugin framework enumerating ActivityInfo's
     *  fields, a test harness) does. Values verbatim from the API-36 signature
     *  file. */
    public static final int UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW = 0x00000001;
    public static final int CONFIG_RESOURCES_UNUSED = 0x08000000;
    public static final int FLAG_ALLOW_UNTRUSTED_ACTIVITY_EMBEDDING = 0x10000000;
    public static final int FLAG_ENABLE_VR_MODE = 0x00008000;
    public static final int FLAG_IMMERSIVE = 0x00000800;
    public static final int FLAG_PREFER_MINIMAL_POST_PROCESSING = 0x02000000;
    public static final int FLAG_RELINQUISH_TASK_IDENTITY = 0x00001000;

    public static final int LAUNCH_MULTIPLE        = 0;
    public static final int LAUNCH_SINGLE_TOP      = 1;
    public static final int LAUNCH_SINGLE_TASK     = 2;
    public static final int LAUNCH_SINGLE_INSTANCE = 3;
    /** API 31+ android:launchMode="singleInstancePerTask". */
    public static final int LAUNCH_SINGLE_INSTANCE_PER_TASK = 4;

    /** android:documentLaunchMode. AOSP ActivityInfo. */
    public static final int DOCUMENT_LAUNCH_NONE          = 0;
    public static final int DOCUMENT_LAUNCH_INTO_EXISTING = 1;
    public static final int DOCUMENT_LAUNCH_ALWAYS        = 2;
    public static final int DOCUMENT_LAUNCH_NEVER         = 3;

    /** android:persistableMode. AOSP ActivityInfo. */
    public static final int PERSIST_ROOT_ONLY = 0;
    public static final int PERSIST_NEVER     = 1;
    public static final int PERSIST_ACROSS_REBOOTS = 2;

    /** AOSP ActivityInfo resize modes. `resizeMode` defaults to
     *  RESIZE_MODE_RESIZEABLE, matching AOSP's field initialiser. */
    public static final int RESIZE_MODE_UNRESIZEABLE                 = 0;
    public static final int RESIZE_MODE_RESIZEABLE                   = 2;
    public static final int RESIZE_MODE_RESIZEABLE_AND_PIPABLE       = 3;
    public static final int RESIZE_MODE_FORCE_RESIZEABLE             = 4;

    /** AOSP ActivityInfo color modes; `colorMode` defaults to
     *  COLOR_MODE_DEFAULT. */
    public static final int COLOR_MODE_DEFAULT   = 0;
    public static final int COLOR_MODE_WIDE_COLOR_GAMUT = 1;
    public static final int COLOR_MODE_HDR       = 2;

    /** Selected AOSP ActivityInfo.FLAG_* values (android:* boolean
     *  attributes are folded into `flags`). */
    public static final int FLAG_MULTIPROCESS            = 0x0001;
    public static final int FLAG_FINISH_ON_TASK_LAUNCH   = 0x0002;
    public static final int FLAG_CLEAR_TASK_ON_LAUNCH    = 0x0004;
    public static final int FLAG_ALWAYS_RETAIN_TASK_STATE= 0x0008;
    public static final int FLAG_STATE_NOT_NEEDED        = 0x0010;
    public static final int FLAG_EXCLUDE_FROM_RECENTS    = 0x0020;
    public static final int FLAG_ALLOW_TASK_REPARENTING  = 0x0040;
    public static final int FLAG_NO_HISTORY              = 0x0080;
    public static final int FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS = 0x0100;
    public static final int FLAG_HARDWARE_ACCELERATED    = 0x0200;
    public static final int FLAG_AUTO_REMOVE_FROM_RECENTS= 0x2000;
    public static final int FLAG_RESUME_WHILE_PAUSING    = 0x4000;
    public static final int FLAG_SINGLE_USER             = 0x40000000;

    /** Config-change flags: bitmask of CONFIG_* values; if a flag is
     *  set, the activity handles that config change rather than being
     *  recreated. AOSP CONFIG_* constants. */
    public static final int CONFIG_MCC             = 0x00000001;
    public static final int CONFIG_MNC             = 0x00000002;
    public static final int CONFIG_LOCALE          = 0x00000004;
    public static final int CONFIG_TOUCHSCREEN     = 0x00000008;
    public static final int CONFIG_KEYBOARD        = 0x00000010;
    public static final int CONFIG_KEYBOARD_HIDDEN = 0x00000020;
    public static final int CONFIG_NAVIGATION      = 0x00000040;
    public static final int CONFIG_ORIENTATION     = 0x00000080;
    public static final int CONFIG_SCREEN_LAYOUT   = 0x00000100;
    public static final int CONFIG_UI_MODE         = 0x00000200;
    public static final int CONFIG_SCREEN_SIZE     = 0x00000400;
    public static final int CONFIG_SMALLEST_SCREEN_SIZE = 0x00000800;
    public static final int CONFIG_DENSITY         = 0x00001000;
    public static final int CONFIG_LAYOUT_DIRECTION= 0x00002000;
    public static final int CONFIG_COLOR_MODE      = 0x00004000;
    public static final int CONFIG_GRAMMATICAL_GENDER = 0x00008000;
    public static final int CONFIG_ASSETS_PATHS    = 0x80000000;
    public static final int CONFIG_FONT_WEIGHT_ADJUSTMENT = 0x10000000;
    public static final int CONFIG_FONT_SCALE      = 0x40000000;

    // Field set + defaults mirror AOSP
    // frameworks/base/core/java/android/content/pm/ActivityInfo.java
    // (aosp-mirror platform_frameworks_base, main, fetched 2026-07-29):
    //   public int theme;
    //   public int launchMode;
    //   public int documentLaunchMode;
    //   public String permission;
    //   public String taskAffinity;
    //   public String targetActivity;
    //   public String launchToken;
    //   public int flags;
    //   public int privateFlags;
    //   public int screenOrientation = SCREEN_ORIENTATION_UNSPECIFIED;
    //   public int configChanges;
    //   public int softInputMode;
    //   public int uiOptions = 0;
    //   public String parentActivityName;
    //   public int persistableMode;
    //   public int maxRecents;
    //   public int lockTaskLaunchMode;
    //   public int resizeMode = RESIZE_MODE_RESIZEABLE;
    //   public String requestedVrComponent;
    //   public int rotationAnimation = -1;
    //   public int colorMode = COLOR_MODE_DEFAULT;
    //   public boolean supportsSizeChanges;
    //   public String requiredDisplayCategory;
    // AOSP javadoc, verbatim:
    //   parentActivityName -- "If defined, the activity named here is the
    //     logical parent of this activity."
    //   targetActivity -- "If this is an activity alias, this is the real
    //     activity class to run for it. Otherwise, this is null."
    public int    theme = 0;
    public int    launchMode = LAUNCH_MULTIPLE;
    public int    documentLaunchMode = DOCUMENT_LAUNCH_NONE;
    public int    flags;
    public int    privateFlags;
    public int    configChanges;
    public int    screenOrientation = SCREEN_ORIENTATION_UNSPECIFIED;
    public int    softInputMode;
    public int    uiOptions = 0;
    public int    persistableMode = PERSIST_ROOT_ONLY;
    public int    maxRecents;
    public int    lockTaskLaunchMode;
    public int    resizeMode = RESIZE_MODE_RESIZEABLE;
    public int    rotationAnimation = -1;
    public int    colorMode = COLOR_MODE_DEFAULT;
    public boolean supportsSizeChanges;
    public String parentActivityName;
    public String permission;
    public String taskAffinity;
    public String targetActivity;
    public String launchToken;
    public String requestedVrComponent;
    public String requiredDisplayCategory;

    /** AOSP ActivityInfo.windowLayout (API 24, ActivityInfo.java:1832): the
     *  `<layout>` child of `<activity>` -- the freeform/multi-window default
     *  size, gravity and minimum size. Null when the activity declares no
     *  `<layout>`, which is the case for nearly every app and is exactly what
     *  AOSP reports for one.
     *
     *  Mac.apk's manifest parser does not read `<layout>` yet, so this stays
     *  null. The FIELD has to exist regardless: we present API 36, so a
     *  getfield on it must link, and null is a value AOSP hands out constantly
     *  (every caller upstream null-checks it -- see dump() below). */
    public WindowLayout windowLayout;

    /** AOSP ActivityInfo.mKnownActivityEmbeddingCerts (API 34): the certificate
     *  digests this activity trusts to embed it
     *  (`android:knownActivityEmbeddingCerts`). Private upstream with a public
     *  getter; same shape here. */
    private java.util.Set<String> mKnownActivityEmbeddingCerts;

    /** AOSP ActivityInfo.WindowLayout (API 24, ActivityInfo.java:2415-2539):
     *  the parsed `<layout>` element. Every field is final and public upstream;
     *  `windowLayoutAffinity` is @hide but non-final, so it stays mutable here
     *  too. */
    public static final class WindowLayout {
        public WindowLayout(int width, float widthFraction, int height, float heightFraction,
                int gravity, int minWidth, int minHeight) {
            this(width, widthFraction, height, heightFraction, gravity, minWidth, minHeight, null);
        }
        public WindowLayout(int width, float widthFraction, int height, float heightFraction,
                int gravity, int minWidth, int minHeight, String windowLayoutAffinity) {
            this.width = width;
            this.widthFraction = widthFraction;
            this.height = height;
            this.heightFraction = heightFraction;
            this.gravity = gravity;
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.windowLayoutAffinity = windowLayoutAffinity;
        }
        public WindowLayout(android.os.Parcel source) {
            width = source.readInt();
            widthFraction = source.readFloat();
            height = source.readInt();
            heightFraction = source.readFloat();
            gravity = source.readInt();
            minWidth = source.readInt();
            minHeight = source.readInt();
            windowLayoutAffinity = source.readString();
        }
        public final int width;
        public final float widthFraction;
        public final int height;
        public final float heightFraction;
        public final int gravity;
        public final int minWidth;
        public final int minHeight;
        public String windowLayoutAffinity;

        /** AOSP WindowLayout.hasSpecifiedSize() (@hide). */
        public boolean hasSpecifiedSize() {
            return width >= 0 || height >= 0 || widthFraction >= 0 || heightFraction >= 0;
        }
        public void writeToParcel(android.os.Parcel dest) {
            dest.writeInt(width);
            dest.writeFloat(widthFraction);
            dest.writeInt(height);
            dest.writeFloat(heightFraction);
            dest.writeInt(gravity);
            dest.writeInt(minWidth);
            dest.writeInt(minHeight);
            dest.writeString(windowLayoutAffinity);
        }
    }

    public ActivityInfo() {}

    /** AOSP ActivityInfo(ActivityInfo orig) copy constructor. AppCompat and
     *  several androidx components clone an ActivityInfo before mutating a
     *  field (e.g. to force a screenOrientation), so the copy has to carry
     *  every field or the clone silently loses the manifest data. */
    public ActivityInfo(ActivityInfo orig) {
        if (orig == null) return;
        this.name              = orig.name;
        this.packageName       = orig.packageName;
        this.labelRes          = orig.labelRes;
        this.nonLocalizedLabel = orig.nonLocalizedLabel;
        this.icon              = orig.icon;
        this.banner            = orig.banner;
        this.logo              = orig.logo;
        this.metaData          = orig.metaData;
        this.applicationInfo   = orig.applicationInfo;
        this.processName       = orig.processName;
        this.descriptionRes    = orig.descriptionRes;
        this.enabled           = orig.enabled;
        this.exported          = orig.exported;
        this.directBootAware   = orig.directBootAware;
        this.theme             = orig.theme;
        this.launchMode        = orig.launchMode;
        this.documentLaunchMode = orig.documentLaunchMode;
        this.flags             = orig.flags;
        this.privateFlags      = orig.privateFlags;
        this.configChanges     = orig.configChanges;
        this.screenOrientation = orig.screenOrientation;
        this.softInputMode     = orig.softInputMode;
        this.uiOptions         = orig.uiOptions;
        this.persistableMode   = orig.persistableMode;
        this.maxRecents        = orig.maxRecents;
        this.lockTaskLaunchMode = orig.lockTaskLaunchMode;
        this.resizeMode        = orig.resizeMode;
        this.rotationAnimation = orig.rotationAnimation;
        this.colorMode         = orig.colorMode;
        this.supportsSizeChanges = orig.supportsSizeChanges;
        this.parentActivityName = orig.parentActivityName;
        this.permission        = orig.permission;
        this.taskAffinity      = orig.taskAffinity;
        this.targetActivity    = orig.targetActivity;
        this.launchToken       = orig.launchToken;
        this.requestedVrComponent = orig.requestedVrComponent;
        this.requiredDisplayCategory = orig.requiredDisplayCategory;
        this.windowLayout      = orig.windowLayout;
        this.mKnownActivityEmbeddingCerts = orig.mKnownActivityEmbeddingCerts;
        // These four are inherited state this copy constructor was silently
        // dropping. AOSP reaches them via super(orig) -> ComponentInfo(orig) ->
        // PackageItemInfo(orig); this class hand-copies instead (so it can
        // tolerate a null `orig`, which AOSP does not), and the hand-copy had
        // gone stale as the base classes grew.
        this.splitName         = orig.splitName;
        this.attributionTags   = orig.attributionTags;
        this.showUserIcon      = orig.showUserIcon;
        this.isArchived        = orig.isArchived;
    }

    /** AOSP ActivityInfo.getKnownActivityEmbeddingCerts() (API 34), body
     *  verbatim including the @NonNull empty-set answer. */
    public java.util.Set<String> getKnownActivityEmbeddingCerts() {
        return mKnownActivityEmbeddingCerts == null
                ? java.util.Collections.<String>emptySet()
                : mKnownActivityEmbeddingCerts;
    }

    /** AOSP ActivityInfo.launchModeToString(int) -- public but @hide upstream;
     *  kept public so a reflective caller finds the same runtime surface. */
    public static String launchModeToString(int launchMode) {
        switch (launchMode) {
            case LAUNCH_MULTIPLE:                 return "LAUNCH_MULTIPLE";
            case LAUNCH_SINGLE_TOP:               return "LAUNCH_SINGLE_TOP";
            case LAUNCH_SINGLE_TASK:              return "LAUNCH_SINGLE_TASK";
            case LAUNCH_SINGLE_INSTANCE:          return "LAUNCH_SINGLE_INSTANCE";
            case LAUNCH_SINGLE_INSTANCE_PER_TASK: return "LAUNCH_SINGLE_INSTANCE_PER_TASK";
            default:                              return "unknown=" + launchMode;
        }
    }

    /** AOSP ActivityInfo.resizeModeToString(int). Only the resize modes this
     *  tree declares are named; the rest fall to AOSP's own "unknown=" form,
     *  which is what upstream prints for a value it does not recognise. */
    public static String resizeModeToString(int mode) {
        switch (mode) {
            case RESIZE_MODE_UNRESIZEABLE:           return "RESIZE_MODE_UNRESIZEABLE";
            case RESIZE_MODE_RESIZEABLE:             return "RESIZE_MODE_RESIZEABLE";
            case RESIZE_MODE_RESIZEABLE_AND_PIPABLE: return "RESIZE_MODE_RESIZEABLE_AND_PIPABLE";
            case RESIZE_MODE_FORCE_RESIZEABLE:       return "RESIZE_MODE_FORCE_RESIZEABLE";
            default:                                 return "unknown=" + mode;
        }
    }

    private String persistableModeToString() {
        switch (persistableMode) {
            case PERSIST_ROOT_ONLY:      return "PERSIST_ROOT_ONLY";
            case PERSIST_NEVER:          return "PERSIST_NEVER";
            case PERSIST_ACROSS_REBOOTS: return "PERSIST_ACROSS_REBOOTS";
            default:                     return "UNKNOWN=" + persistableMode;
        }
    }

    /** AOSP ActivityInfo.dump(Printer, String):2159-2161 -> the flags form at
     *  :2163-2220. Line text follows upstream over the fields this tree
     *  declares; the lines for maxAspectRatio / minAspectRatio /
     *  requireContentUriPermissionFromCaller are omitted because those fields
     *  do not exist here and a dump line naming one would read as data. */
    public void dump(android.util.Printer pw, String prefix) {
        dump(pw, prefix, DUMP_FLAG_ALL);
    }

    void dump(android.util.Printer pw, String prefix, int dumpFlags) {
        super.dumpFront(pw, prefix);
        if (permission != null) {
            pw.println(prefix + "permission=" + permission);
        }
        if ((dumpFlags & DUMP_FLAG_DETAILS) != 0) {
            pw.println(prefix + "taskAffinity=" + taskAffinity
                    + " targetActivity=" + targetActivity
                    + " persistableMode=" + persistableModeToString());
        }
        if (launchMode != 0 || flags != 0 || privateFlags != 0 || theme != 0) {
            pw.println(prefix + "launchMode=" + launchModeToString(launchMode)
                    + " flags=0x" + Integer.toHexString(flags)
                    + " privateFlags=0x" + Integer.toHexString(privateFlags)
                    + " theme=0x" + Integer.toHexString(theme));
        }
        if (screenOrientation != SCREEN_ORIENTATION_UNSPECIFIED
                || configChanges != 0 || softInputMode != 0) {
            pw.println(prefix + "screenOrientation=" + screenOrientation
                    + " configChanges=0x" + Integer.toHexString(configChanges)
                    + " softInputMode=0x" + Integer.toHexString(softInputMode));
        }
        if (uiOptions != 0) {
            pw.println(prefix + " uiOptions=0x" + Integer.toHexString(uiOptions));
        }
        if ((dumpFlags & DUMP_FLAG_DETAILS) != 0) {
            pw.println(prefix + "lockTaskLaunchMode=" + lockTaskLaunchMode);
        }
        if (windowLayout != null) {
            pw.println(prefix + "windowLayout=" + windowLayout.width + "|"
                    + windowLayout.widthFraction + ", " + windowLayout.height + "|"
                    + windowLayout.heightFraction + ", " + windowLayout.gravity);
        }
        pw.println(prefix + "resizeMode=" + resizeModeToString(resizeMode));
        if (requestedVrComponent != null) {
            pw.println(prefix + "requestedVrComponent=" + requestedVrComponent);
        }
        if (supportsSizeChanges) {
            pw.println(prefix + "supportsSizeChanges=true");
        }
        if (mKnownActivityEmbeddingCerts != null) {
            pw.println(prefix + "knownActivityEmbeddingCerts=" + mKnownActivityEmbeddingCerts);
        }
        if (requiredDisplayCategory != null) {
            pw.println(prefix + "requiredDisplayCategory=" + requiredDisplayCategory);
        }
        super.dumpBack(pw, prefix, dumpFlags);
    }

    /** AOSP ActivityInfo.getThemeResource (android11-release
     *  ActivityInfo.java:1033-1035):
     *      return theme != 0 ? theme : applicationInfo.theme;
     *  The application-manifest theme is the documented fallback when the
     *  <activity> declares none; ActivityThread.performLaunchActivity applies
     *  exactly this value via activity.setTheme() before onCreate. Returning
     *  the bare field made every theme-less <activity> answer 0 even when
     *  <application android:theme> was set. */
    public int getThemeResource() {
        if (theme != 0) return theme;
        return applicationInfo != null ? applicationInfo.theme : 0;
    }

    // ---- Parcelable --------------------------------------------------------
    // AOSP: `ActivityInfo extends ComponentInfo implements Parcelable`
    // (android11-release ActivityInfo.java:47). Order below is AOSP
    // ActivityInfo.java:1215-1247, restricted to fields this tree declares:
    // windowLayout, maxAspectRatio and minAspectRatio are not modelled here, so
    // they are simply absent rather than written as invented defaults.
    // requiredDisplayCategory is API 34 and not in the android11 writer; AOSP
    // main puts it after supportsSizeChanges (ActivityInfo.java:2228-2230),
    // which is where it goes here.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        dest.writeInt(theme);
        dest.writeInt(launchMode);
        dest.writeInt(documentLaunchMode);
        dest.writeString(permission);
        dest.writeString(taskAffinity);
        dest.writeString(targetActivity);
        dest.writeString(launchToken);
        dest.writeInt(flags);
        dest.writeInt(privateFlags);
        dest.writeInt(screenOrientation);
        dest.writeInt(configChanges);
        dest.writeInt(softInputMode);
        dest.writeInt(uiOptions);
        dest.writeString(parentActivityName);
        dest.writeInt(persistableMode);
        dest.writeInt(maxRecents);
        dest.writeInt(lockTaskLaunchMode);
        dest.writeInt(resizeMode);
        dest.writeString(requestedVrComponent);
        dest.writeInt(rotationAnimation);
        dest.writeInt(colorMode);
        dest.writeBoolean(supportsSizeChanges);
        dest.writeString(requiredDisplayCategory);
        // AOSP ActivityInfo.java:2258-2263: a present-flag then the body, so a
        // null <layout> costs one int.
        if (windowLayout != null) {
            dest.writeInt(1);
            windowLayout.writeToParcel(dest);
        } else {
            dest.writeInt(0);
        }
        dest.writeStringArray(mKnownActivityEmbeddingCerts == null
                ? null : mKnownActivityEmbeddingCerts.toArray(new String[0]));
    }

    /** AOSP ActivityInfo.java:1337-1366. */
    private ActivityInfo(android.os.Parcel source) {
        super(source);
        theme = source.readInt();
        launchMode = source.readInt();
        documentLaunchMode = source.readInt();
        permission = source.readString();
        taskAffinity = source.readString();
        targetActivity = source.readString();
        launchToken = source.readString();
        flags = source.readInt();
        privateFlags = source.readInt();
        screenOrientation = source.readInt();
        configChanges = source.readInt();
        softInputMode = source.readInt();
        uiOptions = source.readInt();
        parentActivityName = source.readString();
        persistableMode = source.readInt();
        maxRecents = source.readInt();
        lockTaskLaunchMode = source.readInt();
        resizeMode = source.readInt();
        requestedVrComponent = source.readString();
        rotationAnimation = source.readInt();
        colorMode = source.readInt();
        supportsSizeChanges = source.readBoolean();
        requiredDisplayCategory = source.readString();
        if (source.readInt() == 1) {
            windowLayout = new WindowLayout(source);
        }
        String[] certs = source.createStringArray();
        mKnownActivityEmbeddingCerts = (certs == null)
                ? null : new java.util.LinkedHashSet<>(java.util.Arrays.asList(certs));
    }

    public static final android.os.Parcelable.Creator<ActivityInfo> CREATOR =
        new android.os.Parcelable.Creator<ActivityInfo>() {
            @Override public ActivityInfo createFromParcel(android.os.Parcel source) {
                return new ActivityInfo(source);
            }
            @Override public ActivityInfo[] newArray(int size) {
                return new ActivityInfo[size];
            }
        };
}

