package com.android.billingclient.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// com.android.billingclient.api.PurchaseHistoryRecord -- the trimmed receipt
// returned by queryPurchaseHistoryAsync (most-recent purchase per product, even
// expired/consumed). De-Googled device has no history, so we never produce one;
// the full getter surface ships so the type resolves in app signatures.
public class PurchaseHistoryRecord {
    private final String originalJson;
    private final String signature;
    private final JSONObject parsed;

    public PurchaseHistoryRecord(String jsonPurchaseInfo, String signature) throws JSONException {
        this.originalJson = jsonPurchaseInfo == null ? "" : jsonPurchaseInfo;
        this.signature = signature == null ? "" : signature;
        this.parsed = jsonPurchaseInfo == null || jsonPurchaseInfo.isEmpty()
                ? new JSONObject() : new JSONObject(jsonPurchaseInfo);
    }

    public List<String> getProducts() {
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
    public ArrayList<String> getSkus() { return new ArrayList<String>(getProducts()); }

    public long getPurchaseTime() { return parsed.optLong("purchaseTime", 0L); }

    public String getPurchaseToken() {
        String t = parsed.optString("purchaseToken", "");
        if (t.isEmpty()) t = parsed.optString("token", "");
        return t;
    }

    public int getQuantity() { return parsed.optInt("quantity", 1); }

    public String getDeveloperPayload() { return parsed.optString("developerPayload", ""); }

    public String getSignature() { return signature; }

    public String getOriginalJson() { return originalJson; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PurchaseHistoryRecord)) return false;
        PurchaseHistoryRecord p = (PurchaseHistoryRecord) o;
        return originalJson.equals(p.originalJson) && signature.equals(p.signature);
    }

    @Override public int hashCode() { return originalJson.hashCode(); }

    @Override public String toString() { return "PurchaseHistoryRecord. Json: " + originalJson; }
}
