package com.android.billingclient.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// com.android.billingclient.api.Purchase -- a record of an in-app purchase.
// Real Play Billing constructs these from the JSON receipt the Play Store
// returns. On a de-Googled device no purchase ever exists, so Mac.apk never
// hands one back from any query. We still ship the full getter surface (apps
// reference Purchase fields in their type signatures / receipt-validation code)
// and a faithful JSON-parsing constructor for the rare app that serializes a
// previously-cached receipt and reconstructs a Purchase from it. We do NOT
// fabricate entitlements.
public class Purchase {
    private final String originalJson;
    private final String signature;
    private final JSONObject parsed;

    // com.android.billingclient.api.Purchase.PurchaseState -- IntDef in the real
    // SDK; shipped as a constants holder so apps reference PurchaseState.PURCHASED.
    public static final class PurchaseState {
        private PurchaseState() {}
        public static final int UNSPECIFIED_STATE = 0;
        public static final int PURCHASED = 1;
        public static final int PENDING = 2;
    }

    public Purchase(String jsonPurchaseInfo, String signature) throws JSONException {
        this.originalJson = jsonPurchaseInfo == null ? "" : jsonPurchaseInfo;
        this.signature = signature == null ? "" : signature;
        this.parsed = jsonPurchaseInfo == null || jsonPurchaseInfo.isEmpty()
                ? new JSONObject() : new JSONObject(jsonPurchaseInfo);
    }

    public String getOrderId() {
        String s = parsed.optString("orderId", "");
        return s.isEmpty() ? null : s;
    }

    public String getPackageName() { return parsed.optString("packageName", ""); }

    public List<String> getProducts() {
        // v5+ field "productIds" (array); older receipts used a single
        // "productId". Mirror the real client which reads both.
        JSONArray arr = parsed.optJSONArray("productIds");
        List<String> out = new ArrayList<String>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } else {
            String single = parsed.optString("productId", "");
            if (!single.isEmpty()) out.add(single);
        }
        return Collections.unmodifiableList(out);
    }

    /** @deprecated replaced by {@link #getProducts()} in Billing v5. */
    @Deprecated
    public ArrayList<String> getSkus() {
        return new ArrayList<String>(getProducts());
    }

    public long getPurchaseTime() { return parsed.optLong("purchaseTime", 0L); }

    public String getPurchaseToken() {
        // Receipts use "token" (old) or "purchaseToken" (current).
        String t = parsed.optString("purchaseToken", "");
        if (t.isEmpty()) t = parsed.optString("token", "");
        return t;
    }

    public int getPurchaseState() {
        // Receipt encodes 0=Purchased,4=Pending in the wire format; the public
        // PurchaseState enum maps those to PURCHASED/PENDING. Mirror the client.
        int wire = parsed.optInt("purchaseState", 0);
        return wire == 4 ? PurchaseState.PENDING : PurchaseState.PURCHASED;
    }

    public int getQuantity() { return parsed.optInt("quantity", 1); }

    public String getDeveloperPayload() { return parsed.optString("developerPayload", ""); }

    public String getSignature() { return signature; }

    public String getOriginalJson() { return originalJson; }

    public boolean isAcknowledged() { return parsed.optBoolean("acknowledged", true); }

    public boolean isAutoRenewing() { return parsed.optBoolean("autoRenewing", false); }

    public AccountIdentifiers getAccountIdentifiers() {
        String acct = parsed.optString("obfuscatedAccountId", "");
        String prof = parsed.optString("obfuscatedProfileId", "");
        if (acct.isEmpty() && prof.isEmpty()) return null;
        return new AccountIdentifiers(acct.isEmpty() ? null : acct,
                                      prof.isEmpty() ? null : prof);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Purchase)) return false;
        Purchase p = (Purchase) o;
        return originalJson.equals(p.originalJson) && signature.equals(p.signature);
    }

    @Override public int hashCode() { return originalJson.hashCode(); }

    @Override public String toString() { return "Purchase. Json: " + originalJson; }
}
