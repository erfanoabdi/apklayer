#!/usr/bin/env python3
"""Download the latest arm64-compatible APK of F-Droid packages, verified against the sha256 in the repository index.
Usage: fdroid.py INDEX.json OUTDIR package..."""
import hashlib
import json
import os
import sys
import urllib.request

index, out, packages = sys.argv[1], sys.argv[2], sys.argv[3:]
data = json.load(open(index))['packages']
for pkg in packages:
    versions = data.get(pkg, {}).get('versions', {})
    best = None
    for v in versions.values():
        m = v['manifest']
        native = m.get('nativecode')
        if native and 'arm64-v8a' not in native:
            continue
        code = m.get('versionCode', 0)
        if best is None or code > best[0]:
            best = (code, v)
    if best is None:
        print('no arm64 build', pkg)
        continue
    f = best[1]['file']
    url = 'https://f-droid.org/repo' + f['name']
    if os.path.exists('%s/%s.apk' % (out, pkg)):
        print('have', pkg)
        continue
    blob = None
    for attempt in range(3):
        try:
            blob = urllib.request.urlopen(url, timeout=120).read()
            break
        except OSError as failure:
            print('retry', pkg, failure)
    if blob is None:
        continue
    if hashlib.sha256(blob).hexdigest() != f['sha256']:
        print('HASH MISMATCH', pkg)
        continue
    open('%s/%s.apk' % (out, pkg), 'wb').write(blob)
    print('ok', pkg, best[0], len(blob) // 1024, 'KiB')
