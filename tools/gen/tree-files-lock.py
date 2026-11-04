#!/usr/bin/env python3
"""gen-tree-files-lock: lists every file the arm64 route takes from the LineageOS build (~/los24 in OrbStack) and stages on the VM,
with sha256, size and where it came from, into deps/android-tree-files.lock (committed), so nothing is forgotten or unreproducible.
Run on the Mac:  tools/gen/tree-files-lock.py   (reads the VM over ssh, resolves origins in OrbStack, never writes to either)
Each line: <sha256> <size> <path on the VM, relative to ~> <origin in the tree> <status>
status: same (identical to the tree's file), patched (differs: see the note column), apklayer (built from vendor/apklayer), unresolved.
APKLAYER_ARCH=x86_64 PREBUILTS=<prebuilts branch checkout>: the x86_64 set instead (its x86_64/android-libs and x86_64/android-root, local files; product gsi_x86_64)
into deps/android-tree-files-x86_64.lock."""
import hashlib, subprocess, sys, datetime, pathlib

import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tests", "vm"))
import config
VM = config.VM
SSH = config.auth("ssh") + [VM]
ARCH = os.environ.get("APKLAYER_ARCH", "arm64")
X86 = ARCH == "x86_64"
OUT = "out/target/product/generic_" + ARCH
LIBS, ROOTP = ("x86_64/android-libs/lib64/", "x86_64/android-root/") if X86 else ("android-arm64/lib64/", "arm64-native/android-root/")
VARIANT = "x86_64" if X86 else "arm64_armv8-a"

def sh(cmd, orb=False):
    c = ["orb", "-m", "ubuntu22-x86", "bash", "-c", cmd] if orb else SSH + [cmd]
    return subprocess.run(c, capture_output=True, text=True, check=True).stdout

print("Hashing staged Android artifacts", flush=True)
# what lives on the VM: the Android libraries, and the other tree-derived files
if X86:  # the prebuilts branch's x86_64 files, here on the Mac
    base = pathlib.Path(os.environ["PREBUILTS"]).expanduser()
    files = [f for d in ("x86_64/android-libs/lib64", "x86_64/android-root") for f in (base / d).rglob("*") if f.is_file() and not f.is_symlink()]
    vm = "".join("%s %s\n%d %s\n" % (hashlib.sha256(f.read_bytes()).hexdigest(), f.relative_to(base), f.stat().st_size, f.relative_to(base)) for f in files)
else: vm = sh("cd ~ && { find android-arm64/lib64 -type f; find arm64-native/aosp/android-all arm64-native/aosp/Robolectric_all.jar arm64-native/android-root -path arm64-native/android-root/data -prune -o -type f -print; } | xargs sha256sum | sed 's/  / /' ; "
        "find android-arm64/lib64 arm64-native/aosp/android-all arm64-native/aosp/Robolectric_all.jar arm64-native/android-root -path arm64-native/android-root/data -prune -o -type f -printf '%s %p\\n'")
sums, sizes = {}, {}
for line in vm.splitlines():
    a, b = line.split(" ", 1)
    if len(a) == 64: sums[b] = a
    else: sizes[b] = int(a)

print("Resolving build-output origins", flush=True)
# the tree's sha256 of the candidate origins, computed once in OrbStack
names = sorted({p.split("/")[-1] for p in sums if p.startswith(LIBS)})
orb = sh("cd ~/los24 && for n in %s; do f=%s/system/lib64/$n; [ -f $f ] && sha256sum $f; done; "
         "for n in %s; do for f in %s/apex/*/lib64/$n %s/apex/*/lib64/extractors/$n; do [ -f $f ] && sha256sum $f; done; done; "
         "find out/soong/.intermediates/vendor/apklayer -name '*.so' -path '*android*_%s_shared/*' -not -path '*_x86_x86_64_*' -not -path '*unstripped*' -not -path '*before_final*' | xargs sha256sum; "
         "find out/apklayer-vulkan%s -maxdepth 2 -name '*.so' -type f 2>/dev/null | xargs -r sha256sum; "
         "sha256sum out/soong/.intermediates/hardware/interfaces/audio/aidl/default/libeffectconfig/android_vendor_%s_shared/libeffectconfig.so; "
         "sha256sum frameworks/av/services/audiopolicy/config/audio_policy_volumes.xml frameworks/av/services/audiopolicy/config/default_volume_tables.xml; "
         "grep -m3 'ro.build.fingerprint=\\|ro.build.date=\\|ro.build.version.release=' %s/system/build.prop" % (" ".join(names), OUT, " ".join(names), OUT, OUT, VARIANT, "-x86_64" if X86 else "", VARIANT, OUT), orb=True)
tree, apk, ident = {}, {}, []
for line in orb.splitlines():
    if line.startswith("ro."): ident.append(line); continue
    h, p = line.split("  ", 1)
    d = apk if "vendor/apklayer" in p or p.startswith("out/apklayer-vulkan") else tree
    if p.split("/")[-1] not in d or "/system/lib64/" in p: d[p.split("/")[-1]] = (h, p)  # system/lib64 wins over a same-named APEX copy

rows = []
for p in sorted(sums):
    n, h = p.split("/")[-1], sums[p]
    if p.startswith(LIBS):
        if n == "libapklayer_vulkan.so":
            origin, st = tree["libvulkan.so"][1] + " (renamed; patches/aosp/frameworks/native/0003-vulkan-host-dispatch.patch)", "patched"
        elif n in apk and apk[n][0] == h: origin, st = apk[n][1], "apklayer"
        elif n in tree and tree[n][0] == h: origin, st = tree[n][1], "same"
        elif n in tree: origin, st = tree[n][1], "patched"
        elif n in apk: origin, st = apk[n][1], "patched"
        else: origin, st = "-", "unresolved"
    else:
        origin, st = "(staged from the tree's build outputs, see CHANGELOG.md)", "same"
        if "/android-all/" in p and p.endswith(".jar"):
            origin, st = "(tree framework with apklayer JNI shim; tools/build native)", "patched"
        if p.startswith(ROOTP + "vendor/etc/") and n in ("audio_policy_volumes.xml", "default_volume_tables.xml"):
            origin, st = tree[n][1], "same" if tree[n][0] == h else "patched"
        prefix = ROOTP
        if p.startswith(prefix):
            own = pathlib.Path(__file__).resolve().parent.parent.parent / "bridge/native/android-root" / p[len(prefix):]
            if own.is_file() and hashlib.sha256(own.read_bytes()).hexdigest() == h:
                origin, st = str(own.relative_to(pathlib.Path(__file__).resolve().parent.parent.parent)), "apklayer"
    rows.append((h, sizes[p], p, origin, st))

text = ["# Files the %s route takes from the LineageOS build (generated by tools/gen/tree-files-lock.py; do not edit)." % ARCH,
        "# Tree: ~/los24 (OrbStack), product gsi_%s userdebug. " % ARCH + "; ".join(ident),
        "# Generated: " + datetime.date.today().isoformat(), "# <sha256> <size> <path %s> <origin under ~/los24> <status>" % ("in the prebuilts branch" if X86 else "under ~ on the VM")]
text += ["%s %d %s %s %s" % r for r in rows]
path = pathlib.Path(__file__).resolve().parent.parent.parent / "deps" / ("android-tree-files-x86_64.lock" if X86 else "android-tree-files.lock")
path.write_text("\n".join(text) + "\n")
by = {}
for r in rows: by[r[4]] = by.get(r[4], 0) + 1
print(len(rows), "files,", by, "->", path)
