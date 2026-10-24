package android.service.controls.templates;

/**
 * A thermostat: an inner template for the temperature itself, plus the set of
 * modes the device supports and which one is selected / currently active.
 *
 * ⚠ Constants EXTRACTED MECHANICALLY from /tmp/aosp_api/android16.txt. Two
 * parallel runs live here and they are easy to conflate: MODE_* are ORDINALS
 * (0..5) and FLAG_MODE_* are a BITMASK (2,4,8,16,32). Note there is no
 * FLAG_MODE for MODE_UNKNOWN, so the flag for mode N is 1 << N.
 *
 * Source: frameworks/base/core/java/android/service/controls/templates/TemperatureControlTemplate.java
 */
public final class TemperatureControlTemplate extends ControlTemplate {

    public static final int FLAG_MODE_COOL = 8;
    public static final int FLAG_MODE_ECO = 32;
    public static final int FLAG_MODE_HEAT = 4;
    public static final int FLAG_MODE_HEAT_COOL = 16;
    public static final int FLAG_MODE_OFF = 2;
    public static final int MODE_COOL = 3;
    public static final int MODE_ECO = 5;
    public static final int MODE_HEAT = 2;
    public static final int MODE_HEAT_COOL = 4;
    public static final int MODE_OFF = 1;
    public static final int MODE_UNKNOWN = 0;

    private final ControlTemplate mTemplate;
    private final int mCurrentMode;
    private final int mCurrentActiveMode;
    private final int mModes;

    public TemperatureControlTemplate(String templateId, ControlTemplate controlTemplate,
            int currentMode, int currentActiveMode, int modes) {
        super(templateId);
        if (controlTemplate == null) throw new NullPointerException("controlTemplate");
        // AOSP's validation: an out-of-range mode becomes MODE_UNKNOWN rather
        // than throwing, and the mode is then required to be one the device
        // says it supports.
        if (currentMode < MODE_UNKNOWN || currentMode > MODE_ECO) {
            throw new IllegalArgumentException("currentMode out of range: " + currentMode);
        }
        if (currentActiveMode < MODE_UNKNOWN || currentActiveMode > MODE_ECO) {
            throw new IllegalArgumentException(
                    "currentActiveMode out of range: " + currentActiveMode);
        }
        if (currentMode != MODE_UNKNOWN && (modes & (1 << currentMode)) == 0) {
            throw new IllegalArgumentException("currentMode is not supported in modes");
        }
        if (currentActiveMode != MODE_UNKNOWN && (modes & (1 << currentActiveMode)) == 0) {
            throw new IllegalArgumentException(
                    "currentActiveMode is not supported in modes");
        }
        mTemplate = controlTemplate;
        mCurrentMode = currentMode;
        mCurrentActiveMode = currentActiveMode;
        mModes = modes;
    }

    public ControlTemplate getTemplate() { return mTemplate; }

    public int getCurrentMode() { return mCurrentMode; }

    public int getCurrentActiveMode() { return mCurrentActiveMode; }

    /** Bitmask of FLAG_MODE_*. */
    public int getModes() { return mModes; }

    @Override public int getTemplateType() { return TYPE_TEMPERATURE; }
}
