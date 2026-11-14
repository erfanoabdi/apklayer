# Home Manager module for apklayer: the per-user half of nix/nixos-module.nix.
#
# What it cannot do, because it is not root: the udev rule that opens the GPU render nodes to app
# uids, the subordinate id ranges the per-app sandbox needs, and the kernel binder's devices. A
# user who wants those needs the NixOS module (or their distribution's equivalent); the userspace
# binder, which is the default, needs none of them.
{
  config,
  lib,
  pkgs,
  ...
}: let
  cfg = config.programs.apklayer;
in {
  options.programs.apklayer = {
    enable = lib.mkEnableOption "apklayer, which runs Android apps in Wayland windows";

    package = lib.mkOption {
      type = lib.types.package;
      default = pkgs.callPackage ./package.nix {};
      defaultText = lib.literalExpression "pkgs.callPackage ./package.nix {}";
      description = "The apklayer package to use.";
    };

    manager = lib.mkOption {
      type = lib.types.nullOr lib.types.package;
      default = pkgs.callPackage ./manager.nix {apklayer = cfg.package;};
      defaultText = lib.literalExpression "pkgs.callPackage ./manager.nix {}";
      description = "apklayer-manager, the window over the CLI. Null leaves it out.";
    };

    daemon = lib.mkOption {
      type = lib.types.bool;
      default = true;
      description = ''
        Start apklayerd at login (cli/daemon.cpp). Without it the daemon starts on demand.
      '';
    };

    systemApps = lib.mkOption {
      type = lib.types.bool;
      default = true;
      description = ''
        Install Android's content providers and the WebView at login (docs/INSTALL.md step 6).
        They come from the prebuilts in the store, so nothing is downloaded.
      '';
    };

    sandbox = lib.mkOption {
      type = lib.types.bool;
      default = false;
      description = ''
        Set APKLAYER_SANDBOX for the session, so every app process runs as its own uid. It needs
        65536 subordinate ids for this user, which only the system can grant (see the NixOS
        module's sandbox options, or usermod --add-subuids).
      '';
    };
  };

  config = lib.mkIf cfg.enable {
    home.packages = [cfg.package] ++ lib.optional (cfg.manager != null) cfg.manager;

    home.sessionVariables = lib.mkIf cfg.sandbox {APKLAYER_SANDBOX = "1";};

    # tools/host-setup/apklayerd.service, with the store's binary instead of PATH's
    systemd.user.services.apklayerd = {
      Unit = {
        Description = "apklayer daemon (Android instances and their apps)";
        After = ["graphical-session.target"];
      };

      Service =
        {
          ExecStart = "${lib.getExe cfg.package} daemon";
          Restart = "on-failure";
          KillMode = "control-group";
        }
        // lib.optionalAttrs cfg.sandbox {Environment = ["APKLAYER_SANDBOX=1"];};

      Install = lib.mkIf cfg.daemon {WantedBy = ["default.target"];};
    };

    systemd.user.services.apklayer-system-apps = lib.mkIf cfg.systemApps {
      Unit = {
        Description = "Install Android's content providers and the WebView";
        After = ["apklayerd.service"];
      };

      Service = {
        Type = "oneshot";
        RemainAfterExit = true;
        ExecStart = lib.getExe (pkgs.callPackage ./system-apps.nix {apklayer = cfg.package;});
      };

      Install = {WantedBy = ["default.target"];};
    };
  };
}
