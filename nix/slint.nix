# The prebuilt Slint C++ library, its compiler and the Material component set that
# manager/fetch-slint.sh pins, laid out as that script lays them out (-Dslint=<this>).
#
# The three archives are fetched here and put where the script looks for them, so it downloads
# nothing and still checks every hash; the layout stays the script's own rather than a copy of it
# that could drift. Unlike Android's libraries these are ordinary host binaries, so they are the one
# part of apklayer that wants autoPatchelfHook.
{
  lib,
  stdenv,
  fetchurl,
  autoPatchelfHook,
  python3,
  fontconfig,
  freetype,
  libxkbcommon,
  libinput,
  libevdev,
  mtdev,
  libGL,
  libgbm,
  libdrm,
  udev,
  wayland,
  libx11,
  libxcursor,
  libxi,
  libxrandr,
}: let
  version = "1.18.1";
  materialVersion = "1.1.0";

  # the names and hashes of manager/fetch-slint.sh, per architecture
  byPlatform = {
    aarch64-linux = {
      cpp = "Slint-cpp-${version}-Linux-arm64";
      cppHash = "810cfed786c3f63aea9c1b73371a02ed591129d18e077094afa9e024e2d714fd";
      compiler = "slint-compiler-Linux-aarch64";
      compilerHash = "6d704012132ec8c8fadddefb86ba192cd83e09048edbcf61aee457aca300c78c";
    };
    x86_64-linux = {
      cpp = "Slint-cpp-${version}-Linux-x86_64";
      cppHash = "f0b24ea601b80afe241677d1179bf5215dceae529f2b275b678a0af9a144b255";
      compiler = "slint-compiler-Linux-x86_64";
      compilerHash = "543091e6102a02db3403aa77c774a11d9f383b31cb03e25cddad63269a1fb779";
    };
  };

  target =
    byPlatform.${stdenv.hostPlatform.system}
    or (throw "manager/fetch-slint.sh has no pinned Slint for ${stdenv.hostPlatform.system}");

  release = name: "https://github.com/slint-ui/slint/releases/download/v${version}/${name}.tar.gz";

  cppArchive = fetchurl {
    url = release target.cpp;
    sha256 = target.cppHash;
  };

  compilerArchive = fetchurl {
    url = release target.compiler;
    sha256 = target.compilerHash;
  };

  materialArchive = fetchurl {
    url = "https://material.slint.dev/zip/material-${materialVersion}.zip";
    sha256 = "83c57b6f1b8799ddacd94b7e8b140f3f2112c36407b6df31883da0ba4e2dd425";
  };
in
  stdenv.mkDerivation {
    pname = "slint-prebuilt";
    inherit version;

    dontUnpack = true;
    dontConfigure = true;
    dontBuild = true;

    nativeBuildInputs = [autoPatchelfHook python3];

    buildInputs = [
      stdenv.cc.cc.lib
      fontconfig
      freetype
      libxkbcommon
      libinput
      libevdev
      mtdev
      libGL
      libgbm
      libdrm
      udev
      wayland
      libx11
      libxcursor
      libxi
      libxrandr
    ];

    installPhase = ''
      runHook preInstall

      mkdir -p "$out"
      cp "${cppArchive}" "$out/${target.cpp}.tar.gz"
      cp "${compilerArchive}" "$out/${target.compiler}.tar.gz"
      cp "${materialArchive}" "$out/material-${materialVersion}.zip"
      chmod -R u+w "$out"

      # finds the archives already there, verifies their hashes and unpacks them into cpp/ bin/ material/
      sh ${../manager/fetch-slint.sh} "$out"

      rm -f "$out"/*.tar.gz "$out"/*.zip

      runHook postInstall
    '';

    meta = {
      description = "Prebuilt Slint C++ library and compiler for apklayer-manager";
      homepage = "https://slint.dev";
      license = lib.licenses.gpl3Only;
      platforms = builtins.attrNames byPlatform;
    };
  }
