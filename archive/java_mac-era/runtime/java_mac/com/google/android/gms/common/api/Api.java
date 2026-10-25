package com.google.android.gms.common.api;

import android.accounts.Account;

// com.google.android.gms.common.api.Api<O> -- the descriptor object that names a
// GMS API surface (LocationServices.API, Auth.GOOGLE_SIGN_IN_API, etc.). Apps
// reference Api and its nested ApiOptions marker hierarchy when building a
// GoogleApiClient or a GoogleApi-derived client; the option markers tag whether
// an API needs configuration. We expose the public-facing shape only -- the
// client-builder internals (Api.Client / AbstractClientBuilder) are
// play-services-internal and not part of the surface apps compile against.
public final class Api<O extends Api.ApiOptions> {

    private final String name;

    public Api(String name) { this.name = name; }

    // Real public GMS ctor: apps' R8'd code constructs Api(name, clientBuilder,
    // clientKey). The original shim omitted this as "internal", but the broad
    // sweep proved apps DO call it (subway/crossy threw NoSuchMethodError on
    // Api(String, AbstractClientBuilder, ClientKey)). Store the name; the builder
    // and key are inert (no real GMS service backs this device).
    public Api(String name, AbstractClientBuilder<?, O> clientBuilder, ClientKey<?> clientKey) {
        this.name = name;
    }

    public String getName() { return name; }

    @Override public String toString() { return "Api{name=" + name + "}"; }

    // Client-builder internals. Real play-services keeps these in
    // com.google.android.gms.common.api.Api; apps reference them when registering
    // an Api with a GoogleApiClient. Inert markers here.
    public interface Client {}
    public abstract static class AbstractClientBuilder<T extends Client, O> {
        public AbstractClientBuilder() {}
    }
    public static final class ClientKey<C extends Client> {}

    // Marker hierarchy that classifies an API's configuration requirements.
    // The interfaces are empty markers in real play-services; HasAccountOptions
    // carries an account accessor.
    public interface ApiOptions {
        NoOptions NO_OPTIONS = new NoOptions();

        interface HasOptions extends ApiOptions {}
        interface NotRequiredOptions extends ApiOptions {}
        interface Optional extends HasOptions, NotRequiredOptions {}

        interface HasAccountOptions extends HasOptions, NotRequiredOptions {
            Account getAccount();
        }

        final class NoOptions implements NotRequiredOptions {
            private NoOptions() {}
        }
    }
}
