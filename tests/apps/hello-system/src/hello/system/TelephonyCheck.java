package hello.system;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.List;

/** Telephony as a data-only desktop sees it: the manager answers (phone type, SIM state, operator, country) and the subscription list can be asked, without an exception. */
final class TelephonyCheck implements Check {
    @Override public String run(Context context) throws Exception {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        Check.require(tm != null, "no TelephonyManager");
        int type = tm.getPhoneType(), sim = tm.getSimState();
        String operator = tm.getNetworkOperatorName(), country = tm.getNetworkCountryIso();
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        Check.require(sm != null, "no SubscriptionManager");
        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
        return "phoneType " + type + " simState " + sim + " operator '" + operator + "' country '" + country + "' subscriptions " + (subs == null ? "none" : subs.size());
    }
}
