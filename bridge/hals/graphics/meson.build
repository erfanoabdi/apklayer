# apklayer graphics HAL, host side (PLAN P7.1): GPU buffers from libgbm.
gbm_dep = dependency('gbm', required: false)
drm_dep = dependency('libdrm', required: false)
if gbm_dep.found() and drm_dep.found()
  gfx_inc = include_directories('include')
  libgfx = shared_library('apklayer-hal-graphics', ['graphics.c', 'readback.c', 'display.c', 'modifiers.c'],
    include_directories: gfx_inc, dependencies: [gbm_dep, drm_dep, dependency('threads'), dependency('dl')], install: true, install_dir: native_dir)
  executable('graphics-test', '../../../tests/hals/graphics-test.c',
    include_directories: gfx_inc, dependencies: [drm_dep], link_with: libgfx)
  executable('readback-test', '../../../tests/hals/readback-test.c',
    include_directories: gfx_inc, dependencies: [drm_dep, gbm_dep], link_with: libgfx)
  executable('display-test', '../../../tests/hals/display-test.c',
    include_directories: gfx_inc, dependencies: [drm_dep, dependency('threads')], link_with: libgfx)
  egl_dep = dependency('egl', required: false)
  glesv2_dep = dependency('glesv2', required: false)
  if egl_dep.found() and glesv2_dep.found()
    executable('egl-probe', '../../../tests/hals/egl-probe.c',
      include_directories: gfx_inc, dependencies: [drm_dep, gbm_dep, egl_dep, glesv2_dep], link_with: libgfx)
  endif
endif

# the instance-GPU Vulkan filter (aosp/hals/graphics/vulkan/DeviceSelect.h): the header itself on the host, with a log.h stand-in
vulkan_dep = dependency('vulkan', required: false)
if vulkan_dep.found()
  executable('vulkan-select-test', '../../../tests/hals/vulkan-select-test.cpp', override_options: ['b_ndebug=false'],
    include_directories: include_directories('../../../tests/hals/include'), dependencies: [vulkan_dep, dependency('dl')])
  # ASTC/ETC2 emulation of the HAL (TextureEmulation.cpp, gfxstream's decoder) on the host's GPU
  vk_hal = '../../../aosp/hals/graphics/vulkan/'
  executable('vulkan-textures-test', ['../../../tests/hals/vulkan-textures-test.cpp', vk_hal + 'TextureEmulation.cpp', vk_hal + 'TextureCommands.cpp',
      vk_hal + 'gfxstream/vulkan/emulated_textures/compressed_image_info.cpp', vk_hal + 'gfxstream/vulkan/emulated_textures/gpu_decompression_pipeline.cpp',
      vk_hal + 'TextureAstcMesa.cpp', vk_hal + 'TextureDump.cpp', vk_hal + 'mesa_astc/texcompress_astc_luts.cpp'],
    include_directories: include_directories(vk_hal + 'gfxstream'), cpp_args: ['-Wno-missing-field-initializers', '-Wno-unused-parameter'],
    override_options: ['cpp_std=c++20'], dependencies: [vulkan_dep, dependency('dl')])
  executable('vulkan-astc-probe', ['../../../tests/hals/vulkan-astc-probe.cpp', vk_hal + 'TextureEmulation.cpp', vk_hal + 'TextureCommands.cpp',
      vk_hal + 'gfxstream/vulkan/emulated_textures/compressed_image_info.cpp', vk_hal + 'gfxstream/vulkan/emulated_textures/gpu_decompression_pipeline.cpp',
      vk_hal + 'TextureAstcMesa.cpp', vk_hal + 'TextureDump.cpp', vk_hal + 'mesa_astc/texcompress_astc_luts.cpp'],
    include_directories: include_directories(vk_hal + 'gfxstream'), cpp_args: ['-Wno-missing-field-initializers', '-Wno-unused-parameter'],
    override_options: ['cpp_std=c++20'], dependencies: [vulkan_dep, dependency('dl')])
endif
