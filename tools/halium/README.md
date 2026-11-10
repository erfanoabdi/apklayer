Scripts used on the Ubuntu Touch phone (docs/HALIUM.md "Hardware providers"): copy them to the instance's directory (`~/apklayer-dev-cam`, `env.sh` names it) and run there.
`ut-cam.sh`, `ut-player.sh`, `ut-audio.sh`: one app, started, checked and stopped in one command. `ocprefs.py <camera id>`: Open Camera's preferences. `orb-build.sh <module>...`: a build in ~/los24 (OrbStack), queued behind other builds.
`ut-rotate.sh DIR PKG STEP...` with `fake-accel.py`: turns the phone without touching it (a fake accelerometer for sensorfwd; docs/HALIUM.md "Rotation on Ubuntu Touch").
