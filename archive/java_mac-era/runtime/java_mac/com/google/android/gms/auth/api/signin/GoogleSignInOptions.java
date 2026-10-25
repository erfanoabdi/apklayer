package com.google.android.gms.auth.api.signin;

import android.accounts.Account;

import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.Scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// com.google.android.gms.auth.api.signin.GoogleSignInOptions -- the immutable
// configuration the app builds to describe which Google account data it wants
// (email, profile, id token, server auth code, extra scopes). Built via the
// nested Builder; GoogleSignIn.getClient(context, options) turns it into a
// GoogleSignInClient.
//
// It implements Api.ApiOptions.Optional so a GoogleSignInClient can be a
// GoogleApi<GoogleSignInOptions> (the real class implements Optional +
// HasGoogleSignInAccountOptions; we drop the latter marker because it would
// reference play-services-internal types not on our compile classpath, and no
// app branches on it).
//
// Ref: developers.google.com/android/reference/com/google/android/gms/auth/api/
// signin/GoogleSignInOptions and .../GoogleSignInOptions.Builder
public class GoogleSignInOptions implements Api.ApiOptions.Optional {

    // Standard sign-in scope set (email + profile id). The DEFAULT presets the
    // real SDK exposes.
    public static final GoogleSignInOptions DEFAULT_SIGN_IN =
            new Builder().requestId().requestProfile().build();

    public static final GoogleSignInOptions DEFAULT_GAMES_SIGN_IN =
            new Builder().requestScopes(new Scope("https://www.googleapis.com/auth/games_lite")).build();

    private final List<Scope> scopes;
    private final Account account;
    private final boolean idTokenRequested;
    private final boolean serverAuthCodeRequested;
    private final boolean forceCodeForRefreshToken;
    private final String serverClientId;
    private final String hostedDomain;
    private final boolean emailRequested;

    private GoogleSignInOptions(List<Scope> scopes, Account account,
                                boolean idTokenRequested, boolean serverAuthCodeRequested,
                                boolean forceCodeForRefreshToken, String serverClientId,
                                String hostedDomain, boolean emailRequested) {
        this.scopes = scopes;
        this.account = account;
        this.idTokenRequested = idTokenRequested;
        this.serverAuthCodeRequested = serverAuthCodeRequested;
        this.forceCodeForRefreshToken = forceCodeForRefreshToken;
        this.serverClientId = serverClientId;
        this.hostedDomain = hostedDomain;
        this.emailRequested = emailRequested;
    }

    public Scope[] getScopeArray() { return scopes.toArray(new Scope[0]); }

    public ArrayList<Scope> getScopes() { return new ArrayList<Scope>(scopes); }

    // The account the app pinned via setAccountName; null when none was set
    // (the common case). Note this is the platform Account, NOT a signed-in
    // GoogleSignInAccount.
    public Account getAccount() { return account; }

    public String getServerClientId() { return serverClientId; }

    public String getAccountName() { return account != null ? account.name : null; }

    public String getHostedDomain() { return hostedDomain; }

    public boolean isIdTokenRequested() { return idTokenRequested; }

    public boolean isServerAuthCodeRequested() { return serverAuthCodeRequested; }

    public boolean isForceCodeForRefreshToken() { return forceCodeForRefreshToken; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoogleSignInOptions)) return false;
        GoogleSignInOptions g = (GoogleSignInOptions) o;
        return idTokenRequested == g.idTokenRequested
                && serverAuthCodeRequested == g.serverAuthCodeRequested
                && new HashSet<Scope>(scopes).equals(new HashSet<Scope>(g.scopes))
                && (serverClientId == null ? g.serverClientId == null
                                           : serverClientId.equals(g.serverClientId))
                && (account == null ? g.account == null : account.equals(g.account));
    }

    @Override public int hashCode() {
        int h = new HashSet<Scope>(scopes).hashCode();
        h = 31 * h + (account == null ? 0 : account.hashCode());
        h = 31 * h + (serverClientId == null ? 0 : serverClientId.hashCode());
        h = 31 * h + (idTokenRequested ? 1 : 0);
        h = 31 * h + (serverAuthCodeRequested ? 1 : 0);
        return h;
    }

    // com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder --
    // fluent builder. Each requestX accumulates configuration; build() snapshots
    // it. Matches the real builder signatures exactly so apps' chained calls
    // resolve.
    public static final class Builder {
        private final Set<Scope> scopes = new HashSet<Scope>();
        private Account account;
        private boolean idTokenRequested;
        private boolean serverAuthCodeRequested;
        private boolean forceCodeForRefreshToken;
        private String serverClientId;
        private String hostedDomain;
        private boolean emailRequested;

        public Builder() {}

        public Builder(GoogleSignInOptions options) {
            if (options != null) {
                this.scopes.addAll(options.scopes);
                this.account = options.account;
                this.idTokenRequested = options.idTokenRequested;
                this.serverAuthCodeRequested = options.serverAuthCodeRequested;
                this.forceCodeForRefreshToken = options.forceCodeForRefreshToken;
                this.serverClientId = options.serverClientId;
                this.hostedDomain = options.hostedDomain;
                this.emailRequested = options.emailRequested;
            }
        }

        public Builder requestEmail() {
            scopes.add(new Scope(Scopes.EMAIL));
            emailRequested = true;
            return this;
        }

        public Builder requestId() {
            scopes.add(new Scope("openid"));
            return this;
        }

        public Builder requestProfile() {
            scopes.add(new Scope(Scopes.PROFILE));
            return this;
        }

        public Builder requestIdToken(String serverClientId) {
            this.idTokenRequested = true;
            this.serverClientId = checkClientId(serverClientId);
            return this;
        }

        public Builder requestServerAuthCode(String serverClientId) {
            return requestServerAuthCode(serverClientId, false);
        }

        public Builder requestServerAuthCode(String serverClientId, boolean forceCodeForRefreshToken) {
            this.serverAuthCodeRequested = true;
            this.serverClientId = checkClientId(serverClientId);
            this.forceCodeForRefreshToken = forceCodeForRefreshToken;
            return this;
        }

        public Builder requestScopes(Scope scope, Scope... scopesArr) {
            scopes.add(scope);
            if (scopesArr != null) Collections.addAll(scopes, scopesArr);
            return this;
        }

        public Builder setAccountName(String accountName) {
            this.account = new Account(checkNotEmpty(accountName), "com.google");
            return this;
        }

        public Builder setHostedDomain(String hostedDomain) {
            this.hostedDomain = checkNotEmpty(hostedDomain);
            return this;
        }

        public Builder addExtension(GoogleSignInOptionsExtension extension) {
            if (extension != null && extension.getImpliedScopes() != null) {
                scopes.addAll(extension.getImpliedScopes());
            }
            return this;
        }

        public GoogleSignInOptions build() {
            // The real builder, when both openid and the profile/email pair are
            // requested with an id token, ensures the openid scope is present;
            // we mirror only the public-observable scope accumulation.
            List<Scope> list = new ArrayList<Scope>(scopes);
            // Deterministic order so equals/hashCode are stable across builds.
            Collections.sort(list, new Comparator<Scope>() {
                @Override public int compare(Scope a, Scope b) {
                    return a.getScopeUri().compareTo(b.getScopeUri());
                }
            });
            return new GoogleSignInOptions(list, account, idTokenRequested,
                    serverAuthCodeRequested, forceCodeForRefreshToken,
                    serverClientId, hostedDomain, emailRequested);
        }

        private static String checkClientId(String id) {
            if (id == null) throw new IllegalArgumentException("the server client id cannot be null");
            return id;
        }
        private static String checkNotEmpty(String s) {
            if (s == null || s.isEmpty()) throw new IllegalArgumentException("Given String is empty or null");
            return s;
        }
    }
}
