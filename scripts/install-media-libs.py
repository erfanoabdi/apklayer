#!/usr/bin/env python3
"""install-media-libs: the tree's software codecs (libcodec2_soft_*) and container extractors from the swcodec / media APEX build outputs
(read-only in ~/los24 under OrbStack) to the VM's ~/android-arm64/lib64, with the libraries they need that are not there yet.
Existing files are never overwritten (the platform copies of shared libraries stay). Run on the Mac, with the arm64 stack stopped:
  scripts/install-media-libs.py [--dry-run]   then   scripts/gen-tree-files-lock.py"""
import subprocess, sys, pathlib, re

import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tests", "vm"))
import config
VM = config.VM
SSH = ["sshpass", "-p", config.PASS, "ssh", "-o", "StrictHostKeyChecking=no", VM]
SCP = ["sshpass", "-p", config.PASS, "scp", "-q", "-o", "StrictHostKeyChecking=no"]
OUT = "/home/erfanabdi/los24/out/target/product/generic_arm64"
APEX = [OUT + "/apex/com.android.media.swcodec/lib64", OUT + "/apex/com.android.media/lib64/extractors", OUT + "/apex/com.android.media/lib64", OUT + "/system/lib64"]
SKIP = set()  # the platform store aborts at listing when a library of its fixed list is missing, so all of them are installed; the host decoders still come first (rank)
EXTRACTORS = ["libaacextractor.so", "libamrextractor.so", "libflacextractor.so", "libmp3extractor.so", "libmpeg2extractor.so", "liboggextractor.so", "libwavextractor.so"]

def orb(cmd):
    return subprocess.run(["orb", "-m", "ubuntu22-x86", "bash", "-c", cmd], capture_output=True, text=True, check=True).stdout

on_vm = set(subprocess.run(SSH + ["ls ~/android-arm64/lib64"], capture_output=True, text=True, check=True).stdout.split())
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
subprocess.run(SCP + [str(f) for f in stage.iterdir()] + [VM + ":~/android-arm64/lib64/"], check=True)
# apklayer patch: the real OpenSL ES engine uses the original audio services.
# A legacy host mapping would bypass the staged Android library entirely.
subprocess.run(SSH + ["sed -i '/^libOpenSLES.so[[:space:]]/d' ~/arm64-native/share/bionic_translation/cfg.d/bionic_translation.cfg"], check=True)
for n in plan:
    expected = orb("sha256sum " + plan[n]).split()[0]
    actual = subprocess.check_output(SSH + ["sha256sum ~/android-arm64/lib64/" + n], text=True).split()[0]
    if expected != actual: raise RuntimeError("installed hash mismatch: " + n)
    print("verified", n, actual, flush=True)
print("installed")
