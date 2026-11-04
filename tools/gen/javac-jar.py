#!/usr/bin/env python3
"""javac-jar.py <out.jar> <classes dir> [options] <source dir or .java file>...: one javac call over every .java file of the sources, then a jar
(Meson's custom_target for each of our jars: Meson's own jar() starts a compiler per file and resolves siblings by package path, which neither the
host nor dex2jvm's flat upstream layout follows).
  --cp PATH                  compile class path (':'-separated; dir/* globs as javac takes them)
  --main CLASS               the jar's Main-Class
  --resources DIR            copied into the jar as they are (repeatable)
  --extract JAR ENTRY...     entries of another jar put into the classes first (and so on the class path)
  --patched DIR PROJECT      sources copied from DIR with patches/<PROJECT> applied (patches/apply), compiled with the rest
  --javac ARG                passed to javac (repeatable)"""
import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]


def sources(path):
    p = Path(path)
    return [p] if p.is_file() else sorted(f for f in p.rglob('*.java') if not f.name.startswith('._'))


def main():
    a = argparse.ArgumentParser()
    a.add_argument('out')
    a.add_argument('classes')
    a.add_argument('--cp', default='')
    a.add_argument('--main')
    a.add_argument('--resources', action='append', default=[])
    a.add_argument('--extract', nargs='+', action='append', default=[])
    a.add_argument('--patched', nargs=2, action='append', default=[])
    a.add_argument('--javac', action='append', default=[])
    a.add_argument('sources', nargs='*')
    args = a.parse_intermixed_args()  # sources after options (Python before 3.13 stops at the first option otherwise)
    classes = Path(args.classes)
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir(parents=True)
    for jar, *entries in args.extract:
        with zipfile.ZipFile(jar) as z:
            for e in entries: z.extract(e, classes)
    files = [f for s in args.sources for f in sources(s)]
    for i, (src, project) in enumerate(args.patched):
        tree = classes.parent / ('%s.src%d' % (classes.name, i))
        shutil.rmtree(tree, ignore_errors=True)
        shutil.copytree(src, tree)
        subprocess.run([str(REPO / 'patches/apply'), project, str(tree)], check=True, stdout=subprocess.DEVNULL)
        files += sources(tree)
    argfile = classes.parent / (classes.name + '.sources')
    argfile.write_text(''.join('"%s"\n' % f for f in files))
    cp = ':'.join(x for x in (str(classes), args.cp) if x)
    subprocess.run(['javac', '-nowarn', '-encoding', 'UTF-8', '-d', str(classes), '-cp', cp, *args.javac, '@' + str(argfile)], check=True)
    for r in args.resources:
        shutil.copytree(r, classes, dirs_exist_ok=True)
    tmp = args.out + '.tmp'
    subprocess.run(['jar', '--create', '--file', tmp] + (['--main-class', args.main] if args.main else []) + ['-C', str(classes), '.'], check=True)
    os.replace(tmp, args.out)


if __name__ == '__main__':
    sys.exit(main())
