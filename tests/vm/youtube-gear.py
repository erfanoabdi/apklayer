#!/usr/bin/env python3
"""Does a tap on YouTube's player gear reach the view? Own instance (APKLY_YT_INSTANCE, default yt8): YouTube opens a watch URL (an intent file,
as `apklayer open` writes it), the host script (-Dapklayer.script) dumps every window's view tree (`views`), taps the player to show its controls,
dumps again, taps the gear, dumps again. The app log keeps the dumps and the touch path (APKLAYER_DEBUG_INPUT).
Usage: youtube-gear.py [--video ID] [--gear X,Y] [--player X,Y] [--t0 MS] [--size WxH] [--remove]
Without --gear/--player only the first dump is taken: find the gear in it, run again with its coordinates. Ends with `apklayer --instance <id> stop`;
--remove also deletes the instance (never use an instance id that holds something).
A fresh instance first shows YouTube's consent WebView (a modal window over everything; taps and touch steps do not reach its page, keys do):
  --pre '15000:key:61+16000:key:61+17000:key:61+18000:key:66' (3x Tab, Enter = "Accept all"); later runs of the instance skip it.
The `hit:X,Y` step (ScriptRunner) lists every view under the point; --native needs a tree whose host-classes.jar has it. Window px: 502x894 here.
Result (2026-11-25): player tap shows the controls, the gear is player_overflow_button 48x48@454,2 (clickable, onClick), its tap opens a BottomSheetDialog
whose RecyclerView rows are Litho views of height 0 (sheet 84 px high): the input side works."""
import argparse, os, subprocess, tempfile, time
from pathlib import Path

ap = argparse.ArgumentParser()
ap.add_argument('--video', default='jNQXAC9IVRw')
ap.add_argument('--gear', default='')
ap.add_argument('--player', default='')
ap.add_argument('--t0', type=int, default=30000, help='ms after the start of the first dump (the video must play)')
ap.add_argument('--size', default='1080x1920')
ap.add_argument('--pkg', default='com.google.android.youtube')
ap.add_argument('--pre', default='', help='extra steps before the first dump, MS:STEP+MS:STEP (e.g. the consent page: scroll, tap Accept)')
ap.add_argument('--native', default='', help='an APKLAYER_NATIVE tree whose host-classes.jar has the hit: step (a tap shows the views under it first)')
ap.add_argument('--remove', action='store_true')
a = ap.parse_args()
inst = os.environ.get('APKLY_YT_INSTANCE', 'yt8')
w, h = a.size.split('x')
steps = [(int(x.split(':', 1)[0]), x.split(':', 1)[1]) for x in a.pre.split('+') if x] + [(a.t0, 'views')]
if a.player:  # the tap shows the controls for ~3 s: what is under the gear while they are up, then the gear tap, then what changed
    steps += [(a.t0 + 1000, 'hit:' + a.player), (a.t0 + 1500, 'tap:' + a.player), (a.t0 + 2300, 'hit:' + a.gear), (a.t0 + 2500, 'views')]
    if a.gear: steps += [(a.t0 + 2700, 'tap:' + a.gear), (a.t0 + 3100, 'views'), (a.t0 + 3600, 'views'), (a.t0 + 5500, 'views')]
out = Path(tempfile.gettempdir()) / ('apklayer-yt-gear-' + inst)
out.mkdir(exist_ok=True)
for old in out.glob('shot*.png'): old.unlink()
intent = out / 'watch.properties'
intent.write_text('action=android.intent.action.VIEW\ndata=https://www.youtube.com/watch?v=%s\n' % a.video)
env = dict(os.environ, APKLAYER_INSTANCE=inst, APKLAYER_DEBUG_INPUT='1', APKLAYER_DISPLAY_WIDTH=w, APKLAYER_DISPLAY_HEIGHT=h,
           APKLAYER_INTENT_FILE=str(intent), **({'APKLAYER_NATIVE': a.native} if a.native else {}),
           APKLAYER_JOPTS='-Dapklayer.runms=%d -Dapklayer.script=%s' % (a.t0 + 12000, '+'.join('%d:%s' % s for s in steps)))
cli = ['apklayer', '--instance', inst]
state = Path(os.environ.get('XDG_STATE_HOME', Path.home() / '.local/state')) / 'apklayer' / inst
try:
    subprocess.run(cli + ['instance', 'create', inst], env=env, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    run = subprocess.Popen(cli + ['run', a.pkg], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    t = time.time()
    for n, (ms, step) in enumerate(steps):  # a screenshot a second after each step (the window as SurfaceFlinger composes it)
        time.sleep(max(0, t + ms / 1000 + 1 - time.time()))
        subprocess.run(cli + ['screenshot', a.pkg, str(out / ('shot%d-%s.png' % (n, step.split(':')[0])))], env=env, check=False, stderr=subprocess.DEVNULL)
    run.wait(timeout=60)
    log = state / ('app-%s.log' % a.pkg)
    (out / 'app.log').write_text(log.read_text(errors='replace') if log.exists() else '')
    print('log %s, screenshot %s' % (out / 'app.log', out / 'end.png'))
finally:
    subprocess.run(cli + ['stop'], env=env, check=False)
    if a.remove: subprocess.run(cli + ['instance', 'remove', inst], env=env, check=False)
