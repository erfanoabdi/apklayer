#!/usr/bin/env python3
"""compare.py ANDROID APKLAYER... [--width N] [--differ]: joins collect.py result files by key, one row per probe: the Android (Waydroid)
value, then each apklayer value, "=" where it equals Android's after normalization (app paths, numbers in thread/pid names). Markdown table."""
import argparse
import re
from pathlib import Path


def load(path):
    values = {}
    for line in Path(path).read_text(errors='replace').splitlines():
        key, sep, value = line.partition('=')
        if sep:
            values[key] = value
    return values


def normal(v):
    v = re.sub(r'/data/app/[^ ;:\]"]*?/hello\.identity-[^/ ;:\]"]*', '/data/app/<APP>', v)
    v = re.sub(r'0x[0-9a-f]{6,}', '0x<ADDR>', v)
    return v


def cell(v, width):
    v = v.replace('|', '\\|')
    return v if len(v) <= width else v[:width] + '...'


def main():
    p = argparse.ArgumentParser()
    p.add_argument('files', nargs='+')
    p.add_argument('--width', type=int, default=80)
    p.add_argument('--differ', action='store_true', help='only rows where some apklayer value differs')
    a = p.parse_args()
    sets = [load(f) for f in a.files]
    keys = list(dict.fromkeys(k for s in sets for k in s))
    print('| probe | ' + ' | '.join(Path(f).stem for f in a.files) + ' |')
    print('|---' * (len(a.files) + 1) + '|')
    for k in keys:
        ref = normal(sets[0].get(k, '(missing)'))
        row = [cell(ref, a.width)]
        same = True
        for s in sets[1:]:
            v = normal(s.get(k, '(missing)'))
            same &= v == ref
            row.append('=' if v == ref else cell(v, a.width))
        if a.differ and same:
            continue
        print('| %s | %s |' % (k, ' | '.join(row)))


if __name__ == '__main__':
    main()
