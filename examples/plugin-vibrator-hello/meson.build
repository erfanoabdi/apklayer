# An out-of-tree apklayer plugin (docs/PLUGINS.md): needs only apklayer-dev (apklayer-plugin.pc). The library name is the plugin's
# identity: libapklayer-<domain>-<backend>.so, here the vibrator backend "hello"; it installs into apklayer's plugin dir.
project('apklayer-vibrator-hello', 'c', default_options: ['warning_level=2', 'c_std=c11'])
apklayer = dependency('apklayer-plugin')
shared_library('apklayer-vibrator-hello', 'hello.c',
  dependencies: [apklayer, dependency('threads')],
  gnu_symbol_visibility: 'hidden',  # only the API (declared exported in the headers) leaves the library
  install: true, install_dir: apklayer.get_variable(pkgconfig: 'plugindir'))
