package com.google.firebase.auth;

import java.util.List;

// SignInMethodQueryResult: the result of
// FirebaseAuth.fetchSignInMethodsForEmail. Faithful surface from the
// Firebase ref (getSignInMethods()). With no backend we always carry an
// empty list (the honest "no methods known" answer). The real SDK type
// is abstract with a hidden impl; we ship a concrete carrier since we
// construct it ourselves and apps only ever read getSignInMethods().
public final class SignInMethodQueryResult {
    private final List<String> methods;

    SignInMethodQueryResult(List<String> methods) { this.methods = methods; }

    public List<String> getSignInMethods() { return methods; }
}
