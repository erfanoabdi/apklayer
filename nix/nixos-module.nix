# NixOS module for apklayer. Usable from the flake (nixosModules.default) or by importing this
# file directly; it takes its packages from options, so neither needs the other.
#
# What the deb's postinst does and this does not: the AppArmor profile that allows the per-app
# sandbox its user namespaces. Ubuntu 23.10+ restricts unprivileged user namespaces, NixOS does not.
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
      description = ''
        apklayer-manager, the window over the CLI. Null leaves it out.
      '';
    };

    daemon = lib.mkOption {
      type = lib.types.bool;
      default = true;
      description = ''
        Start apklayerd at login (cli/daemon.cpp). Without it the daemon starts on demand; it is
        needed up front only for an instance's always-on mode and for desktop notifications while
        apps are closed.
      '';
    };

    systemApps = lib.mkOption {
      type = lib.types.bool;
      default = true;
      description = ''
        Install Android's content providers and the WebView for each user at login
        (docs/INSTALL.md step 6). They come from the prebuilts in the store, so nothing is
        downloaded. Apps that show web pages or pick files need them.
      '';
    };

    binder = {
      kernel = lib.mkOption {
        type = lib.types.bool;
        default = false;
        description = ''
          Use the kernel's binder driver instead of apklayer's userspace one (docs/BINDER.md).
          The userspace binder is the default everywhere and needs no kernel support and no root;
          turn this on only if you want binderfs.
        '';
      };

      instances = lib.mkOption {
        type = lib.types.listOf lib.types.str;
        default = [];
        example = ["work" "test"];
        description = ''
          Instances that get their own binderfs nodes at boot, besides the default one. Only used
          when binder.kernel is on.
        '';
      };
    };

    sandbox = {
      enable = lib.mkOption {
        type = lib.types.bool;
        default = false;
        description = ''
          Run every app process as its own uid in a user namespace per instance (APKLAYER_SANDBOX).
          Each user in sandbox.users needs 65536 subordinate ids, which this allocates.

          The uid maps are written by newuidmap/newgidmap, which NixOS already provides setuid in
          /run/wrappers/bin. `apklayer setup --check` looks for them in /usr/bin and so reports the
          sandbox as unavailable here; it still works.
        '';
      };

      users = lib.mkOption {
        type = lib.types.listOf lib.types.str;
        default = [];
        example = ["alice"];
        description = "Users that get the subordinate id range the per-app sandbox needs.";
      };
    };
  };

  config = lib.mkIf cfg.enable {
    assertions = [
      {
        assertion = cfg.sandbox.enable -> cfg.sandbox.users != [];
        message = "programs.apklayer.sandbox.enable needs sandbox.users: the per-app sandbox takes each user's own subordinate ids.";
      }
    ];

    environment.systemPackages = [cfg.package] ++ lib.optional (cfg.manager != null) cfg.manager;

    # The sandbox runs apps as uids the seat's ACL does not name, so the render nodes have to be
    # open to them (the rule makes them 0666). Only the sandbox needs it, so unlike the deb — which
    # installs it either way — it goes in only when the sandbox is on.
    services.udev.packages = lib.mkIf cfg.sandbox.enable [
      (pkgs.runCommand "apklayer-udev-rules" {} ''
        install -Dm644 \
          "${cfg.package}/share/apklayer/tools/host-setup/70-apklayer-render.rules" \
          "$out/lib/udev/rules.d/70-apklayer-render.rules"
      '')
    ];

    # tools/host-setup/apklayerd.service, with the store's binary instead of PATH's
    systemd.user.services.apklayerd = {
      description = "apklayer daemon (Android instances and their apps)";
      after = ["graphical-session.target"];
      wantedBy = lib.mkIf cfg.daemon ["default.target"];

      environment = lib.mkIf cfg.sandbox.enable {APKLAYER_SANDBOX = "1";};

      serviceConfig = {
        ExecStart = "${lib.getExe cfg.package} daemon";
        Restart = "on-failure";
        KillMode = "control-group";
      };
    };

    systemd.user.services.apklayer-system-apps = lib.mkIf cfg.systemApps {
      description = "Install Android's content providers and the WebView for this user";
      after = ["apklayerd.service"];
      wantedBy = ["default.target"];

      serviceConfig = {
        Type = "oneshot";
        RemainAfterExit = true;
        ExecStart = lib.getExe (pkgs.callPackage ./system-apps.nix {apklayer = cfg.package;});
      };
    };

    environment.sessionVariables = lib.mkIf cfg.sandbox.enable {APKLAYER_SANDBOX = "1";};

    users.users = lib.mkIf cfg.sandbox.enable (
      lib.genAttrs cfg.sandbox.users (_user: {
        subUidRanges = [
          {
            startUid = 100000;
            count = 65536;
          }
        ];
        subGidRanges = [
          {
            startGid = 100000;
            count = 65536;
          }
        ];
      })
    );

    # the instances whose binderfs nodes apklayer-binder makes at boot (debian/instances)
    environment.etc."apklayer/instances" = lib.mkIf cfg.binder.kernel {
      text = lib.concatMapStrings (instance: instance + "\n") cfg.binder.instances;
    };

    boot.kernelModules = lib.mkIf cfg.binder.kernel ["binder_linux"];

    boot.extraModprobeConfig = lib.mkIf cfg.binder.kernel ''
      options binder_linux devices=binder,hwbinder,vndbinder
    '';

    # tools/host-setup/binder.sh: loads the module, mounts binderfs and makes the nodes. The
    # devices do not survive a reboot, which is why this runs at every boot.
    systemd.services.apklayer-binder = lib.mkIf cfg.binder.kernel {
      description = "apklayer binderfs nodes";
      wantedBy = ["multi-user.target"];
      after = ["systemd-modules-load.service"];

      path = with pkgs; [kmod util-linux gawk gnugrep python3 coreutils];

      serviceConfig = {
        Type = "oneshot";
        RemainAfterExit = true;
        ExecStart = "${pkgs.runtimeShell} ${cfg.package}/share/apklayer/tools/host-setup/binder.sh ${lib.escapeShellArgs cfg.binder.instances}";
      };
    };
  };
}
