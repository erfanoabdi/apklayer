{
  description = "Run Android apps on Linux in Wayland windows";

  inputs = {
    nixpkgs = {
      type = "github";
      owner = "NixOS";
      repo = "nixpkgs";
      ref = "nixpkgs-unstable";
    };
  };

  outputs = {nixpkgs, ...}: let
    inherit (nixpkgs) lib;

    makeDefault = x: {default = x;};

    # apklayer is Linux only, and ships for these two (debian/control, rpm/apklayer.spec)
    supportedSystems = ["aarch64-linux" "x86_64-linux"];

    pkgsFor = let
      perSystem = system:
        import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };
    in
      lib.genAttrs supportedSystems perSystem;

    forAllSystems = f: let
      mapSystem = _system: f;
    in
      lib.mapAttrs mapSystem pkgsFor;

    makePackages = pkgs: rec {
      default = apklayer;
      apklayer = pkgs.callPackage ./nix/package.nix {};
      apklayer-manager = pkgs.callPackage ./nix/manager.nix {inherit apklayer;};
      # the inputs the tree would otherwise fetch while it builds, each on its own so they can be
      # built, cached and checked by themselves
      apklayer-prebuilts = pkgs.callPackage ./nix/prebuilts.nix {};
      apklayer-maven-deps = pkgs.callPackage ./nix/maven-deps.nix {};
      apklayer-slint = pkgs.callPackage ./nix/slint.nix {};
      apklayer-bionic-translation = pkgs.callPackage ./nix/bionic-translation.nix {};
    };

    makeDevShell = pkgs: let
      packages = makePackages pkgs;
    in
      makeDefault (pkgs.mkShell {
        inputsFrom = [packages.apklayer packages.apklayer-manager];

        packages = with pkgs; [
          alejandra
          clang-tools
          cocogitto
          mesa-demos
          statix
          unzip
          wget
          zip
          zstd
        ];

        # everything the tree fetches at configure time, already in the store: a plain
        #   meson setup b && ninja -C b
        # inside this shell needs no network (docs/INSTALL.md step 4).
        env = {
          APKLAYER_PREBUILTS = "${packages.apklayer-prebuilts}";
          APKLAYER_DEPS_LOCAL = "${packages.apklayer-maven-deps}";
          APKLAYER_SLINT = "${packages.apklayer-slint}";
        };

        shellHook = ''
          echo "✅ Development environment is ready"
        '';
      });

    makeChecks = pkgs: let
      packages = makePackages pkgs;

      # the module evaluated against a throwaway system: catches option and type errors without
      # building anything (seq forces the evaluation, the marker depends on nothing)
      system = pkgs.stdenv.hostPlatform.system;

      evaluated =
        (lib.nixosSystem {
          modules = [
            ./nix/nixos-module.nix
            {
              programs.apklayer = {
                enable = true;
                package = packages.apklayer;
                manager = packages.apklayer-manager;
              };

              nixpkgs.hostPlatform = system;
              # enough of a system to evaluate: no bootloader, no file systems to declare
              boot.isContainer = true;
              system.stateVersion = "25.05";
            }
          ];
        })
        .config
        .system
        .build
        .toplevel
        .drvPath;
    in {
      inherit (packages) apklayer apklayer-manager;

      formatting =
        pkgs.runCommand "apklayer-nix-formatting" {
          nativeBuildInputs = [pkgs.alejandra];
        } ''
          alejandra --check "${./flake.nix}" "${./nix}"
          touch "$out"
        '';

      nixos-module =
        builtins.seq evaluated
        (pkgs.runCommand "apklayer-nixos-module" {} "touch \"$out\"");
    };

    makeFormatter = pkgs: pkgs.alejandra;

    modules = {
      default = ./nix/nixos-module.nix;
      apklayer = ./nix/nixos-module.nix;
    };

    homeModules = {
      default = ./nix/hm-module.nix;
      apklayer = ./nix/hm-module.nix;
    };
  in {
    packages = forAllSystems makePackages;
    devShells = forAllSystems makeDevShell;
    checks = forAllSystems makeChecks;
    formatter = forAllSystems makeFormatter;

    nixosModules = modules;

    inherit homeModules;
  };
}
