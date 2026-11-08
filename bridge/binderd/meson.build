# apklayer-binderd: the instance's userspace binder (docs/BINDER.md), into the native dir's bin (cli/binder.cpp starts it)
executable('apklayer-binderd', ['main.cpp', 'proc.cpp', 'thread.cpp', 'transaction.cpp', 'node.cpp', 'alloc.cpp', 'wire.c'],
  cpp_args: ['-std=c++17'], override_options: ['optimization=2', 'warning_level=2', 'cpp_std=c++17'], install: true, install_dir: native_dir / 'bin')
