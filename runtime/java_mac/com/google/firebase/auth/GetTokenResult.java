package com.google.firebase.auth;
import java.util.Collections;
import java.util.Map;
// Token wrapper. Apps that call getIdToken(true) read getToken() for
// the JWT to send to their backend. Ours is a placeholder string;
// any backend that verifies it via Google's public keys will reject
// it (which is the correct behavior -- we're not authenticated).
public final class GetTokenResult {
    private final String token;
    public GetTokenResult(String token) { this.token = token; }
    public String getToken()       { return token; }
    public String getSignInProvider() { return "anonymous"; }
    public Map<String, Object> getClaims() { return Collections.emptyMap(); }
    public long getExpirationTimestamp() { return Long.MAX_VALUE; }
    public long getAuthTimestamp()       { return 0L; }
    public long getIssuedAtTimestamp()   { return 0L; }
}
