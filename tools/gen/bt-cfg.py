#!/usr/bin/env python3
"""bt-cfg.py <ours.cfg> <upstream.cfg> <android libs> <out>: bionic_translation's library mapping (bridge/native/meson.build): our mappings,
the ones that need a staged library, then bionic_translation's own; bionic_translation takes the first line naming a library."""
import sys
from pathlib import Path

ours, upstream, libs, out = sys.argv[1:]
libs = Path(libs)
if not (libs / 'libnativedisplay.so').is_file():
    sys.exit('bt-cfg: %s has no libnativedisplay.so (bridge/native/bionic_translation.cfg loads Android\'s)' % libs)
lines = [Path(ours).read_text().rstrip('\n'), '# mappings that need a staged library']
egl = libs / 'libapklayer_egl.so'
if egl.exists(): lines.append('libEGL.so libapklayer_egl.so')  # our EGL (aosp/hals/graphics/egl)
# Android's GLES 1 trampolines jump through the hook table an EGL stores in TLS_SLOT_OPENGL_API: ours fills it for ES 1 contexts
# (Gles1.cpp: the host's ES 1 or libapklayer-gles1.so); an older staged libEGL does not, so its apps get the host's library
lines.append('libGLESv1_CM.so ' + ('libGLESv1_CM.so' if egl.is_file() and b'apklayer_gles1_create' in egl.read_bytes() else 'libGLESv1_CM.so.1'))
if (libs / 'libapklayer_vulkan.so').is_file() and (libs / 'vulkan.apklayer.so').is_file():
    lines.append('libvulkan.so libapklayer_vulkan.so')  # Android's Vulkan loader (renamed copy)
    # volk opens libvulkan.so.1 first: apps get Android's loader by that name too; our driver opens the host's by it (bionic_translation 0048)
    lines.append('app:libvulkan.so.1 libapklayer_vulkan.so')
if (libs / 'libOpenSLES.so').is_file(): lines.append('libOpenSLES.so libOpenSLES.so')  # Android's OpenSL ES engine on the original audio services
lines += ["# bionic_translation's own cfg.d", Path(upstream).read_text().rstrip('\n')]
Path(out).write_text('\n'.join(lines) + '\n')
