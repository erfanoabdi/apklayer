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

# JNI headers for libapklayer-wl's apklayer.wayland.WlWindow entry points.
jni_out = run_command('sh', '-c',
  'R=$(readlink -f "$(command -v javac)"); echo "${R%/bin/javac}/include"', check: true)
jni_inc = jni_out.stdout().strip()
jni_args = ['-I' + jni_inc, '-I' + jni_inc + '/linux', '-fPIC']

# Minimal presenter for the Robolectric-based runner: plain C ABI (apklayer_wayland_*)
# plus the JNI entry points of apklayer.wayland.WlWindow.
shared_library('apklayer-wl',
  ['src/present.c', xdg_header, xdg_code],
  dependencies: [wl_client_dep],
  c_args: jni_args,
  install: false)

# Out-of-process presenter: maps the app's shared framebuffer (see src/presentd.c).
executable('apklayer-presentd',
  ['src/presentd.c', 'src/present.c', xdg_header, xdg_code],
  dependencies: [wl_client_dep],
  c_args: jni_args,
  install: false)
