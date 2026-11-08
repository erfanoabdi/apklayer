common = '../common'  # shared with the manager (manager/meson.build)
executable('apklayer', ['main.cpp', 'util.cpp', 'layout.cpp', 'env.cpp', 'stack.cpp', 'apps.cpp', 'desktop.cpp', 'run.cpp', 'spare.cpp', 'manage.cpp', 'migrate.cpp', 'gpus.cpp', 'logs.cpp', 'daemon.cpp', 'sandbox.cpp', 'portal.cpp', 'providers.cpp', 'device_hals.cpp', 'autostart.cpp', 'unit.cpp', 'halium.cpp', 'binder.cpp', 'flatpak.cpp', 'purge.cpp', 'setup.cpp', common / 'proc.cpp', common / 'json.cpp', common / 'releases.cpp', common / 'fetch.cpp'],
  # APKLAYER_LIB: where `meson install` puts the runtime (cli/layout.cpp: the source tree's CLI uses that install)
  # APKLAYER_PLUGINDIR: where third-party hardware plugins install (apklayer-plugin.pc's plugindir, docs/PLUGINS.md)
  cpp_args: ['-DAPKLAYER_VERSION="@0@"'.format(version), '-DAPKLAYER_LIB="@0@"'.format(pkg_lib), '-DAPKLAYER_PLUGINDIR="@0@"'.format(plugin_dir)],
  include_directories: include_directories('../bridge/hals/include', '../common'), install: true, install_dir: pkg_lib / 'cli')
