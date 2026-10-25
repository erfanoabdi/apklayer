package com.google.android.gms.common.api;

import android.app.Activity;
import android.content.Context;

// com.google.android.gms.common.api.GoogleApi<O> -- the modern base class for
// the fluent GMS clients (FusedLocationProviderClient, SettingsClient,
// GamesSignInClient, ...). Feature buckets extend this and return Tasks that
// fail cleanly (SERVICE_MISSING / the API's documented failure) on a de-Googled
// device. We expose the public-facing surface a subclass needs (the Context it
// was built with, the Api, and the ApiOptions); the GoogleApiManager plumbing is
// play-services-internal and not part of the compile surface.
public abstract class GoogleApi<O extends Api.ApiOptions> {

    private final Context applicationContext;
    private final Api<O> api;
    private final O apiOptions;

    protected GoogleApi(Context context, Api<O> api, O options, Settings settings) {
        this.applicationContext = (context != null) ? context.getApplicationContext() : null;
        this.api = api;
        this.apiOptions = options;
    }

    protected GoogleApi(Context context, Api<O> api, O options) {
        this(context, api, options, Settings.DEFAULT_SETTINGS);
    }

    protected GoogleApi(Activity activity, Api<O> api, O options, Settings settings) {
        this((Context) activity, api, options, settings);
    }

    public Context getApplicationContext() { return applicationContext; }
    public Api<O> getApi() { return api; }
    public O getApiOptions() { return apiOptions; }

    // GoogleApi.Settings: configures the client's status-exception mapper and
    // dispatch Looper. Apps build it via the nested Builder; the defaults are
    // sufficient for our clean-unavailable clients.
    public static final class Settings {
        public static final Settings DEFAULT_SETTINGS = new Builder().build();

        public final android.os.Looper looper;

        private Settings(android.os.Looper looper) { this.looper = looper; }

        public static final class Builder {
            private android.os.Looper looper;
            public Builder setLooper(android.os.Looper looper) { this.looper = looper; return this; }
            public Settings build() { return new Settings(looper); }
        }
    }
}
