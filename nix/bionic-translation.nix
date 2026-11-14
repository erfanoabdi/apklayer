# bionic_translation's source at the revision subprojects/bionic_translation.wrap pins.
#
# meson.build applies patches/bionic_translation on top of it at configure time, so this is the
# unpatched upstream tree: nix/package.nix copies it to subprojects/bionic_translation (writable)
# and lets the configure step patch it, exactly as a wrap checkout would be. Without it meson runs
# `meson subprojects download`, which has no network in the sandbox.
#
# The revision is read from the wrap so the two can never drift; only the hash is kept here.
{
  lib,
  fetchgit,
}: let
  wrap = builtins.readFile ../subprojects/bionic_translation.wrap;

  revision = lib.head (lib.match ".*revision = ([0-9a-f]{40}).*" wrap);
in
  fetchgit {
    name = "bionic-translation-source";
    url = "https://gitlab.com/android_translation_layer/bionic_translation";
    rev = revision;
    hash = "sha256-WbvFNEdD22fo55F5ly+sMDk5kM7sLaItMKFmP3a5CvY=";
  }
