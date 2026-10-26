#!/usr/bin/env python3
"""apklayer-webview: the WebView engine for one android.webkit.WebView (PLAN P5.1).

Runs WebKitGTK offscreen (stopgap for WPE WebKit, D11) in its own process, because the app JVM
may be a different architecture than the system WebKit. Commands arrive on stdin, one per line:
  size W H | load URL | html BASE64 [BASEURL] | eval ID BASE64(JS) | click X Y | scroll DX DY
  inject BASE64(JS) (a user script for every page, also run now) | policy ID allow|block
  answer ID BASE64(JSON) (the result of a "call") | back | forward | reload | text BASE64 (typed text) | key NAME
Events go to stdout: "started URL", "loaded URL", "title BASE64", "eval ID BASE64(JSON)",
"history CANBACK CANFORWARD", "nav ID URL" (a link click awaiting a policy answer), "call ID BASE64(path?query)" (the page called
apklayer://bridge/<interface>/<method>?a=<JSON args>, a synchronous XHR: addJavascriptInterface). Frames are written to
$1 (<dir>/<id>.rgba, header {w,h,seq} little-endian + top-down RGBA), the format of the GL path.
"""
import base64, json, os, struct, sys, time
os.environ.setdefault("WEBKIT_DISABLE_COMPOSITING_MODE", "1")
import gi
gi.require_version("Gtk", "3.0")
gi.require_version("WebKit2", "4.1")
gi.require_version("Soup", "3.0")
from gi.repository import Gio, GLib, Gtk, Soup, WebKit2

out_path, data_dir = sys.argv[1], sys.argv[2]
win = Gtk.OffscreenWindow()
win.set_default_size(480, 800)
ctx = WebKit2.WebContext.new_with_website_data_manager(
    WebKit2.WebsiteDataManager(base_data_directory=data_dir, base_cache_directory=data_dir + "/cache"))
calls = {}
call_ids = iter(range(1, 1 << 30))


def on_bridge(request):  # the page's synchronous XHR to apklayer://bridge/...: answered by the app host
    ident = next(call_ids)
    calls[ident] = request
    uri = request.get_uri()
    say("call", ident, base64.b64encode(uri.split("://bridge/", 1)[1].encode()).decode())


ctx.register_uri_scheme("apklayer", on_bridge)
security = ctx.get_security_manager()
security.register_uri_scheme_as_cors_enabled("apklayer")
security.register_uri_scheme_as_secure("apklayer")
view = WebKit2.WebView.new_with_context(ctx)
view.get_settings().set_enable_write_console_messages_to_stdout(True)  # page errors show up in the log
manager = view.get_user_content_manager()
decisions = {}
nav_ids = iter(range(1, 1 << 30))
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
    if event == WebKit2.LoadEvent.STARTED:
        say("started", v.get_uri())
    if event in (WebKit2.LoadEvent.COMMITTED, WebKit2.LoadEvent.FINISHED):
        bf = v.get_back_forward_list()
        say("history", int(v.can_go_back()), int(v.can_go_forward()))
    if event == WebKit2.LoadEvent.FINISHED:
        say("loaded", v.get_uri())


view.connect("load-changed", on_load)
view.connect("notify::title", lambda v, _: say("title", base64.b64encode((v.get_title() or "").encode()).decode()))


def on_policy(v, decision, kind):
    if kind != WebKit2.PolicyDecisionType.NAVIGATION_ACTION:
        return False
    action = decision.get_navigation_action()
    if action.get_navigation_type() not in (WebKit2.NavigationType.LINK_CLICKED, WebKit2.NavigationType.FORM_SUBMITTED):
        return False  # loads the app started itself are not asked about
    ident = next(nav_ids)
    decisions[ident] = decision
    say("nav", ident, action.get_request().get_uri())
    return True  # the decision is answered later by "policy"


view.connect("decide-policy", on_policy)


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
    elif c == "inject":
        src = base64.b64decode(parts[1]).decode()
        manager.add_script(WebKit2.UserScript.new(src, WebKit2.UserContentInjectedFrames.ALL_FRAMES,
                                                  WebKit2.UserScriptInjectionTime.START, None, None))
        view.run_javascript(src, None, None, None)
    elif c == "answer":
        req = calls.pop(int(parts[1]), None)
        if req is not None:
            body = base64.b64decode(parts[2])
            resp = WebKit2.URISchemeResponse.new(Gio.MemoryInputStream.new_from_bytes(GLib.Bytes.new(body)), len(body))
            resp.set_content_type("application/json")
            headers = Soup.MessageHeaders.new(Soup.MessageHeadersType.RESPONSE)
            headers.append("Access-Control-Allow-Origin", "*")
            resp.set_http_headers(headers)
            req.finish_with_response(resp)
    elif c == "policy":
        d = decisions.pop(int(parts[1]), None)
        if d is not None:
            d.use() if parts[2] == "allow" else d.ignore()
    elif c == "back":
        view.go_back()
    elif c == "forward":
        view.go_forward()
    elif c == "reload":
        view.reload()
    elif c == "text":  # typed text goes to the focused element of the page
        view.run_javascript("document.execCommand('insertText',false,%s)" % json.dumps(base64.b64decode(parts[1]).decode()), None, None, None)
    elif c == "key":  # editing keys
        js = {"BackSpace": "document.execCommand('delete')", "Delete": "document.execCommand('forwardDelete')",
              "Return": "(e=>{if(e&&e.form)e.form.requestSubmit?e.form.requestSubmit():e.form.submit();else document.execCommand('insertParagraph')})(document.activeElement)"}.get(parts[1])
        if js:
            view.run_javascript(js, None, None, None)
    elif c == "scroll":
        view.run_javascript("window.scrollBy(%s,%s)" % (parts[1], parts[2]), None, None, None)
    elif c == "click":
        view.run_javascript("(e=>{if(e){if(e.focus)e.focus();e.click()}})(document.elementFromPoint(%s,%s))" % (parts[1], parts[2]), None, None, None)


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
