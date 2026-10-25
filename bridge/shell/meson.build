# apklayer-shell needs Wayland; it is skipped where Wayland isn't available (e.g. macOS).
wl_client_dep = dependency('wayland-client', required: host_machine.system() == 'linux')
wl_protos_dep = dependency('wayland-protocols', required: host_machine.system() == 'linux')
xkb_dep = dependency('xkbcommon', required: host_machine.system() == 'linux')
wl_cursor_dep = dependency('wayland-cursor', required: host_machine.system() == 'linux')
scanner = find_program('wayland-scanner', required: host_machine.system() == 'linux')
if wl_client_dep.found() and wl_protos_dep.found() and xkb_dep.found() and scanner.found()
  protodir = wl_protos_dep.get_variable(pkgconfig: 'pkgdatadir')
  # name -> protocol xml (xdg-shell, cursor-shape for resize cursors and its tablet-v2 dependency)
  protocols = {
    'xdg-shell': protodir / 'stable/xdg-shell/xdg-shell.xml',
    'cursor-shape-v1': protodir / 'staging/cursor-shape/cursor-shape-v1.xml',
    'tablet-v2': protodir / 'unstable/tablet/tablet-unstable-v2.xml',
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
  executable('apklayer-shell',
    ['shell.c', 'decor.c', generated],
    dependencies: [wl_client_dep, wl_cursor_dep, xkb_dep],
    c_args: ['-Werror=incompatible-pointer-types', '-Werror=implicit-function-declaration'],
    install: false)
endif
