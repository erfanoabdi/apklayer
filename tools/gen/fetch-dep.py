#!/usr/bin/env python3
"""fetch-dep.py <sha256> <source> <out> [local dir...]: one pinned artifact of deps/deps.lock (Meson, deps/meson.build), sha256-checked.
Looked for by file name in the local dirs (the prebuilts' common/, $APKLAYER_DEPS_LOCAL, the old deps cache), then ~/.m2 or the Android SDK,
then downloaded: maven:<g>:<a>:<v>:<ext> from Maven Central ($APKLAYER_MAVEN) or Google's Maven, aar-classes:<g>:<a>:<v> the AAR's classes.jar."""
import hashlib
import http.client
import io
import os
import shutil
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

HOME = Path.home()
MAVEN = os.environ.get('APKLAYER_MAVEN', 'https://repo1.maven.org/maven2')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def maven(coord):
    g, a, v, ext = coord.split(':')
    rel = '%s/%s/%s/%s-%s.%s' % (g.replace('.', '/'), a, v, a, v, ext)
    m2 = HOME / '.m2/repository' / rel
    if m2.is_file(): return m2.read_bytes()
    for base in (MAVEN, 'https://maven.google.com'):  # androidx.test.* and other Google artifacts are only on Google's
        for attempt in range(5):  # Maven Central answers 429 when parallel jobs fetch at once: back off and try again
            try:
                with urllib.request.urlopen(base + '/' + rel) as r: return r.read()
            except (OSError, http.client.IncompleteRead) as e:  # a connection cut mid-file (IncompleteRead) is tried again too
                print('fetch-dep: %s/%s: %s' % (base, rel, e), file=sys.stderr)
                if not isinstance(e, http.client.IncompleteRead) and getattr(e, 'code', None) not in (429, 500, 502, 503, 504): break
                time.sleep(2 ** attempt)
    raise SystemExit('fetch-dep: %s not found' % coord)


def resolve(want, src, name, local):
    for d in local:
        for f in Path(d).rglob(name) if Path(d).is_dir() else ():
            if f.is_file() and sha(data := f.read_bytes()) == want: return data
    kind, _, rest = src.partition(':')
    if kind == 'maven': return maven(rest)
    if kind == 'aar-classes': return zipfile.ZipFile(io.BytesIO(maven(rest + ':aar'))).read('classes.jar')
    if kind == 'sdk':
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or next(
            (str(d) for d in (HOME / 'Library/Android/sdk', HOME / 'Android/Sdk') if d.is_dir()), '')
        f = Path(sdk) / rest
        if sdk and f.is_file(): return f.read_bytes()
        raise SystemExit('fetch-dep: %s: not in the local dirs nor the Android SDK (-Dprebuilts, ANDROID_HOME)' % name)
    raise SystemExit('fetch-dep: unknown source ' + src)


def main():
    want, src, out, *local = sys.argv[1:]
    local += os.environ.get('APKLAYER_DEPS_LOCAL', '').split() + [str(HOME / '.cache/apklayer/deps')]
    data = resolve(want, src, Path(out).name, local)
    if sha(data) != want: raise SystemExit('fetch-dep: %s: sha256 %s, want %s' % (out, sha(data), want))
    Path(out + '.part').write_bytes(data)
    shutil.move(out + '.part', out)


if __name__ == '__main__':
    sys.exit(main())
