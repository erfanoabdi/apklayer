common = '../common'  # shared with the manager (manager/meson.build)
executable('apklayer', ['main.cpp', 'config.cpp', 'util.cpp', 'layout.cpp', 'env.cpp', 'stack.cpp', 'build_props.cpp', 'stop.cpp', 'apps.cpp', 'desktop.cpp', 'filetypes.cpp', 'open.cpp','run.cpp', 'spare.cpp', 'manage.cpp', 'migrate.cpp', 'gpus.cpp', 'vkprobe.cpp', 'logs.cpp', 'daemon.cpp', 'broker.cpp', 'sandbox.cpp', 'portal.cpp', 'providers.cpp', 'device_hals.cpp', 'autostart.cpp', 'unit.cpp', 'halium.cpp', 'datamove.cpp', 'binder.cpp', 'flatpak.cpp', 'purge.cpp', 'setup.cpp', 'gapps.cpp', 'vpn.cpp', 'vpn_guard.cpp', common / 'proc.cpp', common / 'json.cpp', common / 'releases.cpp', common / 'fetch.cpp', common / 'downloads.cpp'],
  # APKLAYER_LIB: where `meson install` puts the runtime (cli/layout.cpp: the source tree's CLI uses that install)
  # APKLAYER_PLUGINDIR: where third-party hardware plugins install (apklayer-plugin.pc's plugindir, docs/PLUGINS.md)
  cpp_args: ['-DAPKLAYER_VERSION="@0@"'.format(version), '-DAPKLAYER_LIB="@0@"'.format(pkg_lib), '-DAPKLAYER_PLUGINDIR="@0@"'.format(plugin_dir)],
  include_directories: include_directories('../bridge/hals/include', '../common'), install: true, install_dir: pkg_lib / 'cli')

# the root half of the host-wide VPN (docs/VPN.md): run through pkexec where NetworkManager cannot make the TUN; polkit action names its path
executable('apklayer-vpn-helper', 'vpn_helper.cpp', install: true, install_dir: pkg_lib / 'cli')
configure_file(input: '../data/org.apklayer.vpn.policy.in', output: 'org.apklayer.vpn.policy', configuration: {'HELPER': pkg_lib / 'cli/apklayer-vpn-helper'},
  install: true, install_dir: get_option('datadir') / 'polkit-1/actions')
