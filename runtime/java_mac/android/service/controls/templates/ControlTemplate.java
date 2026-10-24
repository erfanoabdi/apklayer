package android.service.controls.templates;

/**
 * How the device-controls panel should render one Control's interactive part.
 *
 * AOSP declares it abstract with a closed set of subclasses (the panel switches
 * on getTemplateType()), which is why there is no public constructor here
 * either. See the package note on android.service.controls.DeviceTypes.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   TYPE_ERROR = -1, TYPE_NO_TEMPLATE = 0, TYPE_TOGGLE = 1, TYPE_RANGE = 2,
 *   TYPE_THUMBNAIL = 3, TYPE_TOGGLE_RANGE = 6, TYPE_TEMPERATURE = 7,
 *   TYPE_STATELESS = 8.
 * ⚠ 4 and 5 are absent from the public run (they are @hide), so the sequence
 * jumps 3 -> 6.
 *
 * Source: frameworks/base/core/java/android/service/controls/templates/ControlTemplate.java
 */
public abstract class ControlTemplate {

    public static final int TYPE_ERROR = -1;
    public static final int TYPE_NO_TEMPLATE = 0;
    public static final int TYPE_TOGGLE = 1;
    public static final int TYPE_RANGE = 2;
    public static final int TYPE_THUMBNAIL = 3;
    public static final int TYPE_TOGGLE_RANGE = 6;
    public static final int TYPE_TEMPERATURE = 7;
    public static final int TYPE_STATELESS = 8;

    private static final ControlTemplate NO_TEMPLATE =
            new SimpleTemplate("", TYPE_NO_TEMPLATE);
    private static final ControlTemplate ERROR_TEMPLATE =
            new SimpleTemplate("", TYPE_ERROR);

    private final String mTemplateId;

    ControlTemplate(String templateId) {
        if (templateId == null) throw new NullPointerException("templateId");
        mTemplateId = templateId;
    }

    public String getTemplateId() { return mTemplateId; }

    public abstract int getTemplateType();

    /** AOSP's singleton for "this control has no interactive template". */
    public static ControlTemplate getNoTemplateObject() { return NO_TEMPLATE; }

    /** AOSP's singleton handed back when a template could not be parsed. */
    public static ControlTemplate getErrorTemplate() { return ERROR_TEMPLATE; }

    private static final class SimpleTemplate extends ControlTemplate {
        private final int mType;
        SimpleTemplate(String id, int type) { super(id); mType = type; }
        @Override public int getTemplateType() { return mType; }
    }
}
