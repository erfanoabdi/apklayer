package apklayer.host;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Priority;
import org.robolectric.internal.ShadowProvider;
import org.robolectric.internal.ClassTracker;

/**
 * apklayer's shadows, found through META-INF/services like Robolectric's own. A higher priority than
 * Robolectric's providers makes ours the first match for a class (ShadowMap takes the first that matches).
 */
@Priority(1000)
public final class HostShadowProvider implements ShadowProvider {
    @Override public void reset(ClassTracker classTracker) {}

    @Override public String[] getProvidedPackageNames() { return new String[0]; }

    @Override public Collection<Map.Entry<String, String>> getShadows() {
        return List.of(Map.entry("android.webkit.WebView", ShadowHostWebView.class.getName()),
                Map.entry("android.media.MediaPlayer", ShadowHostMediaPlayer.class.getName()));
    }
}
