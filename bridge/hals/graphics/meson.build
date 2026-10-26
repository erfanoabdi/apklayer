# apklayer graphics HAL, host side (PLAN P7.1): GPU buffers from libgbm.
gbm_dep = dependency('gbm', required: false)
drm_dep = dependency('libdrm', required: false)
if gbm_dep.found() and drm_dep.found()
  gfx_inc = include_directories('include')
  libgfx = shared_library('apklayer-hal-graphics', 'graphics.c',
    include_directories: gfx_inc, dependencies: [gbm_dep, drm_dep], install: true)
  executable('graphics-test', '../../../tests/hals/graphics-test.c',
    include_directories: gfx_inc, dependencies: [drm_dep], link_with: libgfx)
  egl_dep = dependency('egl', required: false)
  glesv2_dep = dependency('glesv2', required: false)
  if egl_dep.found() and glesv2_dep.found()
    executable('egl-probe', '../../../tests/hals/egl-probe.c',
      include_directories: gfx_inc, dependencies: [drm_dep, gbm_dep, egl_dep, glesv2_dep], link_with: libgfx)
  endif
endif
