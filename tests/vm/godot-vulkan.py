#!/usr/bin/env python3
"""tests/run godot: the Godot 4 Android editor (installed in the instance: godotengine/godot-builds' android_editor.apk) runs a one-scene project
with the Mobile renderer (Vulkan) in its GodotGame process; Godot must report its Vulkan device and the screenshot must show the scene's magenta.
Needs Vulkan presentation (a host device that imports dma-bufs and shares sync fds)."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import pixels  # noqa: E402
PKG = 'org.godotengine.editor.v4'
env = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_LOGCAT='1', APKLAYER_JOPTS='-Dapklayer.runms=40000')
project = instance.OUT / 'godot-project'
project.mkdir(parents=True, exist_ok=True)
(project / 'project.godot').write_text('config_version=5\n\n[application]\nconfig/name="apklayer-vk"\nrun/main_scene="res://main.tscn"\n'
                                       'config/features=PackedStringArray("4.3", "Mobile")\n\n[rendering]\nrenderer/rendering_method="mobile"\n')
(project / 'main.tscn').write_text('[gd_scene format=3]\n\n[node name="Main" type="ColorRect"]\nanchors_preset = 15\nanchor_right = 1.0\n'
                                   'anchor_bottom = 1.0\ncolor = Color(1, 0, 1, 1)\n')
intent = Path('/tmp/apklayer-godot-intent-%s.properties' % instance.INSTANCE)
# GodotEditor hands a project path without --editor to its GodotGame activity (process :GodotGame)
intent.write_text('component=org.godotengine.editor.GodotEditor\nextra[].command_line_params=--path|%s\n' % project)
env['APKLAYER_INTENT_FILE'] = str(intent)


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    instance.kill('[b]in/apklayer hub')


def logs():
    return sorted(Path(instance.LOGDIR).glob('app-%s*.log' % PKG))


def game_lines():
    """Godot's log lines of this run: the editor's process and its GodotGame process (app-<package>*.log, past what earlier runs wrote)."""
    lines = []
    for log in logs():
        with open(log, 'rb') as f:
            f.seek(seen.get(log, 0) if log.stat().st_size >= seen.get(log, 0) else 0)
            lines += f.read().decode(errors='replace').splitlines()
    return lines


seen = {log: log.stat().st_size for log in logs()}


stop()
app = None
try:
    app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + 60
    device = None
    with selectors.DefaultSelector() as events, open(instance.OUT / 'godot-run.out', 'wb') as log:
        events.register(app.stdout, selectors.EVENT_READ)
        while device is None:
            assert time.monotonic() < end, 'Godot reported no Vulkan device: ' + '\n'.join((game_lines() or ['(no game log)'])[-20:])
            if events.select(2):
                chunk = os.read(app.stdout.fileno(), 65536)
                log.write(chunk); log.flush()
                if not chunk: break
            lines = game_lines()
            for line in lines:
                assert 'Missing requirements for vulkan' not in line and 'FATAL EXCEPTION' not in line, line
                if 'Vulkan' in line and 'Using Device' in line: device = line.strip()
                if 'OpenGL API' in line: raise AssertionError('Godot fell back to OpenGL: ' + line.strip())
    assert device, 'the app ended before Godot reported a Vulkan device: ' + '\n'.join(game_lines()[-20:])
    print('godot: ' + device, flush=True)
    # the scene draws on its first frames; a frame counter would need a script: the screenshot is taken once the device is up and frames flow
    shot = None
    for _ in range(10):
        shot = instance.capture('godot', PKG)
        magenta = pixels.count(shot, lambda r, g, b: r > 200 and g < 60 and b > 200) / (lambda im: im.width * im.height)(pixels.rgb(shot))
        if magenta > 0.5: break
        with selectors.DefaultSelector() as events:  # the next screenshot after a second of the app's output (or of none)
            events.register(app.stdout, selectors.EVENT_READ)
            if events.select(1): os.read(app.stdout.fileno(), 65536)
    print('IMAGE %s: %.0f%% of the window magenta' % (shot, magenta * 100), flush=True)
    assert magenta > 0.5, 'the Vulkan scene is not on screen (%.0f%% magenta)' % (magenta * 100)
    print('PASS Godot 4 drew its Mobile (Vulkan) renderer scene', flush=True)
finally:
    if app is not None:
        try: os.killpg(app.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        app.wait(); app.stdout.close()
    stop(); intent.unlink(missing_ok=True)
    instance.kill('[a]pklayer.host.Main')
