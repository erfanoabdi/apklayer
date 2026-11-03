#!/usr/bin/env python3
"""install-media-libs: the tree's software codecs (libcodec2_soft_*) and container extractors from the swcodec / media APEX build outputs
(read-only in ~/los24 under OrbStack) to the VM's Android library dir (its ~/.config/apklayer/env, else ~/android-arm64/lib64), with the libraries they need that are not there yet.
Existing files are never overwritten (the platform copies of shared libraries stay). Run on the Mac, with the arm64 stack stopped:
  tools/stage media [--dry-run]   then   tools/gen/tree-files-lock.py"""
import subprocess, sys, pathlib, re

import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tests", "vm"))
import config
VM = config.VM
SSH = config.auth("ssh") + [VM]
SCP = config.auth("scp") + ["-q"]
OUT = "/home/erfanabdi/los24/out/target/product/generic_arm64"
APEX = [OUT + "/apex/com.android.media.swcodec/lib64", OUT + "/apex/com.android.media/lib64/extractors", OUT + "/apex/com.android.media/lib64", OUT + "/system/lib64"]
SKIP = set()  # the platform store aborts at listing when a library of its fixed list is missing, so all of them are installed; the host decoders still come first (rank)
EXTRACTORS = ["libaacextractor.so", "libamrextractor.so", "libflacextractor.so", "libmp3extractor.so", "libmpeg2extractor.so", "liboggextractor.so", "libwavextractor.so"]

def orb(cmd):
    return subprocess.run(["orb", "-m", "ubuntu22-x86", "bash", "-c", cmd], capture_output=True, text=True, check=True).stdout

PATHS = '[ -f ~/.config/apklayer/env ] && . ~/.config/apklayer/env; N=${APKLAYER_NATIVE:-$HOME/arm64-native}; L=${APKLAYER_ANDROID_LIBS:-$HOME/android-arm64/lib64}; '  # the VM's own runtime paths
on_vm = set(subprocess.run(SSH + [PATHS + "ls $L"], capture_output=True, text=True, check=True).stdout.split())
soft = [n for n in orb("ls %s" % APEX[0]).split() if n.startswith("libcodec2_soft_") and n not in SKIP]
wanted = [n for n in soft + EXTRACTORS + ["libOpenSLES.so"] if n not in on_vm]

def find(name):
    return orb("for d in %s; do [ -f $d/%s ] && { echo $d/%s; break; }; done; true" % (" ".join(APEX), name, name)).strip()

def needed(path):
    return re.findall(r"\[(.*)\]", orb("readelf -d %s | grep NEEDED" % path))

queue, plan = list(wanted), {}
while queue:
    n = queue.pop()
    if n in plan or n in on_vm or n in ("libc.so", "libm.so", "libdl.so", "libc++.so"): continue
    p = find(n)
    if not p: print("not in the tree outputs:", n); continue
    plan[n] = p
    queue += needed(p)
print("%d files to install:" % len(plan), " ".join(sorted(plan)))
if "--dry-run" in sys.argv: sys.exit(0)
stage = pathlib.Path("/tmp/apklayer-media-libs"); stage.mkdir(exist_ok=True)
for n, p in plan.items():
    subprocess.run(["cp", "/Users/erfanabdi/OrbStack/ubuntu22-x86" + p, str(stage / n)], check=True)
tar = subprocess.Popen(["tar", "-c", "-f", "-", "-C", str(stage), "."], stdout=subprocess.PIPE)
subprocess.run(SSH + [PATHS + "tar xf - -C $L"], stdin=tar.stdout, check=True)
assert tar.wait() == 0
# the staged libOpenSLES.so now answers for Android's OpenSL ES (the library mapping, tools/build cfg)
subprocess.run(SSH + [PATHS + "cd ~/apklayer && tools/build cfg $N"], check=True)
for n in plan:
    expected = orb("sha256sum " + plan[n]).split()[0]
    actual = subprocess.check_output(SSH + [PATHS + "sha256sum $L/" + n], text=True).split()[0]
    if expected != actual: raise RuntimeError("installed hash mismatch: " + n)
    print("verified", n, actual, flush=True)
print("installed")
