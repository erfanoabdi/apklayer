#!/usr/bin/env python3
"""Give an app's bundled libraries that share a name with a device library (libcrypto.so, libssl.so ...) a different, same-length name, in the files and in every
DT_NEEDED/DT_SONAME string of the app's other libraries. The translated linker has one namespace: a dependency named "libcrypto.so" would be the device's BoringSSL
that is already loaded, not the app's OpenSSL that its libssl was built against.
Usage: rename-bundled-system-libs.py <app lib dir> <device lib dir>"""
import os
import sys

app, device = sys.argv[1], sys.argv[2]
clash = [n for n in sorted(os.listdir(app)) if n.endswith('.so') and os.path.exists(os.path.join(device, n))]
if not clash:
    sys.exit(0)
# same length: the letter before ".so" becomes x (y if it is x already)
renames = {n: n[:-5] + ('x' if n[-4] != 'x' else 'y') + n[-4:] for n in clash}
for old, new in renames.items():
    assert len(old) == len(new)
for name in sorted(os.listdir(app)):
    path = os.path.join(app, name)
    if not name.endswith('.so') or not os.path.isfile(path):
        continue
    data = open(path, 'rb').read()
    patched = data
    for old, new in renames.items():
        patched = patched.replace(old.encode() + b'\0', new.encode() + b'\0')
    if patched != data:
        open(path, 'wb').write(patched)
for old, new in renames.items():
    os.rename(os.path.join(app, old), os.path.join(app, new))
    print('renamed', old, '->', new)
