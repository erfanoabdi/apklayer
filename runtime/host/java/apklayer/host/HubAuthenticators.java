package apklayer.host;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.pm.PackageParser;
import android.content.res.ApkAssets;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;

/**
 * Which installed package authenticates which account type, as AccountAuthenticatorCache finds them: a service with the
 * android.accounts.AccountAuthenticator action whose meta-data of that name is an account-authenticator XML resource of its APK.
 */
final class HubAuthenticators {
    record Authenticator(AuthenticatorDescription description, ComponentName service) {}

    private final Path apps;
    private final HubPackages packages;
    private List<String> scanned = List.of();
    private Map<String, Authenticator> byType = Map.of();

    HubAuthenticators(Path apps, HubPackages packages) { this.apps = apps; this.packages = packages; }

    /** By account type; scanned again when the installed packages change. */
    synchronized Map<String, Authenticator> all() {
        List<String> installed = packages.packageNames();
        if (installed.equals(scanned)) return byType;
        Map<String, Authenticator> found = new LinkedHashMap<>();
        for (String name : installed) {
            PackageParser.Package pkg;
            try { pkg = packages.get(name); } catch (RuntimeException unreadable) { continue; }
            if (pkg == null) continue;
            for (PackageParser.Service service : pkg.services) {
                if (service.intents.stream().noneMatch(f -> ((IntentFilter) f).hasAction(AccountManager.ACTION_AUTHENTICATOR_INTENT))) continue;
                int xml = service.metaData == null ? 0 : service.metaData.getInt(AccountManager.AUTHENTICATOR_META_DATA_NAME);
                AuthenticatorDescription description = xml == 0 ? null : describe(name, xml);
                if (description != null) found.putIfAbsent(description.type, new Authenticator(description, new ComponentName(name, service.info.name)));
            }
        }
        scanned = installed;
        byType = found;
        System.out.println("[apklayer-hub] account authenticators: " + found.keySet());
        return found;
    }

    /** The account-authenticator element of the package's XML resource, read like AccountAuthenticatorCache.parseServiceAttributes. */
    private AuthenticatorDescription describe(String pkg, int xml) {
        try {
            AssetManager assets = new AssetManager();
            ApkAssets[] system = AssetManager.getSystem().getApkAssets();
            ApkAssets[] all = java.util.Arrays.copyOf(system, system.length + 1);
            all[system.length] = ApkAssets.loadFromPath(apps.resolve(pkg).resolve("base.apk").toString());
            assets.setApkAssets(all, false);
            Resources res = new Resources(assets, Resources.getSystem().getDisplayMetrics(), Resources.getSystem().getConfiguration());
            try (XmlResourceParser parser = res.getXml(xml)) {
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT && event != XmlPullParser.START_TAG) { /* to the root element */ }
                if (!"account-authenticator".equals(parser.getName())) return null;
                TypedArray a = res.obtainAttributes(android.util.Xml.asAttributeSet(parser), com.android.internal.R.styleable.AccountAuthenticator);
                try {
                    String type = a.getString(com.android.internal.R.styleable.AccountAuthenticator_accountType);
                    if (type == null || type.isEmpty()) return null;
                    return new AuthenticatorDescription(type, pkg, a.getResourceId(com.android.internal.R.styleable.AccountAuthenticator_label, 0),
                            a.getResourceId(com.android.internal.R.styleable.AccountAuthenticator_icon, 0), a.getResourceId(com.android.internal.R.styleable.AccountAuthenticator_smallIcon, 0),
                            a.getResourceId(com.android.internal.R.styleable.AccountAuthenticator_accountPreferences, 0),
                            a.getBoolean(com.android.internal.R.styleable.AccountAuthenticator_customTokens, false));
                } finally { a.recycle(); }
            }
        } catch (Exception unreadable) {
            System.out.println("[apklayer-hub] account authenticator of " + pkg + " not readable: " + unreadable);
            return null;
        }
    }
}
