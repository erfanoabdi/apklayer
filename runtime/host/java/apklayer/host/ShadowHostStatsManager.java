package apklayer.host;

import android.app.StatsManager;
import java.util.concurrent.Executor;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * There is no statsd: apps and system providers register pull callbacks (MediaProvider's metrics at its creation) that nothing will ever pull. Calls that need the
 * service answer StatsUnavailableException, as a device whose statsd is not running does (it was a NullPointerException from the missing StatsServiceManager, which
 * ended Play services' process through its uncaught-exception handler: AnomalyConfigIntentOperation).
 */
@Implements(value = StatsManager.class, isInAndroidSdk = false)
public class ShadowHostStatsManager {
    @Implementation protected void setPullAtomCallback(int atomTag, StatsManager.PullAtomMetadata metadata, Executor executor, StatsManager.StatsPullAtomCallback callback) {}
    @Implementation protected void clearPullAtomCallback(int atomTag) {}
    @Implementation protected android.os.IStatsManagerService getIStatsManagerServiceLocked() { return null; }
    @Implementation protected void setBroadcastSubscriber(android.app.PendingIntent pendingIntent, long configId, long subscriberId) throws StatsManager.StatsUnavailableException {
        throw new StatsManager.StatsUnavailableException("statsd is not available");
    }
    @Implementation protected void addConfig(long configKey, byte[] config) throws StatsManager.StatsUnavailableException { throw new StatsManager.StatsUnavailableException("statsd is not available"); }
    @Implementation protected void removeConfig(long configKey) throws StatsManager.StatsUnavailableException { throw new StatsManager.StatsUnavailableException("statsd is not available"); }
    @Implementation protected void setFetchReportsOperation(android.app.PendingIntent pendingIntent, long configId) throws StatsManager.StatsUnavailableException { throw new StatsManager.StatsUnavailableException("statsd is not available"); }
    @Implementation protected byte[] getReports(long configKey) throws StatsManager.StatsUnavailableException { throw new StatsManager.StatsUnavailableException("statsd is not available"); }
}
