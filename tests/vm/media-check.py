#!/usr/bin/env python3
"""Checks of the media tests (tests/vm/fennec-media.sh, newpipe-media.sh), printing one number each:
  media-check.py frames A.png B.png     pixels that differ between two app screenshots (a playing video changes them)
  media-check.py picture A.png          distinct colours (4 bits a channel) of a screenshot: a video frame has hundreds, a player's black area with its spinner and controls ~50
  media-check.py pipewire PW.json ID    running PipeWire output streams whose client is a process of apklayer instance ID (pw-dump output)"""
import json
import sys


def frames(a, b):
    from PIL import Image, ImageChops
    one, two = Image.open(a).convert('RGB'), Image.open(b).convert('RGB')
    if one.size != two.size: return 0
    return sum(1 for p in ImageChops.difference(one, two).convert('L').point(lambda x: 255 if x > 24 else 0).getdata() if p)


def picture(a):
    from PIL import Image
    return len({(r >> 4, g >> 4, b >> 4) for r, g, b in Image.open(a).convert('RGB').getdata()})


def instance(pid):  # whose instance the PipeWire client is (each instance's audio HAL is a process of its own)
    try: env = open('/proc/%s/environ' % pid, 'rb').read().split(b'\0')
    except (OSError, TypeError): return None
    return next((e[18:].decode() for e in env if e.startswith(b'APKLAYER_INSTANCE=')), 'default')


def pipewire(dump, wanted):
    objs = json.load(open(dump))
    clients = {o['id']: o['info']['props'].get('application.process.id') for o in objs if o.get('type') == 'PipeWire:Interface:Client'}
    return sum(1 for o in objs if o.get('type') == 'PipeWire:Interface:Node' and o['info']['props'].get('media.class') == 'Stream/Output/Audio'
               and o['info'].get('state') == 'running' and instance(clients.get(o['info']['props'].get('client.id'))) == wanted)


try: print({'frames': frames, 'picture': picture, 'pipewire': pipewire}[sys.argv[1]](*sys.argv[2:]))
except (OSError, ValueError, KeyError): print(0)
