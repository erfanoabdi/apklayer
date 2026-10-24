package android.service.controls.actions;

/**
 * The user picked a different mode on a TemperatureControlTemplate.
 *
 * Source: frameworks/base/core/java/android/service/controls/actions/ModeAction.java
 */
public final class ModeAction extends ControlAction {

    private final int mNewMode;

    public ModeAction(String templateId, int newMode) {
        this(templateId, newMode, null);
    }

    public ModeAction(String templateId, int newMode, String challengeValue) {
        super(templateId, challengeValue);
        mNewMode = newMode;
    }

    public int getNewMode() { return mNewMode; }

    @Override public int getActionType() { return TYPE_MODE; }
}
