#!/usr/bin/env python3
"""flatpak-sources.py: flatpak/deps-sources.json, deps/deps.lock's artifacts as the Flatpak's pinned sources (the build has no network: Flathub).
They land in flatpak-deps/, where fetch-dep.py finds them (APKLAYER_DEPS_LOCAL). Run it after a change to deps.lock and commit the result.
An aar-classes entry is pinned by its classes.jar: its AAR is downloaded here once for that AAR's own sha256. android-all/ is not built
(deps/meson.build) and sdk: entries come from the prebuilts (common/)."""
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CENTRAL, GOOGLE = 'https://repo1.maven.org/maven2', 'https://dl.google.com/android/maven2'


def url(coord):
    g, a, v, ext = coord.split(':')
    base = GOOGLE if g.startswith('androidx.') else CENTRAL  # as fetch-dep.py: androidx.test.* only on Google's
    return '%s/%s/%s/%s/%s-%s.%s' % (base, g.replace('.', '/'), a, v, a, v, ext)


def main():
    sources, seen = [], set()
    for line in (ROOT / 'deps/deps.lock').read_text().splitlines():
        f = line.split()
        if len(f) != 3 or f[0].startswith('#') or f[1].startswith('android-all/'): continue
        sha, kind, coord = f[0], *f[2].split(':', 1)
        if kind == 'aar-classes':
            coord += ':aar'
            with urllib.request.urlopen(url(coord)) as r: sha = hashlib.sha256(r.read()).hexdigest()
        elif kind != 'maven':
            continue
        name = url(coord).rsplit('/', 1)[1]  # fetch-dep.py looks a jar up by its lock name, an AAR by its Maven name
        if kind == 'maven': name = Path(f[1]).name
        if name in seen: continue
        seen.add(name)
        sources.append({'type': 'file', 'url': url(coord), 'sha256': sha, 'dest': 'flatpak-deps', 'dest-filename': name})
    out = ROOT / 'flatpak/deps-sources.json'
    out.write_text(json.dumps(sources, indent=2) + '\n')
    print('flatpak-sources: %d sources in %s' % (len(sources), out.relative_to(ROOT)))


if __name__ == '__main__':
    sys.exit(main())
