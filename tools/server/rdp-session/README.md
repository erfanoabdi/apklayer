# RDP always opens the same session

systemd user units for a server (user `ubuntu`) so the RDP client always lands in one headless GNOME Wayland session.

- `apklayer-gnome-headless.service` runs `apklayer-gnome-headless` (gnome-shell `--headless` plus `gnome-remote-desktop-daemon --headless` on a private session bus), `Restart=always`.
- `apklayer-pgr.service` runs `apklayer --instance pgr run <pkg>` into `wayland-0` after it, restarted with the session (`PartOf`).
- RDP user/password are stored once with `grdctl --headless rdp set-credentials USER PASS` (kept in `~/.local/share/gnome-remote-desktop/credentials.ini`); nothing secret is in these files.

Install:
    install -D apklayer-gnome-headless ~/.local/bin/apklayer-gnome-headless
    install -Dm644 *.service -t ~/.config/systemd/user/
    sudo loginctl enable-linger $USER    # user manager runs without a login (survives reboot)
    systemctl --user daemon-reload && systemctl --user enable --now apklayer-gnome-headless apklayer-pgr
Reconnect: RDP to host:3389. Restart PGR only: `systemctl --user restart apklayer-pgr`.
