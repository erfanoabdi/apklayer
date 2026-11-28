# apklayer-shell needs Wayland; it is skipped where Wayland isn't available (e.g. macOS).
wl_client_dep = dependency('wayland-client', required: host_machine.system() == 'linux')
wl_protos_dep = dependency('wayland-protocols', required: host_machine.system() == 'linux')
cairo_dep = dependency('cairo', required: host_machine.system() == 'linux')
xkb_dep = dependency('xkbcommon', required: host_machine.system() == 'linux')
wl_cursor_dep = dependency('wayland-cursor', required: host_machine.system() == 'linux')
scanner = find_program('wayland-scanner', required: host_machine.system() == 'linux')
gio_shell_dep = dependency('gio-2.0', required: host_machine.system() == 'linux')  # Maliit's D-Bus: the on-screen keyboard of Lipstick and Lomiri (maliit.c)
if wl_client_dep.found() and wl_protos_dep.found() and xkb_dep.found() and cairo_dep.found() and scanner.found() and gio_shell_dep.found()
  protodir = wl_protos_dep.get_variable(pkgconfig: 'pkgdatadir')
  # name -> protocol xml (xdg-shell, cursor-shape for resize cursors and its tablet-v2 dependency)
  protocols = {
    'xdg-shell': protodir / 'stable/xdg-shell/xdg-shell.xml',
    'cursor-shape-v1': protodir / 'staging/cursor-shape/cursor-shape-v1.xml',
    'text-input-v3': protodir / 'unstable/text-input/text-input-unstable-v3.xml',
    'tablet-v2': protodir / 'unstable/tablet/tablet-unstable-v2.xml',
    'linux-dmabuf-v1': protodir / 'stable/linux-dmabuf/linux-dmabuf-v1.xml',
    'viewporter': protodir / 'stable/viewporter/viewporter.xml',  # buffers of any scale shown at the window's logical size
    'fractional-scale-v1': protodir / 'staging/fractional-scale/fractional-scale-v1.xml',  # the compositor's preferred (fractional) scale per surface
    'xdg-activation-v1': protodir / 'staging/xdg-activation/xdg-activation-v1.xml',  # raising a window (activation.c)
    'xdg-decoration-unstable-v1': protodir / 'unstable/xdg-decoration/xdg-decoration-unstable-v1.xml',  # server-side title bars (decoration.c)
    'wayland-android': 'protocols/wayland-android.xml',  # libhybris's android_wlegl: gralloc buffers on Halium compositors (wlegl.c)
    'surface-extension': 'protocols/surface-extension.xml',  # Qt's qt_surface_extension: Lipstick's raise and lower (wlshell.c)
  }
  generated = []
  foreach name, xml : protocols
    generated += custom_target(name + '-client-header',
      output: name + '-client-protocol.h',
      input: xml,
      command: [scanner, 'client-header', '@INPUT@', '@OUTPUT@'])
    generated += custom_target(name + '-code',
      output: name + '-protocol.c',
      input: xml,
      command: [scanner, 'private-code', '@INPUT@', '@OUTPUT@'])
  endforeach
  # the render filter (sharpen.c): EGL, GBM and GLES; without them the shell builds without it
  egl_dep = dependency('egl', required: false)
  gbm_dep = dependency('gbm', required: false)
  gles_dep = dependency('glesv2', required: false)
  sharpen_src = []
  sharpen_deps = []
  sharpen_args = []
  if egl_dep.found() and gbm_dep.found() and gles_dep.found()
    sharpen_src = ['sharpen.c', 'sharpen_smaa.c', 'sharpen_cmaa.c', 'sharpen_temporal.c', 'sharpen_nn.c']
    sharpen_deps = [egl_dep, gbm_dep, gles_dep]
    sharpen_args = ['-DHAVE_SHARPEN']
  endif
  # the Vulkan backend of the render filter (sharpen_vk.c): the Vulkan headers (libvulkan itself is loaded at run time), from the system or the prebuilts
  vk_inc = []
  vk_dep = dependency('vulkan', required: false)
  if vk_dep.found()
    vk_inc = [vk_dep.partial_dependency(includes: true, compile_args: true)]
  elif prebuilts != '' and fs.exists(prebuilts / 'common/gfxstream/third_party/vulkan/include/vulkan/vulkan.h')
    vk_inc = [declare_dependency(include_directories: include_directories(prebuilts / 'common/gfxstream/third_party/vulkan/include', is_system: true))]
  endif
  if sharpen_src.length() > 0 and vk_inc.length() > 0
    sharpen_src += ['sharpen_vk.c', 'sharpen_vk_draw.c']
    sharpen_deps += vk_inc + [meson.get_compiler('c').find_library('dl', required: false)]
    sharpen_args += ['-DHAVE_SHARPEN_VK']
  endif
  if sharpen_src.length() > 0  # the pass alone, for tests/vm/sharpen.py (not installed)
    executable('sharpen-test', ['../../tests/hals/sharpen-test.c', sharpen_src], dependencies: sharpen_deps + [dependency('threads'), meson.get_compiler('c').find_library('m')], c_args: sharpen_args)
  endif
  executable('apklayer-shell',
    ['main.c', 'client.c', 'window.c', 'geometry.c', 'pointer.c', 'keyboard.c', 'registry.c', 'clipboard.c', 'textinput.c', 'touch.c', 'decor.c', 'decor_draw.c', 'navbar.c', 'mobile.c', 'appinfo.c', 'dmabuf.c', 'dmabuf_shm.c', 'feedback.c', 'splash.c', 'vsync.c', 'testinput.c', 'activation.c', 'decoration.c', 'wlegl.c', 'wlshell.c', 'maliit.c', 'tablet.c', 'gamepad.c', 'sharpen_win.c', sharpen_src, generated],
    dependencies: [dependency('threads'), cairo_dep, gio_shell_dep, wl_client_dep, wl_cursor_dep, xkb_dep, sharpen_deps, meson.get_compiler('c').find_library('m')],  # hypot (splash.c): glibc 2.39 (Ubuntu 24.04) does not pull libm in by itself
    c_args: ['-Werror=incompatible-pointer-types', '-Werror=implicit-function-declaration', sharpen_args],
    install: true, install_dir: pkg_lib / 'bridge/shell')
endif
