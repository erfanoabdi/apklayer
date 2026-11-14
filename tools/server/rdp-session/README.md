# RDP always opens the same session

systemd user units for a server (user `ubuntu`) so every RDP connect lands in one headless GNOME Wayland session and the windows stay open between connections.

- `apklayer-gnome-shell.service`: `dbus-run-session` + `apklayer-gnome-shell` (gnome-shell `--headless`, a private session bus whose address goes to `$XDG_RUNTIME_DIR/apklayer-session-bus`, no idle lock or suspend). `Restart=always`. Only this unit owns the windows.
- `apklayer-grd.service`: `apklayer-grd` (`gnome-remote-desktop-daemon --headless` on that bus). Separate unit: restarting or crashing it never restarts the shell. If the shell restarts, its bus dies, the daemon exits and systemd restarts it on the new bus.
- `apklayer-pgr.service`: optional (not enabled by default), `apklayer --instance pgr run <pkg>` after the shell; it never restarts the session.
- RDP user/password are stored once with `grdctl --headless rdp set-credentials USER PASS` (kept in `~/.local/share/gnome-remote-desktop/credentials.ini`); nothing secret is in these files.

Each RDP connection adds a virtual monitor `Meta-1` ("Virtual remote monitor") beside the always-present `Meta-0` (1280x720, not streamed); disconnecting removes it. RDP streams ONLY `Meta-1`, so windows on `Meta-0` look like an empty desktop: `monitors.xml` (install as `~/.config/monitors.xml`; Meta-1 alone, primary, 1368x852, Meta-0 disabled) makes Meta-1 the only display. Applied live with `org.gnome.Mutter.DisplayConfig.ApplyMonitorsConfig` (method 2); it matches only that Meta-1 mode, otherwise redo it after the client connects (same call, current serial). gnome-shell keeps running through that. The old single unit (shell and grd in one) was restarted as a whole by hand-restarts of that unit, which closed every window.

Install:

    install -D apklayer-gnome-shell apklayer-grd -t ~/.local/bin/
    install -Dm644 *.service -t ~/.config/systemd/user/
    sudo loginctl enable-linger $USER    # user manager runs without a login (survives reboot)
    systemctl --user daemon-reload && systemctl --user enable --now apklayer-gnome-shell apklayer-grd

Never restart `apklayer-gnome-shell` while windows are open (that is what closes them). Reconnect: RDP to host:3389. Restart RDP only: `systemctl --user restart apklayer-grd`.
