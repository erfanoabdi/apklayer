package android.service.controls.templates;

/**
 * An on/off control, rendered as a single button.
 *
 * Source: frameworks/base/core/java/android/service/controls/templates/ToggleTemplate.java
 */
public final class ToggleTemplate extends ControlTemplate {

    private final ControlButton mButton;

    public ToggleTemplate(String templateId, ControlButton button) {
        super(templateId);
        if (button == null) throw new NullPointerException("button");
        mButton = button;
    }

    public boolean isChecked() { return mButton.isChecked(); }

    /** AOSP reads this off the button rather than storing a second copy. */
    public CharSequence getContentDescription() { return mButton.getActionDescription(); }

    @Override public int getTemplateType() { return TYPE_TOGGLE; }
}
