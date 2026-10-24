package com.macapk.accounts;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;

/**
 * The account types the running app's OWN authenticators manage -- Mac.apk's equivalent of
 * the slice of AOSP's AccountAuthenticatorCache that belongs to the caller.
 *
 * AOSP (android14-release):
 *   AccountAuthenticatorCache.java -- a RegisteredServicesCache over services that answer
 *     ACTION_AUTHENTICATOR_INTENT ("android.accounts.AccountAuthenticator") and carry
 *     AUTHENTICATOR_META_DATA_NAME ("android.accounts.AccountAuthenticator") pointing at an
 *     XML whose root tag is AUTHENTICATOR_ATTRIBUTES_NAME ("account-authenticator").
 *     parseServiceAttributes reads accountType, label, icon, smallIcon, accountPreferences
 *     and customTokens, and returns null (no authenticator) when accountType is empty.
 *   RegisteredServicesCache.parseServiceInfo -- loadXmlMetaData(pm, metaDataName); skip to the
 *     first START_TAG; a root tag other than the attributes name is an error (the service is
 *     not registered).
 *   AccountManagerService.isAccountManagedByCaller (:5761) / getTypesForCaller (:5778) -- a
 *     caller manages a type when the authenticator's uid shares its signing identity
 *     (hasSignatureCapability ... AUTH). Mac.apk hosts ONE app per process, so the caller's
 *     own package's authenticators are exactly the types it manages; there is no second
 *     installed package whose authenticator could match.
 */
public final class AppAuthenticators {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** One registered authenticator: its description and the service that hosts it. */
    public static final class Entry {
        public final AuthenticatorDescription description;
        public final ComponentName service;
        Entry(AuthenticatorDescription d, ComponentName s) { description = d; service = s; }
    }

    private static final Object LOCK = new Object();
    private static String sPkg;
    private static Map<String, Entry> sByType;

    private AppAuthenticators() {}

    /** type -> authenticator for the app {@code ctx} belongs to. Scanned once per package (the
     *  manifest cannot change under a running process), insertion order = manifest order. */
    public static Map<String, Entry> of(Context ctx) {
        if (ctx == null) return Collections.emptyMap();
        String pkg = ctx.getPackageName();
        synchronized (LOCK) {
            if (sByType != null && pkg != null && pkg.equals(sPkg)) return sByType;
            Map<String, Entry> m = scan(ctx, pkg);
            sPkg = pkg;
            sByType = m;
            return m;
        }
    }

    private static Map<String, Entry> scan(Context ctx, String pkg) {
        Map<String, Entry> out = new LinkedHashMap<>();
        PackageManager pm = ctx.getPackageManager();
        if (pm == null || pkg == null) return Collections.unmodifiableMap(out);
        List<ResolveInfo> l;
        try {
            Intent i = new Intent(AccountManager.ACTION_AUTHENTICATOR_INTENT).setPackage(pkg);
            l = pm.queryIntentServices(i, PackageManager.GET_META_DATA);
        } catch (Throwable t) {
            System.err.println("[accounts] authenticator scan failed: " + t);
            return Collections.unmodifiableMap(out);
        }
        if (l == null) return Collections.unmodifiableMap(out);
        for (ResolveInfo ri : l) {
            ServiceInfo si = ri == null ? null : ri.serviceInfo;
            if (si == null) continue;
            XmlResourceParser parser = null;
            try {
                parser = si.loadXmlMetaData(pm, AccountManager.AUTHENTICATOR_META_DATA_NAME);
                if (parser == null) {
                    System.err.println("[accounts] " + si.name + ": no "
                            + AccountManager.AUTHENTICATOR_META_DATA_NAME + " meta-data");
                    continue;
                }
                Resources res;
                try {
                    res = pm.getResourcesForApplication(si.packageName != null ? si.packageName : pkg);
                } catch (Throwable t1) {
                    try { res = ctx.getResources(); } catch (Throwable t2) { res = null; }
                }
                AuthenticatorDescription d = parse(parser, res, si.packageName != null ? si.packageName : pkg);
                if (d != null && !out.containsKey(d.type)) {
                    out.put(d.type, new Entry(d, new ComponentName(pkg, si.name)));
                }
            } catch (Throwable t) {
                System.err.println("[accounts] " + si.name + ": unparseable authenticator: " + t);
            } finally {
                if (parser != null) try { parser.close(); } catch (Throwable ignored) {}
            }
        }
        if (!out.isEmpty()) {
            System.err.println("[accounts] app authenticators: " + out.keySet());
        }
        return Collections.unmodifiableMap(out);
    }

    /** RegisteredServicesCache.parseServiceInfo + AccountAuthenticatorCache.parseServiceAttributes
     *  over an already-opened meta-data XML. Null when the root tag is not
     *  "account-authenticator" or accountType is empty -- both mean "not registered" in AOSP. */
    public static AuthenticatorDescription parse(XmlPullParser parser, Resources res, String pkg)
            throws Exception {
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && type != XmlPullParser.START_TAG) {
            // skip to the root element, as RegisteredServicesCache does
        }
        if (type != XmlPullParser.START_TAG
                || !AccountManager.AUTHENTICATOR_ATTRIBUTES_NAME.equals(parser.getName())) {
            return null;
        }
        String accountType = string(parser, res, "accountType");
        if (accountType == null || accountType.isEmpty()) return null;
        return new AuthenticatorDescription(accountType, pkg,
                ref(parser, "label"), ref(parser, "icon"), ref(parser, "smallIcon"),
                ref(parser, "accountPreferences"),
                parser instanceof android.util.AttributeSet
                        ? ((android.util.AttributeSet) parser)
                                .getAttributeBooleanValue(ANDROID_NS, "customTokens", false)
                        : Boolean.parseBoolean(parser.getAttributeValue(ANDROID_NS, "customTokens")));
    }

    /** TypedArray.getString semantics: a reference resolves through the app's resources, a
     *  literal is returned as written. */
    private static String string(XmlPullParser parser, Resources res, String attr) {
        int id = ref(parser, attr);
        if (id != 0 && res != null) {
            try { return res.getString(id); } catch (Throwable ignored) { }
        }
        String v = parser.getAttributeValue(ANDROID_NS, attr);
        if (v != null && v.startsWith("@")) return null;   // an unresolvable reference
        return v;
    }

    private static int ref(XmlPullParser parser, String attr) {
        if (parser instanceof android.util.AttributeSet) {
            return ((android.util.AttributeSet) parser).getAttributeResourceValue(ANDROID_NS, attr, 0);
        }
        return 0;
    }
}
