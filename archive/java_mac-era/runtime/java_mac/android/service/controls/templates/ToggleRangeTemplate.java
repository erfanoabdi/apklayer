package android.service.controls.templates;

/**
 * A control that is both switchable and dimmable -- a light, typically.
 *
 * Source: frameworks/base/core/java/android/service/controls/templates/ToggleRangeTemplate.java
 */
public final class ToggleRangeTemplate extends ControlTemplate {

    private final ControlButton mControlButton;
    private final RangeTemplate mRangeTemplate;

    public ToggleRangeTemplate(String templateId, ControlButton button,
            RangeTemplate range) {
        super(templateId);
        if (button == null) throw new NullPointerException("button");
        if (range == null) throw new NullPointerException("range");
        mControlButton = button;
        mRangeTemplate = range;
    }

    /** Convenience overload; AOSP builds the ControlButton for the caller. */
    public ToggleRangeTemplate(String templateId, boolean checked,
            CharSequence actionDescription, RangeTemplate range) {
        this(templateId, new ControlButton(checked, actionDescription), range);
    }

    public boolean isChecked() { return mControlButton.isChecked(); }

    public CharSequence getActionDescription() {
        return mControlButton.getActionDescription();
    }

    public RangeTemplate getRange() { return mRangeTemplate; }

    @Override public int getTemplateType() { return TYPE_TOGGLE_RANGE; }
}
