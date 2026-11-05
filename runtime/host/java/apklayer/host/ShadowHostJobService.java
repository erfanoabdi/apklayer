package apklayer.host;

import android.app.job.JobService;
import org.robolectric.annotation.Implements;

/**
 * A JobService's shadow is a ShadowHostService (stopSelf runs on the object's own shadow, see ShadowHostIntentService), and it replaces Robolectric's
 * ShadowJobService, whose jobFinished only recorded its flags: here the real jobFinished, setNotification and update*NetworkBytes run (JobServiceEngine
 * posts them to the callback of HostJobs' JobParameters; Robolectric stubbed them because tests never bind the service, so its engine was null).
 * An asynchronous job (onStartJob returns true, jobFinished later from any thread; WorkManager's SystemJobService) now ends, unbinds and is rescheduled.
 */
@Implements(value = JobService.class, isInAndroidSdk = false)
public class ShadowHostJobService extends ShadowHostService {
}
