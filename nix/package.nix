# apklayer with its Android half built in (-Dandroid_runtime, the default): the framework jars carry
# the JNI shim and are instrumented here, so nothing is downloaded or written into a home at first
# run. cli/layout.cpp calls that a complete runtime and uses this prefix instead of a bundle.
#
# The build follows debian/rules, which is the recipe this packaging is derived from.
{
  lib,
  stdenv,
  callPackage,
  # build tools
  meson,
  ninja,
  pkg-config,
  python3,
  jdk21,
  wayland-scanner,
  git,
  makeWrapper,
  # libraries
  cairo,
  elfutils,
  glib,
  gst_all_1,
  libbsd,
  libdrm,
  libgbm,
  libglvnd,
  libmd,
  libpng,
  libpulseaudio,
  libunwind,
  libxkbcommon,
  pipewire,
  wayland,
  wayland-protocols,
  # runtime
  mesa-demos,
  unzip,
  wget,
  zip,
  zstd,
  prebuilts ? callPackage ./prebuilts.nix {},
  mavenDeps ? callPackage ./maven-deps.nix {},
  bionicTranslation ? callPackage ./bionic-translation.nix {},
}: let
  # bridge/portal's D-Bus helpers (the file chooser, the launcher portal) import gi
  python = python3.withPackages (ps: [ps.pygobject3]);

  gst = with gst_all_1; [
    gstreamer
    gst-plugins-base
    gst-plugins-good
    gst-plugins-bad
    gst-libav
  ];

  # what the CLI shells out to: docs/INSTALL.md's packages, debian/control's Depends.
  #
  # Not here: newuidmap/newgidmap for the per-app sandbox (cli/sandbox.cpp). They only work setuid
  # root, which a store copy is not; the system's own (NixOS puts them in /run/wrappers/bin) have
  # to stay ahead on PATH, and a copy here would hide them.
  runtimePath = [
    jdk21
    python
    zip
    unzip
    wget
    zstd
    mesa-demos # eglinfo, the GLES version probe
  ];
in
  stdenv.mkDerivation (finalAttrs: {
    pname = "apklayer";
    version = "0.4.18";

    src = lib.fileset.toSource {
      root = ../.;
      fileset = lib.fileset.difference ../. (lib.fileset.unions [
        (lib.fileset.maybeMissing ../.git)
        (lib.fileset.maybeMissing ../.claude)
        (lib.fileset.maybeMissing ../b)
        (lib.fileset.maybeMissing ../inst)
        (lib.fileset.maybeMissing ../result)
        (lib.fileset.maybeMissing ../subprojects/bionic_translation)
        # the packaging itself: no input of the Meson build, and every edit here would
        # otherwise be a whole rebuild
        ../nix
        ../flake.nix
        (lib.fileset.maybeMissing ../flake.lock)
      ]);
    };

    outputs = ["out" "dev"]; # dev: the hardware plugin API, as the apklayer-dev deb

    nativeBuildInputs = [
      meson
      ninja
      pkg-config
      python
      jdk21 # javac and jar: meson.build declares java as a language
      wayland-scanner
      git # patches/apply uses git apply
      makeWrapper
    ];

    buildInputs =
      [
        cairo
        elfutils
        glib
        libbsd
        libdrm
        libgbm
        libglvnd
        libmd
        libpng
        libpulseaudio
        libunwind
        libxkbcommon
        pipewire
        wayland
        wayland-protocols
        jdk21
      ]
      ++ gst;

    # debian/rules builds with meson's own flags and none of the distribution's: the path layer and
    # bionic_translation interpose symbols across libraries, which bindnow and fortify break.
    hardeningDisable = ["all"];

    # dh_strip -Xlibdl_bio: libdl_bio.so is /system/bin/linker64 to Android code, and PLT hook
    # libraries (bytehook) read its .symtab as they would the linker's.
    dontStrip = true;

    # Android's own libraries are bionic ELFs that bionic_translation's linker loads, never the
    # host's: rewriting their RPATHs would break them. debian/rules patches no ELF file either.
    dontPatchELF = true;

    # bridge/native/android-root carries the tree's own links to Android's libraries, which resolve
    # beside the runtime (lib/apklayer/native/android-root, next to the android-libs symlink) and
    # dangle in the copy under share/apklayer that the CLI reads as data. Upstream's layout.
    dontCheckForBrokenSymlinks = true;

    env = {
      # tools/gen/fetch-dep.py takes deps.lock's artifacts from here instead of Maven Central
      APKLAYER_DEPS_LOCAL = "${mavenDeps}";
      JAVA_HOME = "${jdk21}";
    };

    postUnpack = ''
      # the wrap's checkout, which meson.build patches in place at configure time (patches/apply).
      # Without it meson runs `meson subprojects download`, and the sandbox has no network.
      cp -r "${bionicTranslation}" "$sourceRoot/subprojects/bionic_translation"
      chmod -R u+w "$sourceRoot/subprojects/bionic_translation"
    '';

    mesonBuildType = "release"; # the project's own default_options

    mesonFlags = [
      (lib.mesonOption "prebuilts" "${prebuilts}")
      (lib.mesonOption "package_version" finalAttrs.version)
    ];

    # bionic_translation's own install would add a system-wide copy beside ours
    mesonInstallFlags = ["--skip-subprojects"];

    postInstall = ''
      wrapProgram "$out/lib/apklayer/cli/apklayer" \
        --prefix PATH : "${lib.makeBinPath runtimePath}" \
        --prefix GST_PLUGIN_SYSTEM_PATH_1_0 : "${lib.makeSearchPathOutput "lib" "lib/gstreamer-1.0" gst}" \
        --set-default APKLAYER_NATIVE_JAVA "${jdk21}/bin/java"
    '';

    # the modules install the system providers from here: meson installs no system-apps
    passthru = {inherit prebuilts;};

    meta = {
      description = "Run Android apps on Linux in Wayland windows";
      longDescription = ''
        apklayer runs Android apps (APKs) as Linux processes: the app's dex code is converted to
        JVM classes, Android's own native libraries run through bionic_translation, and each app
        opens a Wayland window. This build carries the Android runtime, so it needs no per-user
        download; it does need a Wayland session and a GPU with Mesa (GBM, EGL, GLES).
      '';
      homepage = "https://github.com/erfanoabdi/apklayer";
      license = lib.licenses.agpl3Plus;
      mainProgram = "apklayer";
      platforms = ["aarch64-linux" "x86_64-linux"];
    };
  })
