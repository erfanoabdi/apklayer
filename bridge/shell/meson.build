# apklayer-shell needs Wayland; it is skipped where Wayland isn't available (e.g. macOS).
wl_client_dep = dependency('wayland-client', required: host_machine.system() == 'linux')
wl_protos_dep = dependency('wayland-protocols', required: host_machine.system() == 'linux')
scanner = find_program('wayland-scanner', required: host_machine.system() == 'linux')
if wl_client_dep.found() and wl_protos_dep.found() and scanner.found()

xdg_xml = wl_protos_dep.get_variable(pkgconfig: 'pkgdatadir') + '/stable/xdg-shell/xdg-shell.xml'

xdg_header = custom_target('xdg-shell-header',
  command: [scanner, 'client-header', '@INPUT@', '@OUTPUT@'],
  input: xdg_xml,
  output: 'xdg-shell-client-protocol.h')

xdg_code = custom_target('xdg-shell-code',
  command: [scanner, 'private-code', '@INPUT@', '@OUTPUT@'],
  input: xdg_xml,
  output: 'xdg-shell-protocol.c')

# Per-session Wayland shell: app windows, zero-copy shm frames, input (see shell.c).
executable('apklayer-shell',
  ['shell.c', xdg_header, xdg_code],
  dependencies: [wl_client_dep],
  install: false)
endif
