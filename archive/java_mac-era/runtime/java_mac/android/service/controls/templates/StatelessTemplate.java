package android.service.controls.templates;

/**
 * A template for a control that has no state to show -- tapping it just fires
 * the control's app intent.
 *
 * Source: frameworks/base/core/java/android/service/controls/templates/StatelessTemplate.java
 */
public final class StatelessTemplate extends ControlTemplate {

    public StatelessTemplate(String templateId) { super(templateId); }

    @Override public int getTemplateType() { return TYPE_STATELESS; }
}
