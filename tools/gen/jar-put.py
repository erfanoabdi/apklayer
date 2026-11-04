#!/usr/bin/env python3
"""jar-put.py <in.jar> <out.jar> <entry>=<file>...: a copy of in.jar with these entries added or replaced (Meson: the JNI shim and the critical-natives
list into native-runtime.jar, the shim into the framework jars). The output is a new file, so hardlinked copies of the input stay as they are."""
import os
import sys
import zipfile


def main():
    src, out, *pairs = sys.argv[1:]
    put = dict(p.split('=', 1) for p in pairs)
    tmp = out + '.tmp'
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            if info.filename not in put:
                zout.writestr(info, zin.read(info))
        for name, path in put.items():
            zout.write(path, name)
    os.replace(tmp, out)


if __name__ == '__main__':
    sys.exit(main())
