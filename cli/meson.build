version = get_option('package_version') != '' ? get_option('package_version') : meson.project_version()  # the deb passes its own (debian/rules)
executable('apklayer', ['main.cpp', 'util.cpp', 'layout.cpp', 'env.cpp', 'stack.cpp', 'apps.cpp', 'desktop.cpp', 'run.cpp', 'spare.cpp', 'manage.cpp', 'migrate.cpp', 'gpus.cpp', 'logs.cpp', 'daemon.cpp', 'sandbox.cpp', 'portal.cpp', 'providers.cpp'],
  # APKLAYER_LIB: where `meson install` puts the runtime (cli/layout.cpp: the source tree's CLI uses that install)
  cpp_args: ['-DAPKLAYER_VERSION="@0@"'.format(version), '-DAPKLAYER_LIB="@0@"'.format(pkg_lib)], install: true, install_dir: pkg_lib / 'cli')
