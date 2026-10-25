package com.android.billingclient.api;

// com.android.billingclient.api.AccountIdentifiers -- obfuscated account/profile
// ids attached to a Purchase. We never produce purchases, but the type must
// exist so Purchase.getAccountIdentifiers() resolves.
public final class AccountIdentifiers {
    private final String obfuscatedAccountId;
    private final String obfuscatedProfileId;

    AccountIdentifiers(String obfuscatedAccountId, String obfuscatedProfileId) {
        this.obfuscatedAccountId = obfuscatedAccountId;
        this.obfuscatedProfileId = obfuscatedProfileId;
    }

    public String getObfuscatedAccountId() { return obfuscatedAccountId; }
    public String getObfuscatedProfileId() { return obfuscatedProfileId; }
}
