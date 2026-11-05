package apklayer.host;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageParser;
import android.content.pm.verify.domain.DomainVerificationUserState;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import org.robolectric.shadows.ShadowPackageParser;

/**
 * DomainVerificationManager.getDomainVerificationUserState (Settings' "Open by default") for an installed app: the web hosts its activities declare
 * (VIEW + BROWSABLE + DEFAULT on http/https, DomainVerificationCollector.collectAllWebDomains), none verified or selected, link handling allowed: what
 * Android answers for a sideloaded app whose hosts nobody verified. No verification agent runs in an instance.
 */
final class HostDomainVerification {
    private HostDomainVerification() {}

    static DomainVerificationUserState userState(String packageName, int userId) {
        Map<String, Integer> hosts = new TreeMap<>();
        File apk = new File(new File(System.getProperty("apklayer.apps", "/nonexistent"), packageName), "base.apk");
        if (apk.isFile()) {
            try {
                PackageParser.Package pkg = ShadowPackageParser.callParsePackage(apk.toPath());
                for (PackageParser.Activity activity : pkg.activities)
                    for (IntentFilter filter : activity.intents)
                        if (filter.hasAction(Intent.ACTION_VIEW) && filter.hasCategory(Intent.CATEGORY_BROWSABLE) && filter.hasCategory(Intent.CATEGORY_DEFAULT)
                                && (filter.hasDataScheme("http") || filter.hasDataScheme("https")))
                            for (int i = 0; i < filter.countDataAuthorities(); i++) hosts.put(filter.getDataAuthority(i).getHost(), DomainVerificationUserState.DOMAIN_STATE_NONE);
            } catch (RuntimeException unreadable) { System.out.println("[apklayer] web domains of " + packageName + " not read: " + unreadable); }
        }
        return new DomainVerificationUserState(java.util.UUID.nameUUIDFromBytes(packageName.getBytes()), packageName, android.os.UserHandle.of(userId), true, hosts);
    }
}
