"""KDE Plasma's compositor (KWin 5.27 and 6) from a test, as sway's swaymsg is for sway: a KWin script is loaded through D-Bus (org.kde.KWin /Scripting),
runs once, and what it prints (P(), a marker with this run's tag) is read back: from the user journal (the session's KWin: its process is kwin_wayland on
Plasma 6, kwin_wayland_wrapper on 5.27) or, for a KWin of its own (kwin_wayland --virtual, own D-Bus; tests/vm/kde-output.py, kde-navbar.py), from its log
file: set LOG (and ENV, the environment of kscreen-doctor there, WAYLAND_DISPLAY and the D-Bus address) then. info() is the state of an app's window,
act() changes it (maximize, fullscreen, tile, minimize, close ...). Needs qdbus6 (or qdbus) and journalctl on the host, a KWin session.
KWin 5.27 has no output objects, maximizeMode, clientGeometry or scale in scripts: the shims below and kscreen-doctor fill them in."""
import json
import os
import re
import shutil
import subprocess
import time
from itertools import count
from pathlib import Path

LOG = None  # a private KWin's log file (its stdout and stderr), None: the session's journal
ENV = None  # environment for kscreen-doctor / qdbus of a private KWin (None: this process's, with Qt on Wayland)
_ids = count(1)
QDBUS = next((t for t in ('qdbus6', 'qdbus-qt6', 'qdbus') if shutil.which(t)), 'qdbus6')
# KWin 6 has windowList()/output/maximizeMode/clientGeometry; 5.27 clientList()/screen (an index)/derived values. Outputs: name and scale come from python
PRELUDE = '''var K5 = !workspace.windowList, TAG = "%s";
function P(s) { print("KWT " + TAG + " " + s); }
function area(w) { return K5 ? workspace.clientArea(KWin.FullScreenArea, w) : w.output.geometry; }
function untile(w) { if (!K5) { w.tile = null; return; } var a = area(w); if (w.frameGeometry.x <= a.x + 1) workspace.slotWindowQuickTileLeft(); else workspace.slotWindowQuickTileRight(); }  // 5.27: the same quick tile again toggles; tile = null leaves the size
function activate(w) { if (K5) workspace.activeClient = w; else workspace.activeWindow = w; }
function maxmode(w) { if (!K5) return w.maximizeMode; var a = workspace.clientArea(KWin.MaximizeArea, w), f = w.frameGeometry;
  return (Math.abs(f.width - a.width) < 2 ? 1 : 0) + (Math.abs(f.height - a.height) < 2 ? 2 : 0); }
'''
FIND = 'var P0 = "%s", ws = (K5 ? workspace.clientList() : workspace.windowList()).filter(function (x) { return x.resourceClass == P0 || x.desktopFileName == P0; }), w = ws[0];'
STATE = '''if (!w) P("none"); else { var cg = w.clientGeometry || w.bufferGeometry, og = area(w); P(JSON.stringify({caption: w.caption, noBorder: w.noBorder, fullScreen: w.fullScreen, minimized: w.minimized,
  maximize: maxmode(w), active: w.active, tiled: !!w.tile, min: [w.minSize.width, w.minSize.height], max: [w.maxSize.width, w.maxSize.height],
  frame: [w.frameGeometry.x, w.frameGeometry.y, w.frameGeometry.width, w.frameGeometry.height],
  client: [cg.x, cg.y, cg.width, cg.height],
  output: K5 ? null : w.output.name, outputGeometry: [og.x, og.y, og.width, og.height],
  scale: K5 ? null : w.output.devicePixelRatio, movable: w.moveable, resizeable: w.resizeable, maximizable: w.maximizable, fullScreenable: w.fullScreenable,
  windows: ws.length})); }'''


def _run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, check=True, **kw).stdout


def _env():
    return ENV if ENV is not None else dict(os.environ, QT_QPA_PLATFORM='wayland')


def _lines(tag):
    """The marked lines of this run, once its end marker is in the log (journald is a moment behind the script: a bounded wait); None while missing."""
    pattern = re.compile(r'KWT %s (.*)' % tag)
    for _ in range(50):
        if LOG: text = Path(LOG).read_text(errors='replace')
        else: text = _run(['journalctl', '--user', '_COMM=kwin_wayland', '_COMM=kwin_wayland_wrapper', '-n', '600', '-o', 'cat'])
        found = [m[1] for m in map(pattern.search, text.splitlines()) if m]
        if found and found[-1] == 'END': return found[:-1]
        time.sleep(0.1)
    return None


def script(js, out_dir):
    """Runs a KWin script (JavaScript, P(text) reports a line); the reported lines."""
    tag = '%d.%d' % (os.getpid(), next(_ids))
    name = 'apklayer-test-' + tag
    path = Path(out_dir) / (name + '.js')
    path.write_text(PRELUDE % tag + 'try { ' + js + ' } finally { P("END"); }')
    sid = _run([QDBUS, 'org.kde.KWin', '/Scripting', 'org.kde.kwin.Scripting.loadScript', str(path), name], env=_env()).strip()
    try:
        # the script's object: /Scripting/Script<id> on Plasma 6, /<id> on 5.27
        for obj in ('/Scripting/Script' + sid, '/' + sid):
            if subprocess.run([QDBUS, 'org.kde.KWin', obj, 'org.kde.kwin.Script.run'], env=_env(), capture_output=True).returncode == 0: break
        else: raise AssertionError('KWin has no script object for id %s' % sid)
    finally: _run([QDBUS, 'org.kde.KWin', '/Scripting', 'org.kde.kwin.Scripting.unloadScript', name], env=_env())
    lines = _lines(tag)
    assert lines is not None, 'the KWin script printed nothing (journal or log not readable?)'
    return lines


OUTPUT = re.compile(r'Output: \d+ (\S+) enabled .*?Geometry: (-?\d+),(-?\d+) (\d+)x(\d+).*?Scale: ([\d.]+)')


def outputs():
    """The enabled outputs by kscreen-doctor -o (its -j aborts on 5.27): name, scale, logical geometry [x, y, w, h]."""
    text = re.sub(r'\x1b\[[0-9;]*m', '', _run(['kscreen-doctor', '-o'], env=_env()))
    return [{'name': m[1], 'scale': float(m[6]), 'geometry': [int(m[i]) for i in range(2, 6)]} for m in map(OUTPUT.search, text.splitlines()) if m]


def info(pkg, out_dir):
    """The state of the window whose app id is pkg (a dict; geometries are [x, y, w, h] in logical pixels), None without one."""
    lines = script('(function () { %s %s })();' % (FIND % pkg, STATE), out_dir)
    if not lines or lines[-1] == 'none': return None
    state = json.loads(lines[-1])
    if state['scale'] is None:  # 5.27: the output (and its scale) at the window's screen geometry
        og = state['outputGeometry']
        out = next((o for o in outputs() if o['geometry'][:2] == og[:2]), None)
        state['output'], state['scale'] = (out['name'], out['scale']) if out else (None, 1)
    return state


def act(pkg, action, out_dir):
    """`action`: JavaScript with the window as w (w.setMaximize(true, true), w.fullScreen = true, workspace.slotWindowQuickTileLeft() ...)."""
    lines = script('(function () { %s if (!w) { P("none"); return; } activate(w); %s })();' % (FIND % pkg, action), out_dir)
    assert 'none' not in lines, 'no window %s in KWin' % pkg


def send_to_output(pkg, name, out_dir):
    """Moves the window to the output called `name` (both KWin versions: matched by its position)."""
    x, y = next(o for o in outputs() if o['name'] == name)['geometry'][:2]
    act(pkg, '''var t = null; if (K5) { for (var i = 0; i < workspace.numScreens; i++) { var g = workspace.clientArea(KWin.FullScreenArea, i, w.desktop); if (g.x == %d && g.y == %d) t = i; } }
      else t = workspace.screens.filter(function (o) { return o.geometry.x == %d && o.geometry.y == %d; })[0];
      workspace.sendClientToScreen(w, t);''' % (x, y, x, y), out_dir)


SIZE = re.compile(r'configure content (\d+)x(\d+) @(\d+)% .*?title bar (\d+) logical = (\d+) px(, maximized|, tiled)?')


def configure(shell_log):
    """The shell's last CONFIGURE of the app (shell.log): (w, h, scale percent, bar logical, state), state '', 'maximized' or 'tiled'; None before one."""
    last = [m for m in SIZE.finditer(Path(shell_log).read_text(errors='replace') if Path(shell_log).exists() else '')][-1:]
    return (*(int(g) for g in last[0].groups()[:4]), (last[0].group(6) or '').strip(', ')) if last else None
