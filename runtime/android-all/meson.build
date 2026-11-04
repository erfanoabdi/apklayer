# the framework jar with our JNI shim in its native/ (Robolectric loads the library from the framework jar's own native/ first); the SDK provider
# asks for it under both names
framework_jar = custom_target(framework_name, input: [framework, shim], output: framework_name,
  command: [jar_put, '@INPUT0@', '@OUTPUT@', shim_entries], install: true, install_dir: native_dir / 'aosp/android-all')
install_symlink('android-all-current-robolectric-r0.jar', pointing_to: framework_name, install_dir: native_dir / 'aosp/android-all')
