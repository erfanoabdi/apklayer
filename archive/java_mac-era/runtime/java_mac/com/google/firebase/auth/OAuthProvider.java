package com.google.firebase.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// OAuthProvider: generic OAuth provider (Microsoft, Apple, Yahoo, plus
// the well-known providers). Faithful surface from the Firebase ref:
// builder-based configuration (scopes + custom parameters) plus the
// static getCredential factories. The sign-in flow that consumes a
// configured OAuthProvider (FirebaseAuth.startActivityForSignInWithProvider)
// is the same anonymous-success path the rest of FirebaseAuth uses
// (no real OAuth round-trip).
public final class OAuthProvider extends FederatedAuthProvider {
    private final String providerId;
    private final List<String> scopes;
    private final Map<String, String> customParameters;

    private OAuthProvider(String providerId, List<String> scopes,
                          Map<String, String> customParameters) {
        this.providerId = providerId;
        this.scopes = scopes;
        this.customParameters = customParameters;
    }

    public String getProviderId() { return providerId; }
    public List<String> getScopes() { return scopes; }
    public Map<String, String> getCustomParameters() { return customParameters; }

    public static Builder newBuilder(String providerId) {
        return new Builder(providerId);
    }
    public static Builder newBuilder(String providerId, FirebaseAuth firebaseAuth) {
        return new Builder(providerId);
    }

    // Direct credential factories (when the app already holds the OAuth
    // tokens, e.g. from a third-party SDK).
    public static OAuthCredential getCredential(String providerId, String idToken, String accessToken) {
        return new OAuthAuthCredential(providerId, providerId, idToken, accessToken, null);
    }

    public static CredentialBuilder newCredentialBuilder(String providerId) {
        return new CredentialBuilder(providerId);
    }

    public static final class Builder {
        private final String providerId;
        private final List<String> scopes = new ArrayList<>();
        private final Map<String, String> customParameters = new HashMap<>();

        Builder(String providerId) { this.providerId = providerId; }

        public Builder setScopes(List<String> scopes) {
            this.scopes.clear();
            if (scopes != null) this.scopes.addAll(scopes);
            return this;
        }
        public Builder addCustomParameter(String key, String value) {
            customParameters.put(key, value);
            return this;
        }
        public Builder addCustomParameters(Map<String, String> parameters) {
            if (parameters != null) customParameters.putAll(parameters);
            return this;
        }
        public OAuthProvider build() {
            return new OAuthProvider(providerId, scopes, customParameters);
        }
    }

    // CredentialBuilder lets apps assemble an OAuthCredential from
    // id token + access token + raw nonce. Mirrors the Firebase ref.
    public static final class CredentialBuilder {
        private final String providerId;
        private String idToken;
        private String accessToken;
        private String rawNonce;

        CredentialBuilder(String providerId) { this.providerId = providerId; }

        public CredentialBuilder setIdToken(String idToken) { this.idToken = idToken; return this; }
        public CredentialBuilder setAccessToken(String accessToken) { this.accessToken = accessToken; return this; }
        public CredentialBuilder setIdTokenWithRawNonce(String idToken, String rawNonce) {
            this.idToken = idToken; this.rawNonce = rawNonce; return this;
        }
        public OAuthCredential build() {
            return new OAuthAuthCredential(providerId, providerId, idToken, accessToken, rawNonce);
        }
    }
}
