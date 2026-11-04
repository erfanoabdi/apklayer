version = get_option('package_version') != '' ? get_option('package_version') : meson.project_version()
executable('apklayer', ['main.cpp', 'util.cpp', 'layout.cpp', 'env.cpp', 'stack.cpp', 'apps.cpp', 'desktop.cpp', 'run.cpp', 'spare.cpp', 'manage.cpp'],
  cpp_args: ['-DAPKLAYER_VERSION="' + version + '"'], install: true)
