# apklayer-shell needs Wayland; it is skipped where Wayland isn't available (e.g. macOS).
wl_client_dep = dependency('wayland-client', required: host_machine.system() == 'linux')
wl_protos_dep = dependency('wayland-protocols', required: host_machine.system() == 'linux')
xkb_dep = dependency('xkbcommon', required: host_machine.system() == 'linux')
scanner = find_program('wayland-scanner', required: host_machine.system() == 'linux')
if wl_client_dep.found() and wl_protos_dep.found() and xkb_dep.found() and scanner.found()
  xdg_xml = wl_protos_dep.get_variable(pkgconfig: 'pkgdatadir') / 'stable/xdg-shell/xdg-shell.xml'
  xdg_header = custom_target('xdg-shell-client-header',
    output: 'xdg-shell-client-protocol.h',
    input: xdg_xml,
    command: [scanner, 'client-header', '@INPUT@', '@OUTPUT@'])
  xdg_code = custom_target('xdg-shell-code',
    output: 'xdg-shell-protocol.c',
    input: xdg_xml,
    command: [scanner, 'private-code', '@INPUT@', '@OUTPUT@'])
  executable('apklayer-shell',
    ['shell.c', xdg_header, xdg_code],
    dependencies: [wl_client_dep, xkb_dep],
    c_args: ['-Werror=incompatible-pointer-types', '-Werror=implicit-function-declaration'],
    install: false)
endif
