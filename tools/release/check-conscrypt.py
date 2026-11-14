#!/usr/bin/env python3
"""check-conscrypt.py <jar>...: fails unless each jar's com/android/org/conscrypt/NativeCryptoJni.class is the patched one (1343 bytes, runtime/patches;
the unpatched class is 371 bytes: Conscrypt fails to load and every WebView https load fails; the 0.4.18 bundle shipped it).
The prebuilts' and the bundle's raw android-all jars hold the unpatched class by design: setup-runtime.sh (a deb install) and meson install put the patched one in;
run this on the set-up native dir's aosp/android-all/*.jar, never on the bundle or the prebuilts."""
import sys, zipfile

PATCHED = 1343
bad = 0
for jar in sys.argv[1:]:
    size = zipfile.ZipFile(jar).getinfo("com/android/org/conscrypt/NativeCryptoJni.class").file_size
    print(f"{jar}: NativeCryptoJni {size} bytes" + ("" if size == PATCHED else f" (want {PATCHED})"))
    bad |= size != PATCHED
sys.exit(bad)
