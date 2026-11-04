foreach f : deps_lock
  if f[1].startswith('libs/')
    name = f[1].substring(5)
    # ASM is what an install runs (apklayer.tools.DenyInstrument, cli/apps.cpp: <deps>/libs/*)
    deps += {f[1]: custom_target(name, output: name, command: [fetch_dep, f[0], f[2], '@OUTPUT@', deps_local], build_by_default: true,
      install: name.startswith('asm-'), install_dir: native_dir / 'libs')}
  endif
endforeach
