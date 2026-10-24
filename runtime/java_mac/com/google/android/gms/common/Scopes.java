package com.google.android.gms.common;

// com.google.android.gms.common.Scopes -- canonical OAuth scope URI string
// constants apps pass to GoogleSignInOptions.Builder.requestScopes(new
// Scope(Scopes.EMAIL)). Values are fixed by Google's OAuth endpoints and must
// match exactly.
public final class Scopes {
    private Scopes() {}

    public static final String PROFILE = "profile";
    public static final String EMAIL = "email";
    public static final String OPEN_ID = "openid";
    public static final String PLUS_LOGIN = "https://www.googleapis.com/auth/plus.login";
    public static final String PLUS_ME = "https://www.googleapis.com/auth/plus.me";
    public static final String GAMES = "https://www.googleapis.com/auth/games";
    public static final String GAMES_LITE = "https://www.googleapis.com/auth/games_lite";
    public static final String CLOUD_SAVE = "https://www.googleapis.com/auth/datastoremobile";
    public static final String APP_STATE = "https://www.googleapis.com/auth/appstate";
    public static final String DRIVE_FILE = "https://www.googleapis.com/auth/drive.file";
    public static final String DRIVE_APPFOLDER = "https://www.googleapis.com/auth/drive.appdata";
    public static final String DRIVE_FULL = "https://www.googleapis.com/auth/drive";
    public static final String DRIVE_APPS = "https://www.googleapis.com/auth/drive.apps";
}
