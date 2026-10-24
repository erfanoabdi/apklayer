package com.google.firebase;

// FirebaseOptions stub. Real Firebase parses google-services.json at
// build time and bakes the values into res/values/strings.xml; the
// SDK reads them via Resources.getIdentifier + getString. We don't
// need those values for anything (no network traffic), but we keep
// the API surface so apps that explicitly construct a FirebaseOptions
// via the Builder don't crash.
//
// All fields are stored verbatim and returned via getters. None of
// them are used by our stubs to make decisions -- they're held for
// completeness because some apps log them or assert on them.
public final class FirebaseOptions {
    private final String apiKey;
    private final String applicationId;
    private final String databaseUrl;
    private final String gaTrackingId;
    private final String gcmSenderId;
    private final String storageBucket;
    private final String projectId;

    private FirebaseOptions(Builder b) {
        this.apiKey         = b.apiKey;
        this.applicationId  = b.applicationId;
        this.databaseUrl    = b.databaseUrl;
        this.gaTrackingId   = b.gaTrackingId;
        this.gcmSenderId    = b.gcmSenderId;
        this.storageBucket  = b.storageBucket;
        this.projectId      = b.projectId;
    }

    public String getApiKey()         { return apiKey; }
    public String getApplicationId()  { return applicationId; }
    public String getDatabaseUrl()    { return databaseUrl; }
    public String getGaTrackingId()   { return gaTrackingId; }
    public String getGcmSenderId()    { return gcmSenderId; }
    public String getStorageBucket()  { return storageBucket; }
    public String getProjectId()      { return projectId; }

    /** AOSP-Firebase exposes a static fromResource(Context) that looks
     *  up the config strings from android-generated R.string.*. We
     *  honor the same contract -- read from Resources via
     *  getIdentifier + getString. Returns null when google_app_id is
     *  not present (matches the real SDK). Our Resources stub now
     *  serves strings from values/strings.xml, so this works for any
     *  APK that included google-services.json at build time. */
    public static FirebaseOptions fromResource(android.content.Context context) {
        if (context == null) return null;
        android.content.res.Resources res = context.getResources();
        if (res == null) return null;
        String pkg = context.getPackageName();
        String appId = readString(res, pkg, "google_app_id");
        if (appId == null || appId.isEmpty()) return null;
        return new Builder()
            .setApplicationId(appId)
            .setApiKey(readString(res, pkg, "google_api_key"))
            .setDatabaseUrl(readString(res, pkg, "firebase_database_url"))
            .setGcmSenderId(readString(res, pkg, "gcm_defaultSenderId"))
            .setStorageBucket(readString(res, pkg, "google_storage_bucket"))
            .setProjectId(readString(res, pkg, "project_id"))
            .build();
    }
    private static String readString(android.content.res.Resources res,
                                      String pkg, String name) {
        int id = res.getIdentifier(name, "string", pkg);
        return id != 0 ? res.getString(id) : null;
    }

    public static final class Builder {
        String apiKey;
        String applicationId;
        String databaseUrl;
        String gaTrackingId;
        String gcmSenderId;
        String storageBucket;
        String projectId;

        public Builder() {}
        public Builder(FirebaseOptions opts) {
            if (opts == null) return;
            this.apiKey         = opts.apiKey;
            this.applicationId  = opts.applicationId;
            this.databaseUrl    = opts.databaseUrl;
            this.gaTrackingId   = opts.gaTrackingId;
            this.gcmSenderId    = opts.gcmSenderId;
            this.storageBucket  = opts.storageBucket;
            this.projectId      = opts.projectId;
        }

        public Builder setApiKey(String v)        { this.apiKey         = v; return this; }
        public Builder setApplicationId(String v) { this.applicationId  = v; return this; }
        public Builder setDatabaseUrl(String v)   { this.databaseUrl    = v; return this; }
        public Builder setGaTrackingId(String v)  { this.gaTrackingId   = v; return this; }
        public Builder setGcmSenderId(String v)   { this.gcmSenderId    = v; return this; }
        public Builder setStorageBucket(String v) { this.storageBucket  = v; return this; }
        public Builder setProjectId(String v)     { this.projectId      = v; return this; }

        public FirebaseOptions build() { return new FirebaseOptions(this); }
    }
}
