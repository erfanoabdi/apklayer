# apklayer-manager, a window over the CLI (docs/INSTALL.md "The manager").
#
# Its own Meson project, so the main build never needs Slint, but it compiles ../common beside its
# own sources: the whole tree is the source and meson is pointed at manager/, as the Flatpak and
# debian/rules both do.
{
  lib,
  stdenv,
  callPackage,
  meson,
  ninja,
  pkg-config,
  makeWrapper,
  libpng,
  slint ? callPackage ./slint.nix {},
  apklayer ? callPackage ./package.nix {},
}:
stdenv.mkDerivation (finalAttrs: {
  pname = "apklayer-manager";
  version = "0.1"; # manager/meson.build

  src = lib.fileset.toSource {
    root = ../.;
    fileset = lib.fileset.unions [
      ../manager
      ../common # backend.cpp and the CLI's shared sources
      ../data # ui/app.slint embeds the icon from here
    ];
  };

  nativeBuildInputs = [meson ninja pkg-config makeWrapper];

  buildInputs = [libpng];

  # libslint_cpp.so is installed beside the binary as a copy of the prebuilt one, which
  # nix/slint.nix has already patched; shrinking its RPATH here would undo that.
  dontPatchELF = true;

  configurePhase = ''
    runHook preConfigure

    meson setup b manager \
      --prefix="$out" \
      --buildtype=release \
      -Dlibdir=lib/apklayer \
      -Dslint="${slint}"

    runHook postConfigure
  '';

  buildPhase = ''
    runHook preBuild
    ninja -C b apklayer-manager
    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall
    meson install -C b --no-rebuild
    runHook postInstall
  '';

  # the manager drives the CLI: every button is an `apklayer` command
  postFixup = ''
    wrapProgram "$out/bin/apklayer-manager" \
      --prefix PATH : "${lib.makeBinPath [apklayer]}"
  '';

  meta = {
    description = "Window over the apklayer CLI: apps, instances and Android properties";
    homepage = "https://github.com/erfanoabdi/apklayer";
    license = lib.licenses.agpl3Plus;
    mainProgram = "apklayer-manager";
    platforms = ["aarch64-linux" "x86_64-linux"];
  };
})
