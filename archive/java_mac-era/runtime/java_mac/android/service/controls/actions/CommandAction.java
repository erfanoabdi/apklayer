package android.service.controls.actions;

/**
 * The user tapped a StatelessTemplate control; there is no new value, only the
 * fact that it was pressed.
 *
 * Source: frameworks/base/core/java/android/service/controls/actions/CommandAction.java
 */
public final class CommandAction extends ControlAction {

    public CommandAction(String templateId) { this(templateId, null); }

    public CommandAction(String templateId, String challengeValue) {
        super(templateId, challengeValue);
    }

    @Override public int getActionType() { return TYPE_COMMAND; }
}
