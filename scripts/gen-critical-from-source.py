#!/usr/bin/env python3
"""Critical-native lines ("class name+signature") scanned from Java sources: for tree classes missing from the javac shards
(android-all has the annotation stripped), e.g. the android.content.res natives. Usage: gen-critical-from-source.py file.java... >> critical-natives.txt"""
import re, sys

PRIM = {'void': 'V', 'boolean': 'Z', 'byte': 'B', 'char': 'C', 'short': 'S', 'int': 'I', 'long': 'J', 'float': 'F', 'double': 'D'}
DECL = re.compile(r'@CriticalNative\s+(?:(?:private|public|protected|static|final|/\*.*?\*/)\s+)*native\s+(\w+)\s+(\w+)\s*\(([^)]*)\)', re.S)

for path in sys.argv[1:]:
    text = open(path).read()
    pkg = re.search(r'^package\s+([\w.]+);', text, re.M).group(1)
    cls = pkg + '.' + re.search(r'\bclass\s+(\w+)', text).group(1)
    for ret, name, params in DECL.findall(text):
        args = ''.join(PRIM[p.split()[-2]] for p in params.split(',') if p.strip())
        print('%s %s(%s)%s' % (cls, name, args, PRIM[ret]))
