// Shadow the obfuscated AdSize. APKs minified by ProGuard/R8 with Google
// Ads SDK rename AdSize to "g" inside their classes.dex; the parent
// ClassLoader hands out THIS empty version instead of the real
// minified one, breaking the chain of <clinit>'s that pull in network
// stack and other heavy SDK code we never want to run.
//
// We extend AdSize so the AdView constructor that takes an AdSize
// argument can accept this too.
package com.google.ads;

public class g extends AdSize {
    // Obfuscated names for the AdSize public static fields. We can't
    // know which letter ProGuard chose without disassembling, but the
    // app code that walked them showed `g.a` as the BANNER analogue.
    // Add the common single-letter aliases so apps compiled against
    // any of them resolve.
    public static final g a = new g();
    public static final g b = new g();
    public static final g c = new g();
    public static final g d = new g();
    public static final g e = new g();

    public g() { super(); }
    public g(int width, int height) { super(width, height); }
}
