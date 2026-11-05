import re,sys,os
p=os.path.expanduser('~/apklayer-dev-cam/data/apklayer/cam/app-data/net.sourceforge.opencamera/app/shared_prefs/net.sourceforge.opencamera_preferences.xml')
s=open(p).read()
for k in ('preference_camera_api','camera_id'):
    s=re.sub(r'\s*<string name="%s">[^<]*</string>'%k,'',s)
s=s.replace('<map>','<map>\n    <string name="preference_camera_api">preference_camera_api_old</string>\n    <string name="camera_id">%s</string>'%sys.argv[1],1)
open(p,'w').write(s)
