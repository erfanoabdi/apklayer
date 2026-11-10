# OpenGL ES 1 for hosts without it: gfxstream's GLES_CM translator (the tree's hardware/google/gfxstream, unmodified, given as -Dgfxstream=<dir>:
# orb -m ubuntu22-x86 bash -c "cd ~/los24/hardware/google/gfxstream && tar cf - meson.build meson_options.txt host common third_party" | ssh VM 'mkdir -p <dir> && tar xf - -C <dir>')
# and its EGL side (gles1.cpp) as libapklayer-gles1.so. gfxstream's own Meson build makes only the archives this needs (a subproject would build
# all of its host renderer), linked through a linker script naming them; it is configured once: a new gfxstream source needs a fresh build dir.
gfxstream = get_option('gfxstream')
if gfxstream == '' and prebuilts != '' and fs.is_dir(prebuilts / 'common/gfxstream') and meson.get_compiler('cpp').has_header('X11/Xlib.h')
  # the prebuilts' copy (tools/stage gfxstream): release packages ship ES 1 too. gfxstream's GL headers include X11's (glx.h): a build root
  # without them (Sailfish OS: no X11; a Halium device's GLES driver has ES 1 itself) builds without it
  gfxstream = prebuilts / 'common/gfxstream'
endif
if gfxstream != ''
  gfx_build = meson.current_build_dir() / 'gfxstream-build'
  # decoders: gles alone leaves gfxstream's inc_renderdoc_external undefined; nothing of the Vulkan decoder is built
  gfx_lib = custom_target('gfxstream-gles1.ld', output: 'gfxstream-gles1.ld',
    command: ['sh', '-c', 'set -e; b="$1" src="$2" out="$3"; shift 3; [ -f "$b/build.ninja" ] || meson setup "$b" "$src" -Dgfxstream-build=host ' +
      '-Ddecoders=gles,vulkan -Dbuildtype=release >/dev/null; ninja -C "$b" "$@"; ' +
      'echo "GROUP ( $(for a; do printf "%s " "$b/$a"; done))" > "$out"', 'sh', gfx_build, gfxstream, '@OUTPUT@',
      'host/gl/glestranslator/gles_cm/libglescm_translator.a', 'host/gl/OpenGLESDispatch/libgl_openglesdispatch.a',
      'host/gl/glestranslator/common/libgl_common.a', 'host/compressed_textures/libhost_compressed_textures.a', 'common/etc/libetc.a',
      'host/common/libhost_common.a', 'host/snapshot/libhost_snapshot.a', 'common/logging/liblib_common_logging.a', 'common/base/libcommon_base.a'])
  gfx_inc = []
  foreach d : ['common/base', 'common/etc', 'host', 'host/gl/glestranslator/common', 'host/gl/OpenGLESDispatch', 'third_party/glm', 'host/common',
      'common/logging', 'host/snapshot', 'third_party/opengl']
    gfx_inc += '-I' + gfxstream / d / 'include'
  endforeach
  shared_module('apklayer-gles1', 'gles1.cpp', cpp_args: gfx_inc + ['-DGLM_ENABLE_EXPERIMENTAL=1', '-DGFXSTREAM_MESON_BUILD=1', '-DGFXSTREAM_ENABLE_HOST_GLES=1'],
    link_args: [gfx_lib.full_path(), '-lEGL', '-ldl', '-lpthread', '-Wl,--no-undefined'], link_depends: gfx_lib,
    override_options: ['cpp_std=gnu++17', 'optimization=2', 'warning_level=0'], install: true, install_dir: native_dir)
endif
