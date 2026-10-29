package apklayer.host;

import android.app.StatsManager;
import java.util.concurrent.Executor;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** There is no statsd: apps and system providers register pull callbacks (MediaProvider's metrics at its creation) that nothing will ever pull. */
@Implements(value = StatsManager.class, isInAndroidSdk = false)
public class ShadowHostStatsManager {
    @Implementation protected void setPullAtomCallback(int atomTag, StatsManager.PullAtomMetadata metadata, Executor executor, StatsManager.StatsPullAtomCallback callback) {}
    @Implementation protected void clearPullAtomCallback(int atomTag) {}
}
