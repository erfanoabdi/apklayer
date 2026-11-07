#!/usr/bin/env python3
"""glbench's window modes through apklayer: starts hello.glbench (tests/apps/hello-glbench, installed in the instance) in an 800x600 window,
prints its result lines, and stops the instance once it prints "glbench: done" (or after a 120 s budget, or 20 s without output).
Run in the repo copy on the VM with the instance's environment (APKLAYER_INSTANCE, XDG_RUNTIME_DIR, WAYLAND_DISPLAY...):
  python3 tests/bench/glbench/run-android.py [modes] [scenes] [seconds]     modes: window,window-novsync; scenes: clear,fill,draws
Extra JVM options (-Dapklayer.perf=true...) come from APKLAYER_JOPTS; GLBENCH_DUMP=<prefix> writes SurfaceFlinger's dumpsys at each mode's\nstart and at the end to <prefix>.N (GLBENCH_DUMP_ARGS: its arguments, e.g. --frametimeline -all); GLBENCH_SHOT=<prefix>: a screenshot after each scene, <prefix>.<mode>.<scene>.png."""
import os
import selectors
import subprocess
import sys
import tempfile
import time

modes, scenes, seconds = (sys.argv[1:] + ['window,window-novsync', 'clear,fill,draws', '2'][len(sys.argv) - 1:])[:3]
intent = tempfile.NamedTemporaryFile('w', suffix='.properties', delete=False)
intent.write('component=hello.glbench.Main\nextra.modes=%s\nextra.scenes=%s\nextra.seconds=%s\n' % (modes, scenes, seconds))
intent.close()
env = dict(os.environ, APKLAYER_INTENT_FILE=intent.name, APKLAYER_DISPLAY_WIDTH='800', APKLAYER_DISPLAY_HEIGHT='600')
env['APKLAYER_JOPTS'] = (env.get('APKLAYER_JOPTS', '') + ' -Dapklayer.runms=120000').strip()
app = subprocess.Popen([os.environ.get('APKLAYER_BIN', 'bin/apklayer'), 'run', 'hello.glbench'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
end = time.monotonic() + 120
try:
    with selectors.DefaultSelector() as sel:
        sel.register(app.stdout, selectors.EVENT_READ)
        pending = b''
        dumps = 0
        while time.monotonic() < end:
            if not sel.select(20):
                print('run-android: no output for 20 s', flush=True)
                break
            chunk = os.read(app.stdout.fileno(), 65536)
            if not chunk:
                break
            lines = (pending + chunk).split(b'\n')
            pending = lines.pop()
            text = [l.decode(errors='replace') for l in lines]
            for line in text:
                if line.startswith('glbench') or 'FATAL' in line or 'perf' in line:
                    print(line, flush=True)
                if os.environ.get('GLBENCH_DUMP') and ('GL_RENDERER' in line or 'glbench: done' in line):  # SurfaceFlinger's state at a mode's start and at the end
                    with open('%s.%d' % (os.environ['GLBENCH_DUMP'], dumps), 'wb') as out:
                        subprocess.run([os.environ.get('APKLAYER_BIN', 'bin/apklayer'), 'exec', 'libapklayer_servicetool.so', 'dump', 'SurfaceFlinger'] + os.environ.get('GLBENCH_DUMP_ARGS', '').split(), env=env, stdout=out, stderr=subprocess.STDOUT)
                    dumps += 1
                if os.environ.get('GLBENCH_SHOT') and line.startswith('glbench: android') and 'fps=' in line:  # the window as SurfaceFlinger shows it after each scene
                    shot = '%s.%s.png' % (os.environ['GLBENCH_SHOT'], '.'.join(line.split()[2:4]))
                    subprocess.run([os.environ.get('APKLAYER_BIN', 'bin/apklayer'), 'screenshot', 'hello.glbench', shot], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
            if any('glbench: done' in l for l in text):
                break
finally:
    # this instance's service processes (pids for the per-process maps of sf-trace.bt): APKLAYER_INSTANCE in their environment
    me = os.environ.get('APKLAYER_INSTANCE', 'default')
    for pid in filter(str.isdigit, os.listdir('/proc')):
        try:
            environ = open('/proc/%s/environ' % pid, 'rb').read().split(b'\0')
            cmd = open('/proc/%s/cmdline' % pid, 'rb').read().split(b'\0')
        except OSError:
            continue
        if ('APKLAYER_INSTANCE=' + me).encode() in environ and any(b'libapklayer_' in c for c in cmd):
            print('pid %s %s' % (pid, next(c.decode() for c in cmd if b'libapklayer_' in c)), flush=True)
    subprocess.run([os.environ.get('APKLAYER_BIN', 'bin/apklayer'), 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if os.path.exists(intent.name): os.unlink(intent.name)  # the CLI may have taken it
    if app.poll() is None:
        app.terminate()
    app.wait()
