package androidx.window.extensions.area;
public interface ExtensionWindowAreaPresentation {
    android.content.Context getPresentationContext();
    android.view.Window getWindow();
    void setPresentationView(android.view.View view);
}
