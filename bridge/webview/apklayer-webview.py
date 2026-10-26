#!/usr/bin/env python3
"""apklayer-webview: the WebView engine for one android.webkit.WebView (PLAN P5.1).

Runs WebKitGTK offscreen (stopgap for WPE WebKit, D11) in its own process, because the app JVM
may be a different architecture than the system WebKit. Commands arrive on stdin, one per line:
  size W H | load URL | html BASE64 [BASEURL] | eval ID BASE64(JS) | click X Y | scroll DX DY
Events go to stdout: "loaded URL", "title TEXT", "eval ID BASE64(JSON)". Frames are written to
$1 (<dir>/<id>.rgba, header {w,h,seq} little-endian + top-down RGBA), the format of the GL path.
"""
import base64, os, struct, sys, time
os.environ.setdefault("WEBKIT_DISABLE_COMPOSITING_MODE", "1")
import gi
gi.require_version("Gtk", "3.0")
gi.require_version("WebKit2", "4.1")
from gi.repository import GLib, Gtk, WebKit2

out_path, data_dir = sys.argv[1], sys.argv[2]
win = Gtk.OffscreenWindow()
win.set_default_size(480, 800)
ctx = WebKit2.WebContext.new_with_website_data_manager(
    WebKit2.WebsiteDataManager(base_data_directory=data_dir, base_cache_directory=data_dir + "/cache"))
view = WebKit2.WebView.new_with_context(ctx)
win.add(view)
win.show_all()
seq = 0
dirty_until = time.time() + 3  # frames are refreshed for a few seconds after anything happens


def say(*parts):
    print(*parts, flush=True)


def write_frame():
    global seq
    pix = win.get_pixbuf()
    if pix is None:
        return True
    seq += 1
    data = pix.add_alpha(False, 0, 0, 0).get_pixels() if not pix.get_has_alpha() else pix.get_pixels()
    w, h, stride = pix.get_width(), pix.get_height(), pix.get_rowstride()
    rows = b"".join(data[y * stride:y * stride + w * 4] for y in range(h))
    tmp = out_path + ".part"
    with open(tmp, "wb") as f:
        f.write(struct.pack("<III", w, h, seq) + rows)
    os.replace(tmp, out_path)
    return True


def tick():
    if time.time() < dirty_until:
        write_frame()
    return True


def touch(seconds=3):
    global dirty_until
    dirty_until = time.time() + seconds


def on_load(v, event):
    touch()
    if event == WebKit2.LoadEvent.FINISHED:
        say("loaded", v.get_uri())


view.connect("load-changed", on_load)
view.connect("notify::title", lambda v, _: say("title", base64.b64encode((v.get_title() or "").encode()).decode()))


def eval_done(v, res, ident):
    try:
        val = v.run_javascript_finish(res).get_js_value().to_json(0)
    except Exception as e:  # script error: Android's callback gets "null"
        val = "null"
    say("eval", ident, base64.b64encode((val or "null").encode()).decode())


def command(line):
    touch()
    parts = line.split(" ")
    c = parts[0]
    if c == "size":
        win.set_default_size(int(parts[1]), int(parts[2]))
        win.resize(int(parts[1]), int(parts[2]))
    elif c == "load":
        view.load_uri(parts[1])
    elif c == "html":
        view.load_html(base64.b64decode(parts[1]).decode(), parts[2] if len(parts) > 2 else None)
    elif c == "eval":
        view.run_javascript(base64.b64decode(parts[2]).decode(), None, eval_done, parts[1])
    elif c == "scroll":
        view.run_javascript("window.scrollBy(%s,%s)" % (parts[1], parts[2]), None, None, None)
    elif c == "click":
        view.run_javascript("(e=>e&&e.click())(document.elementFromPoint(%s,%s))" % (parts[1], parts[2]), None, None, None)


pending = b""


def on_stdin(fd, cond):
    global pending
    chunk = os.read(fd, 65536)  # raw reads: every complete line in the chunk is a command
    if not chunk:
        Gtk.main_quit()
        return False
    pending += chunk
    *lines, pending = pending.split(b"\n")
    for line in lines:
        command(line.decode())
    return True


GLib.io_add_watch(sys.stdin.fileno(), GLib.IO_IN | GLib.IO_HUP, on_stdin)
GLib.timeout_add(100, tick)
Gtk.main()
