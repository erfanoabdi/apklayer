// Shadow the obfuscated AdRequest. ProGuard/R8 renames AdRequest to "d"
// in this APK; the parent loader hands out THIS empty version so
// AdView.a(d) can resolve. We extend AdRequest so any signatures that
// take the original (non-obfuscated) class still match.
package com.google.ads;
public class d extends AdRequest {
    public d() { super(); }
}
