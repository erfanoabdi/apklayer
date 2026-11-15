# The pinned Java artifacts of deps/deps.lock, laid out as $APKLAYER_DEPS_LOCAL so that
# tools/gen/fetch-dep.py finds every one of them and downloads nothing (deps/meson.build).
#
# flatpak/deps-sources.json is generated from deps.lock by tools/gen/flatpak-sources.py, because the
# Flatpak build has no network either; its hashes are the ones Nix needs too. Reading that file
# leaves no hash list to maintain here: regenerate it after a deps.lock change and Nix follows.
#
# What it does not carry comes from the prebuilts (its docstring): android-all/ is not built, and
# the sdk: entry (android-stub.jar) is in <prebuilts>/common.
{
  lib,
  fetchurl,
  runCommand,
}: let
  sources = builtins.fromJSON (builtins.readFile ../flatpak/deps-sources.json);

  wanted = builtins.filter (source: source.dest == "flatpak-deps") sources;

  fetch = source: {
    name = source."dest-filename";
    path = fetchurl {inherit (source) url sha256;};
  };

  copy = file: ''cp "${file.path}" "$out/${file.name}"'';

  files = map fetch wanted;
in
  runCommand "apklayer-maven-deps" {} ''
    mkdir -p "$out"
    ${lib.concatMapStringsSep "\n" copy files}
  ''
