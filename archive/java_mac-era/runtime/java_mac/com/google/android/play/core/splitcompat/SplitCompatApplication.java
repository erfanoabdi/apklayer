package com.google.android.play.core.splitcompat;

import android.content.Context;

// com.google.android.play.core.splitcompat.SplitCompatApplication -- a drop-in
// Application base class that calls SplitCompat.install for the app. Apps that
// declare android:name="...SplitCompatApplication" (or subclass it) get split
// support without writing attachBaseContext themselves.
//
// We extend our android.app.Application shadow and call our no-op SplitCompat
// .install in attachBaseContext, exactly mirroring the published Play Core class
// shape. Because our install() is a successful no-op (the full classpath is
// already present), this just lets such apps boot normally.
public class SplitCompatApplication extends android.app.Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        SplitCompat.install(this);
    }
}
