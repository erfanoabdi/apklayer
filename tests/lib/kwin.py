"""KDE Plasma's compositor (KWin 6) from a test, as sway's swaymsg is for sway: a KWin script is loaded through D-Bus (org.kde.KWin /Scripting),
runs once, and what it print()s with the marker KWT is read back from the user journal (kwin_wayland's). info() is the state of an app's
window, act() changes it (maximize, fullscreen, tile, minimize, close ...). Needs qdbus6 and journalctl on the host, a KWin session.
A KWin of its own (kwin_wayland --virtual, own D-Bus; tests/vm/kde-output.py) logs to the journal as well: DBUS_SESSION_BUS_ADDRESS picks it."""
import json
import re
import subprocess
from itertools import count
from pathlib import Path

_ids = count(1)
FIND = 'var P = "%s", ws = workspace.windowList().filter(function (x) { return x.resourceClass == P || x.desktopFileName == P; }), w = ws[0];'
STATE = '''if (!w) print("KWT none"); else print("KWT " + JSON.stringify({caption: w.caption, noBorder: w.noBorder, fullScreen: w.fullScreen, minimized: w.minimized,
  maximize: w.maximizeMode, active: w.active, tiled: !!w.tile, min: [w.minSize.width, w.minSize.height], max: [w.maxSize.width, w.maxSize.height],
  frame: [w.frameGeometry.x, w.frameGeometry.y, w.frameGeometry.width, w.frameGeometry.height],
  client: [w.clientGeometry.x, w.clientGeometry.y, w.clientGeometry.width, w.clientGeometry.height],
  output: w.output.name, outputGeometry: [w.output.geometry.x, w.output.geometry.y, w.output.geometry.width, w.output.geometry.height],
  scale: w.output.devicePixelRatio, movable: w.moveable, resizeable: w.resizeable, maximizable: w.maximizable, fullScreenable: w.fullScreenable,
  windows: ws.length}));'''


def _run(cmd):
    return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout


def script(js, out_dir):
    """Runs a KWin script; the lines it printed with the KWT marker, without it."""
    cursor = re.search(r'-- cursor: (\S+)', _run(['journalctl', '--user', '_COMM=kwin_wayland', '-n0', '--show-cursor', '-o', 'cat'])).group(1)
    name = 'apklayer-test-%d' % next(_ids)
    path = Path(out_dir) / (name + '.js')
    path.write_text(js)
    sid = _run(['qdbus6', 'org.kde.KWin', '/Scripting', 'org.kde.kwin.Scripting.loadScript', str(path), name]).strip()
    try: _run(['qdbus6', 'org.kde.KWin', '/Scripting/Script' + sid, 'org.kde.kwin.Script.run'])
    finally: _run(['qdbus6', 'org.kde.KWin', '/Scripting', 'org.kde.kwin.Scripting.unloadScript', name])
    log = _run(['journalctl', '--user', '_COMM=kwin_wayland', '--after-cursor=' + cursor, '-o', 'cat'])
    return [l.split('KWT ', 1)[1] for l in log.splitlines() if 'KWT ' in l]


def info(pkg, out_dir):
    """The state of the window whose app id is pkg (a dict; geometries are [x, y, w, h] in logical pixels), None without one."""
    lines = script('(function () { %s %s })();' % (FIND % pkg, STATE), out_dir)
    return json.loads(lines[-1]) if lines and lines[-1] != 'none' else None


def act(pkg, action, out_dir):
    """`action`: JavaScript with the window as w (w.setMaximize(true, true), w.fullScreen = true, workspace.slotWindowQuickTileLeft() ...)."""
    lines = script('(function () { %s if (!w) { print("KWT none"); return; } workspace.activeWindow = w; %s })();' % (FIND % pkg, action), out_dir)
    assert 'none' not in lines, 'no window %s in KWin' % pkg


SIZE = re.compile(r'configure content (\d+)x(\d+) @(\d+)% .*?title bar (\d+) logical = (\d+) px(, maximized|, tiled)?')


def configure(shell_log):
    """The shell's last CONFIGURE of the app (shell.log): (w, h, scale percent, bar logical, state), state '', 'maximized' or 'tiled'; None before one."""
    last = [m for m in SIZE.finditer(Path(shell_log).read_text(errors='replace') if Path(shell_log).exists() else '')][-1:]
    return (*(int(g) for g in last[0].groups()[:4]), (last[0].group(6) or '').strip(', ')) if last else None
