project('apklayer-bridge', 'c',
  version: '0.1',
  default_options: ['warning_level=2'])

wl_client_dep = dependency('wayland-client')
wl_protos_dep = dependency('wayland-protocols')
scanner = find_program('wayland-scanner')

xdg_xml = wl_protos_dep.get_variable(pkgconfig: 'pkgdatadir') + '/stable/xdg-shell/xdg-shell.xml'

xdg_header = custom_target('xdg-shell-header',
  command: [scanner, 'client-header', '@INPUT@', '@OUTPUT@'],
  input: xdg_xml,
  output: 'xdg-shell-client-protocol.h')

xdg_code = custom_target('xdg-shell-code',
  command: [scanner, 'private-code', '@INPUT@', '@OUTPUT@'],
  input: xdg_xml,
  output: 'xdg-shell-protocol.c')

executable('apklayer-window',
  ['src/window.c', xdg_header, xdg_code],
  dependencies: [wl_client_dep],
  install: false)
