package com.google.android.gms.auth.api.signin;

import android.accounts.Account;
import android.net.Uri;

import com.google.android.gms.common.api.Scope;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// com.google.android.gms.auth.api.signin.GoogleSignInAccount -- the value object
// describing a signed-in Google account. On a de-Googled / no-Play-services
// device (Mac.apk's policy) there is NEVER a real signed-in account: the
// GoogleSignIn entry points return null / fail their Tasks with
// SIGN_IN_REQUIRED, so the framework itself never manufactures a populated
// instance. We still ship the full getter surface so apps that hold a reference
// (e.g. from a deserialized cache) compile and read honest values.
//
// Ref: developers.google.com/android/reference/com/google/android/gms/auth/api/
// signin/GoogleSignInAccount . All getters are nullable on real Android when the
// corresponding scope/field was not requested; we mirror that by defaulting to
// null / empty. We intentionally do NOT forge an id token or server auth code
// (owner policy: never impersonate Google).
public class GoogleSignInAccount {

    private final String id;
    private final String tokenId;            // id token (JWT) -- always null here
    private final String email;
    private final String displayName;
    private final String givenName;
    private final String familyName;
    private final Uri photoUrl;
    private final String serverAuthCode;
    private final Account account;
    private final Set<Scope> grantedScopes;
    private final Set<Scope> requestedScopes;

    // Package-private full constructor. The framework never builds a "real"
    // (signed-in) account; this exists only so a cached/deserialized account can
    // be reconstructed faithfully. Apps construct accounts via GoogleSignIn, not
    // directly (the real class has no public constructor either).
    GoogleSignInAccount(String id, String tokenId, String email, String displayName,
                        String givenName, String familyName, Uri photoUrl,
                        String serverAuthCode, Account account,
                        Set<Scope> grantedScopes, Set<Scope> requestedScopes) {
        this.id = id;
        this.tokenId = tokenId;
        this.email = email;
        this.displayName = displayName;
        this.givenName = givenName;
        this.familyName = familyName;
        this.photoUrl = photoUrl;
        this.serverAuthCode = serverAuthCode;
        this.account = account;
        this.grantedScopes = grantedScopes != null
                ? new HashSet<Scope>(grantedScopes) : new HashSet<Scope>();
        this.requestedScopes = requestedScopes != null
                ? new HashSet<Scope>(requestedScopes) : new HashSet<Scope>();
    }

    public String getId()             { return id; }
    public String getIdToken()        { return tokenId; }
    public String getEmail()          { return email; }
    public String getDisplayName()    { return displayName; }
    public String getGivenName()      { return givenName; }
    public String getFamilyName()     { return familyName; }
    public Uri getPhotoUrl()          { return photoUrl; }
    public String getServerAuthCode() { return serverAuthCode; }

    // Returns the platform Account if the account has been associated with one
    // (real Android requires the email to be present). No account here -> null.
    public Account getAccount() {
        if (account != null) return account;
        return email != null ? new Account(email, "com.google") : null;
    }

    public Set<Scope> getGrantedScopes()   { return Collections.unmodifiableSet(grantedScopes); }
    public Set<Scope> getRequestedScopes() { return Collections.unmodifiableSet(requestedScopes); }

    public boolean isExpired() { return true; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoogleSignInAccount)) return false;
        GoogleSignInAccount a = (GoogleSignInAccount) o;
        return (id == null ? a.id == null : id.equals(a.id))
                && grantedScopes.equals(a.grantedScopes);
    }

    @Override public int hashCode() {
        int h = id == null ? 0 : id.hashCode();
        return 31 * h + grantedScopes.hashCode();
    }
}
