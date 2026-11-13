# the framework jar instrumented once (apklayer.tools.PreInstrument: the app JVMs' sandbox configuration, so their loader skips ~3600 classes),
# then the shim as in android-all/: a shim change does not instrument again. App JVMs take it while it is newer than its inputs (cli/env.cpp).
instrumented = custom_target('instrumented.jar', input: [framework_patched, overlay_jar, host_jar], output: 'instrumented.jar',
  command: [java, '-cp', ':'.join([overlay_jar.full_path(), host_jar.full_path(), sim_classes, robolectric]), 'apklayer.tools.PreInstrument',
    '@INPUT0@', '@OUTPUT@'])
custom_target(framework_name, input: [instrumented, shim], output: framework_name,
  command: [jar_put, '@INPUT0@', '@OUTPUT@', shim_entries], install: true, install_dir: native_dir / 'aosp/android-all-instrumented')
install_symlink('android-all-current-robolectric-r0.jar', pointing_to: framework_name, install_dir: native_dir / 'aosp/android-all-instrumented')
