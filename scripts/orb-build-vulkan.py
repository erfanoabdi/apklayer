#!/usr/bin/env python3
"""Incremental Vulkan build, reusing existing arm64 Soong compile/link commands.
Run inside OrbStack after syncing aosp/ and applying patch-vulkan.sh. Never cleans.
The graph need not be regenerated to build the two small new shared tools: their
Android.bp modules use the same existing device C++ toolchain as libapklayer_egl.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess

os.chdir(Path.home() / 'los24')
ninja = ['prebuilts/build-tools/linux-x86/bin/ninja', '-f', 'out/combined-lineage_gsi_arm64.ninja', '-t', 'commands']
stage = Path.home() / 'apklayer-vulkan-stage'
stage.mkdir(exist_ok=True)

def commands(root, name):
    print('query', name, flush=True)
    return subprocess.check_output(ninja + [root + name + '.so'], text=True).splitlines()

def response(root, name):
    target = root + 'unstripped/' + name + '.so'
    files = [str(p) for p in Path('out/soong').glob('build.lineage_gsi_arm64.*.ninja')]
    found = subprocess.check_output(['grep', '-l', '-F', target + ':', *files], text=True).splitlines()
    assert len(found) == 1
    text = re.sub(r'\$\n[ \t]*', '', Path(found[0]).read_text())
    block = text[text.index('build ' + target + ':'):].split('\n\n')[0]
    explicit = block.splitlines()[0].split('g.cc.ld ', 1)[1].split(' |', 1)[0]
    flags = next(x.split(' = ', 1)[1] for x in block.splitlines() if x.strip().startswith('libFlags ='))
    return target, explicit + ' ' + flags

root = 'out/soong/.intermediates/frameworks/native/vulkan/libvulkan/libvulkan/android_arm64_armv8-a_shared/'
cmds = commands(root, 'libvulkan')
for c in cmds:
    if ' -c ' in c and ' -o ' + root in c:
        obj = Path(c.split(' -o ')[1].split()[0])
        header = Path('frameworks/native/vulkan/libvulkan/driver.h')
        if not obj.exists() or obj.stat().st_mtime < max(header.stat().st_mtime, Path(c.split()[-1]).stat().st_mtime):
            print('compile', c.split()[-1], flush=True)
            subprocess.run(c, shell=True, check=True)
target, rsp = response(root, 'libvulkan')
Path(target + '.rsp').write_text(rsp)
for c in cmds:
    if ' -c ' not in c and ' -o ' + target in c:
        print('link Android loader', flush=True)
        subprocess.run(c, shell=True, check=True)
    elif root in c and ('strip.sh' in c or '/cp -d ' in c):
        subprocess.run(c, shell=True, check=True)
shutil.copy2(root + 'libvulkan.so', stage / 'libvulkan.so')

old = 'out/soong/.intermediates/vendor/apklayer/hals/graphics/egl/libapklayer_egl/android_arm64_armv8-a_shared/'
cmds = commands(old, 'libapklayer_egl')
compile_template = next(c for c in cmds if ' -c ' in c and ' -o ' + old in c)
old_target, old_rsp = response(old, 'libapklayer_egl')
for name, source in [('vulkan.apklayer', 'hals/graphics/vulkan/Driver.cpp'), ('libapklayer_vktest', 'tools/vktest.cpp')]:
    root = 'out/apklayer-vulkan/' + name + '/'
    Path(root + 'unstripped').mkdir(parents=True, exist_ok=True)
    Path(root + 'before_final_validations').mkdir(parents=True, exist_ok=True)
    obj = root + 'obj.o'
    c = re.sub(r' -o \S+', ' -o ' + obj, compile_template)
    c = re.sub(r' -MF \S+', ' -MF ' + obj + '.d', c)
    c = c.rsplit(' ', 1)[0] + ' vendor/apklayer/' + source
    c = c.replace(' -c ', ' -Iexternal/vulkan-headers/include -Ilibnativehelper/include_jni -Iframeworks/native/vulkan/include -Ihardware/libhardware/include_all -Ivendor/apklayer/hals/graphics/common -Wno-main -Wno-missing-field-initializers -c ')
    print('compile', source, flush=True)
    subprocess.run(c, shell=True, check=True)
    target = root + 'unstripped/' + name + '.so'
    rsp = re.sub(r'\S+\.o(?= )', lambda m: obj if m.group().startswith(old) else m.group(), old_rsp)
    Path(target + '.rsp').write_text(rsp)
    for c in cmds:
        if ' -c ' not in c and (' -o ' + old_target in c or (old in c and ('strip.sh' in c or '/cp -d ' in c))):
            c = c.replace(old, root).replace('libapklayer_egl.so', name + '.so')
            print('link/strip', name, flush=True)
            subprocess.run(c, shell=True, check=True)
    shutil.copy2(root + name + '.so', stage / (name + '.so'))
print('staged Vulkan libraries in', stage, flush=True)
