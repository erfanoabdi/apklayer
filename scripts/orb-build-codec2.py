#!/usr/bin/env python3
"""Incremental build of libapklayer_codec2 in the user's LineageOS tree (run INSIDE the OrbStack VM, by the user or with their authorization).

The graph must already know the sources (regenerate it first with `m nothing`-style graph-only step). This replays the exact generated compile
commands for the changed sources, relinks, strips and copies the library like the module's own commands do, and stages it for the VM install.
No clean, no product change. Usage in OrbStack: python3 <repo>/scripts/orb-build-codec2.py [Source.cpp ...]  (default: AudioDecoder ComponentStore Service)
Then install on the Parallels VM: copy ~/apklayer-codec2-stage/libapklayer_codec2.so to ~/android-arm64/lib64 (verify the SHA)."""
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

os.chdir(Path.home() / 'los24')
root = 'out/soong/.intermediates/vendor/apklayer/hals/media/codec2/libapklayer_codec2/android_arm64_armv8-a_shared/'
final, target = root + 'libapklayer_codec2.so', root + 'unstripped/libapklayer_codec2.so'
cmds_file = Path('/tmp/apklayer-codec2-commands')
if not cmds_file.exists():  # the module's commands from the regenerated graph (a query, nothing is built)
    out = subprocess.check_output(['prebuilts/build-tools/linux-x86/bin/ninja', '-f', 'out/combined-lineage_gsi_arm64.ninja', '-t', 'commands', final], text=True)
    cmds_file.write_text(out)
commands = cmds_file.read_text().splitlines()
sources = sys.argv[1:] or ['AudioDecoder.cpp', 'ComponentStore.cpp', 'Service.cpp']
for src in sources:
    c = next(c for c in commands if ' -c ' in c and ' -o ' + root in c and c.endswith('vendor/apklayer/hals/media/codec2/' + src))
    Path(c.split(' -o ')[-1].split()[0]).parent.mkdir(parents=True, exist_ok=True)
    print('compile', src, flush=True)
    subprocess.run(c, shell=True, check=True)
ninja_files = subprocess.check_output(['grep', '-l', target + ':', *[str(p) for p in Path('out/soong').glob('build.lineage_gsi_arm64.*.ninja')]], text=True).splitlines()
assert len(ninja_files) == 1
text = re.sub(r'\$\n[ \t]*', '', Path(ninja_files[0]).read_text())
block = text[text.index('build ' + target + ':'):].split('\n\n')[0]
explicit = block.splitlines()[0].split('g.cc.ld ', 1)[1].split(' |', 1)[0]
flags = next(x.split(' = ', 1)[1] for x in block.splitlines() if x.strip().startswith('libFlags ='))
Path(target).parent.mkdir(parents=True, exist_ok=True)
Path(root + 'before_final_validations').mkdir(parents=True, exist_ok=True)
Path(target + '.rsp').write_text(explicit + ' ' + flags)
link = next(c for c in commands if ' -c ' not in c and ' -o ' + target in c)
print('link', flush=True)
subprocess.run(link, shell=True, check=True)
for c in commands:
    if root in c and ' -c ' not in c and 'clang++ ' not in c and ('strip.sh' in c or '/cp -d ' in c):
        subprocess.run(c, shell=True, check=True)
stage = Path.home() / 'apklayer-codec2-stage'
stage.mkdir(exist_ok=True)
shutil.copy2(final, stage / 'libapklayer_codec2.so')
print('staged', stage / 'libapklayer_codec2.so', flush=True)
