#!/usr/bin/env python3
"""Files an app declares in its manifest open with it from the desktop (docs/FILE-ASSOCIATIONS.md). hello.openfile (VIEW of .ovpn, application/x-openvpn-profile,
image/*; a bare */* and an https-only filter) is installed into a data home of the test's own (nothing of the VM user's menu changes): its .desktop entry
has MimeType= and `open <package> %f`, the extension without a system type gets a shared-mime-info package, `gio open sample.ovpn` starts the app with
an ACTION_VIEW (a content Uri, the type, the display name, the content), a second file reaches the running app in onNewIntent, and an uninstall takes
the entry and the package away. Last the .apk handler: the data/ files pass desktop-file-validate and the mime package defines .apks/.xapk/.apkm."""
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
from instance import ENV, LOGDIR, OUT, wait_file  # noqa: E402

PKG = 'hello.openfile'
data = OUT / 'open-files-data'
shutil.rmtree(data, ignore_errors=True)
data.mkdir(parents=True)
env = dict(ENV, XDG_DATA_HOME=str(data), APKLAYER_JOPTS='-Dapklayer.runms=90000', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400')
entry = data / ('applications/apklayer-%s.desktop' % PKG)
package_xml = data / ('mime/packages/apklayer-%s.xml' % PKG)
app_log = Path(LOGDIR) / ('app-%s.log' % PKG)
failures = []


def run(*argv, **kw):
    return subprocess.run(argv, env=env, capture_output=True, text=True, **kw)


def gio_open(path):
    """`gio open`: the handler (and the app it starts) keeps the pipes, so they are not captured; returns gio's exit status."""
    return subprocess.run(['gio', 'open', str(path)], env=env, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode


def check(ok, what):
    print(('ok   ' if ok else 'FAIL ') + what, flush=True)
    if not ok: failures.append(what)


def seen(needle, seconds):
    """The app log's last line with needle (the log is waited on with inotify), None on a timeout."""
    try: text = wait_file(app_log, needle, seconds)
    except AssertionError as late: print(late); return None
    return [l for l in text.splitlines() if needle in l][-1]


def finish():
    instance.stop()
    shutil.rmtree(data, ignore_errors=True)
    for name in ('sample.ovpn', 'second.ovpn'):
        (OUT / name).unlink(missing_ok=True)
    if failures:
        print('FAILED: ' + '; '.join(failures))
        sys.exit(1)
    print('open-files: all passed')
    sys.exit(0)


try:
    out = run('bin/apklayer', 'install', '.tests/apks/hello-openfile.apk')
    check('installed ' + PKG in out.stdout, 'install ' + PKG + ' ' + (out.stderr or out.stdout)[-200:])
    text = entry.read_text() if entry.exists() else ''
    mimes = re.search(r'^MimeType=(.*)$', text, re.M)
    types = (mimes.group(1).split(';') if mimes else [])
    print(text)
    check(entry.exists() and run('desktop-file-validate', str(entry)).returncode == 0, 'the entry validates')
    check('application/x-openvpn-profile' in types, 'MimeType has the declared type')
    check('image/png' in types and 'image/jpeg' in types, 'MimeType has the image/* family as concrete types')
    check('*/*' not in types and 'application/x-web-only' not in types and not any('*' in t for t in types), 'no */*, no wildcard, no https-only type')
    check(all(t.split('/')[0] in ('application', 'audio', 'font', 'image', 'inode', 'message', 'model', 'multipart', 'text', 'video') for t in types if t), 'every MimeType has a registered top-level (no vnd.android.document/*)')
    check(re.search(r'^Exec=.* open %s %%f$' % re.escape(PKG), text, re.M) is not None, 'Exec opens the file: `open <package> %f`')
    xml = package_xml.read_text() if package_xml.exists() else ''
    print(xml)
    check('*.ovpn' in xml or run('xdg-mime', 'query', 'filetype', '/etc/hostname').returncode == 0, 'the .ovpn extension is defined by a mime package (or the system knows it)')
    sample = OUT / 'sample.ovpn'
    sample.write_text('client\nremote vpn.example.org 1194\n')
    kind = run('xdg-mime', 'query', 'filetype', str(sample)).stdout.strip()
    check(kind in types, 'xdg-mime knows sample.ovpn as %r, one of the entry\'s types' % kind)
    check(entry.name in run('gio', 'mime', kind).stdout, 'gio mime %s lists the entry' % kind)
    check(run('gio', 'mime', kind).stdout.count('apklayer-') >= 1, 'gio lists apklayer entries for the type')
    # opening: the entry is the only handler of the type in this data home
    check(gio_open(sample) == 0, 'gio open sample.ovpn')
    line = seen('[hello-openfile] onCreate', 120)
    print(line)
    check(line is not None and 'action=android.intent.action.VIEW' in line, 'the app got ACTION_VIEW in onCreate')
    check(line is not None and 'uri=content://apklayer.opened/' in line, 'the data is a content Uri')
    check(line is not None and 'type=' + kind in line and 'resolvedType=' + kind in line, 'the intent type and the provider\'s type are %s' % kind)
    check(line is not None and 'name=sample.ovpn' in line and 'size=%d' % sample.stat().st_size in line, 'OpenableColumns: the name and the size')
    check(line is not None and 'grant=true' in line and 'content=client|remote vpn.example.org 1194|' in line, 'read grant flag and the file content')
    # the running app: another file arrives in onNewIntent
    second = OUT / 'second.ovpn'
    second.write_text('client\nremote two.example.org 443\n')
    gio_open(second)
    line = seen('[hello-openfile] onNewIntent action=android.intent.action.VIEW', 60)
    print(line)
    check(line is not None and 'name=second.ovpn' in line and 'remote two.example.org 443' in line, 'a second file reaches the running app in onNewIntent')
    instance.stop()
    # under the per-app sandbox (`apklayer open`: the copy cannot go into the app's own, uid-owned data): the same Uri, type, name and content in the app
    if not any(l.startswith(os.environ.get('USER', '?') + ':') for l in Path('/etc/subuid').read_text().splitlines()):
        print('skipped: no sandbox here (tools/host-setup/sandbox.sh)')
    else:
        env['APKLAYER_SANDBOX'] = '1'
        sbx = OUT / 'sandboxed.ovpn'
        sbx.write_text('client\nremote sandbox.example.org 1195\n')
        out = run('bin/apklayer', 'open', PKG, str(sbx))
        print(out.stdout[-300:], out.stderr[-300:])
        line = seen('name=sandboxed.ovpn', 120)
        print(line)
        check(out.returncode == 0, 'apklayer open under the sandbox: ' + (out.stderr or out.stdout)[-200:])
        check(line is not None and 'uri=content://apklayer.opened/' in line and 'remote sandbox.example.org 1195' in line and 'size=%d' % sbx.stat().st_size in line,
              'the sandboxed app reads the opened file through its content Uri')
        instance.stop()
        del env['APKLAYER_SANDBOX']
        sbx.unlink(missing_ok=True)
    # removal: uninstall and update take the associations away
    run('bin/apklayer', 'uninstall', PKG)
    check(not entry.exists() and not package_xml.exists(), 'uninstall removes the entry and the mime package')
    # a real app that opens files: OpenVPN for Android (a local copy of the corpus, when the VM has it)
    vpn = Path.home() / 'corpus-openvpn.apk'
    if vpn.exists():
        out = run('bin/apklayer', 'install', str(vpn))
        vpn_entry = data / 'applications/apklayer-de.blinkt.openvpn.desktop'
        vpn_text = vpn_entry.read_text() if vpn_entry.exists() else ''
        print(re.findall(r'^(?:Exec|MimeType)=.*$', vpn_text, re.M))
        check('installed de.blinkt.openvpn' in out.stdout, 'install OpenVPN for Android ' + (out.stderr or out.stdout)[-150:])
        check('application/x-openvpn-profile;' in vpn_text, 'OpenVPN\'s entry carries its profile type (MimeType=)')
        check(re.search(r'^Exec=.* open de\.blinkt\.openvpn %f$', vpn_text, re.M) is not None, 'OpenVPN\'s entry opens the file')
        run('bin/apklayer', 'uninstall', 'de.blinkt.openvpn')
    # .apk, .apks, .xapk, .apkm: the manager's entry (data/apklayer-manager.desktop) takes the files, %F, into its install dialog
    manager = Path('manager/b/apklayer-manager')
    if not manager.exists():
        subprocess.run('meson setup manager/b manager >/dev/null 2>&1; ninja -C manager/b apklayer-manager >/dev/null 2>&1', shell=True)
    if not manager.exists():
        print('skipped: no manager build (manager/fetch-slint.sh, manager/meson.build)')
    else:
        (data / 'apklayer').mkdir(exist_ok=True)
        (data / 'apklayer/setup-done').write_text('')  # no first-run checklist over the install dialog
        check(run('sh', 'manager/install-desktop.sh', str(manager)).returncode == 0, 'manager/install-desktop.sh: the entry and the mime package')
        manager_entry = data / 'applications/apklayer-manager.desktop'
        check(run('desktop-file-validate', str(manager_entry)).returncode == 0, 'the installed manager entry validates')
        for name, kind in (('a.apk', 'application/vnd.android.package-archive'), ('a.apks', 'application/x-android-apks'), ('a.xapk', 'application/x-android-xapk'), ('a.apkm', 'application/x-android-apkm')):
            (OUT / name).write_bytes(b'PK\x05\x06' + bytes(18))
            got = run('xdg-mime', 'query', 'filetype', str(OUT / name)).stdout.strip()
            check(got == kind, '%s is %s (%s)' % (name, kind, got))
            check('apklayer-manager.desktop' in run('gio', 'mime', kind).stdout, 'gio mime %s lists the manager' % kind)
        # one process takes every file a file manager selects (%F; `gio open` itself launches once per file)
        check(re.search(r'^Exec=.* %F$', manager_entry.read_text(), re.M) is not None, 'the manager entry takes a list of files: Exec=... %F')
        # the install dialog's preview before Install: the app's name, version and permissions read from the manifest (nothing converted yet)
        out = subprocess.run([str(manager), '--screenshot', str(OUT / 'open-apk-preview.png'), '--size', '400x840', '.tests/apks/hello-openfile.apk',
                              '--wait-preview', '--expect', 'App openfile', '--expect', 'Permissions'], env=env, capture_output=True, text=True, timeout=300)
        print(out.stdout[-300:], out.stderr[-300:])
        check(out.returncode == 0 and not entry.exists(),'the install dialog previews the app (name, permissions) before Install')
        # the install dialog: the file opened with the manager is installed on Install, and the dialog of the new app offers Open
        out = subprocess.run([str(manager), '--screenshot', str(OUT / 'open-apk.png'), '--size', '400x840', '.tests/apks/hello-openfile.apk',
                              '--click', 'Install', '--wait-refresh', '1', '--expect', 'Open'], env=env, capture_output=True, text=True, timeout=300)
        print(out.stdout[-300:], out.stderr[-300:])
        check(out.returncode == 0, 'the manager opened with an .apk: Install, then the installed dialog with Open')
        check(entry.exists(), 'the app installed through the manager has its entry')
        run('bin/apklayer', 'uninstall', PKG)
    # the .apk handler of the packages: data/ files
    for f in ('data/apklayer-manager.desktop', 'data/apklayer-share.desktop'):
        check(run('desktop-file-validate', f).returncode == 0, f + ' validates')
    mimes = Path('data/apklayer-mime.xml').read_text()
    check(all(g in mimes for g in ('*.apks', '*.xapk', '*.apkm')), 'data/apklayer-mime.xml defines .apks, .xapk and .apkm')
    check('application/vnd.android.package-archive' in Path('data/apklayer-manager.desktop').read_text(), 'the manager entry handles .apk')
finally:
    finish()
