#!/usr/bin/env python3
"""nested-gnome.py <scale> <cmd...> (VM): a window/scale check without touching the desktop session: runs cmd against a headless gnome-shell (its own D-Bus session, one 1920x1080 virtual monitor) whose monitor
is set to <scale> (fractional with scale-monitor-framebuffer). cmd gets WAYLAND_DISPLAY of the nested compositor. Everything started ends."""
import os, re, subprocess, sys, signal
scale, cmd = float(sys.argv[1]), sys.argv[2:]
if os.environ.get('NESTED_INNER') != '1':  # re-run inside a private D-Bus session
    os.environ['NESTED_INNER'] = '1'
    os.execvp('dbus-run-session', ['dbus-run-session', '--', sys.executable, '-u'] + sys.argv)
rt = os.environ['XDG_RUNTIME_DIR']
log = open(os.path.join(rt, 'nested-shell.log'), 'w')
shell = subprocess.Popen(['gnome-shell', '--headless', '--wayland', '--no-x11', '--virtual-monitor', '1920x1080', '--wayland-display', 'geom-nested'],
                         stdout=log, stderr=log, start_new_session=True)
rc = 1
try:
    subprocess.run(['gdbus', 'wait', '--session', '--timeout', '20', 'org.gnome.Mutter.DisplayConfig'], check=True)
    call = ['gdbus', 'call', '--session', '--dest', 'org.gnome.Mutter.DisplayConfig', '--object-path', '/org/gnome/Mutter/DisplayConfig',
            '--method']
    state = subprocess.run(call + ['org.gnome.Mutter.DisplayConfig.GetCurrentState'], capture_output=True, text=True, check=True).stdout
    serial = re.match(r'\(uint32 (\d+)', state).group(1)
    connector = re.search(r"\(\('([^']+)'", state).group(1)
    mode = re.search(r"\('(\d+x\d+@[\d.]+)'", state).group(1)
    scales = [float(x) for x in re.search(r"'%s', \d+, \d+, [\d.]+, [\d.]+, \[([^\]]*)\]" % re.escape(mode), state).group(1).split(', ')]
    pick = min(scales, key=lambda s: abs(s - scale))
    print('nested: %s %s, scales %s -> %.4f' % (connector, mode, scales, pick), flush=True)
    subprocess.run(call + ['org.gnome.Mutter.DisplayConfig.ApplyMonitorsConfig', serial, '1',
                           "[(0, 0, %r, 0, true, [('%s', '%s', @a{sv} {})])]" % (pick, connector, mode), '@a{sv} {}'], check=True,
                   stdout=subprocess.DEVNULL)
    rc = subprocess.run(cmd, env=dict(os.environ, WAYLAND_DISPLAY=os.path.join(rt, 'geom-nested'))).returncode
finally:
    os.killpg(shell.pid, signal.SIGTERM)
    shell.wait()
sys.exit(rc)
