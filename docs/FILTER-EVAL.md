# Render filter yardstick (`tests/vm/evalfilter.py`)

One command measures the whole render filter (FSR 1 and the AA pass, `bridge/shell/sharpen*.c`) against bilinear on a generated corpus. Every
filter change (EASU/RCAS, AA, shader edits) is judged with it: run before, run after, quote the table. This is the yardstick; `tests/vm/sharpen-eval.py`
(two pictures, PSNR/SSIM/edge/steepness/halo, `--env` for the knobs) stays as the small quick check and for the numbers older changelog entries quote.

    ninja -C b bridge/shell/sharpen-test                 # once (or set SHARPEN_TEST=<binary>)
    python3 tests/vm/evalfilter.py                        # 35/50/75 %, bilinear vs the current default pipeline ("fsr1"), ~3 s
    python3 tests/vm/evalfilter.py --json before.json     # ... change the shader, rebuild ...
    python3 tests/vm/evalfilter.py --compare before.json  # mean differences current - saved
    python3 tests/vm/evalfilter.py --filter 'fsr1-noaa:APKLAYER_SHARPEN_AA=off' --scales 60 --only text_small,text_large
    python3 tests/vm/evalfilter.py --save /tmp/x          # ref.png and every output as PNG, to look at

Options: `--filter label[:ENV=VAL,...]` (repeatable; the environment of the pass: `APKLAYER_SHARPEN_AA=off` (a test knob: the shell always runs CMAA2 ultra; `APKLAYER_SHARPEN_AA=smaa` forces the SMAA ultra fallback), `APKLAYER_SHARPEN_BACKEND` gles|vulkan), `--sharp` (the RCAS constant exp2(-stops): 0.8706 = the default 0.2 stops), `--scales`, `--only`, `--check` (exit 1 if the first
filter is below bilinear on PSNR/SSIM of the smooth and 3D scores; group `evalfilter`). It skips (exit 0) without a render node or the binary; on NVIDIA
it retries with the block-linear modifier (`SHARPEN_MODIFIER` overrides). `tests/vm/sharpen.py` stays the quick pass/fail smoke test.

## Corpus (`tests/vm/evalcorpus.py`)

One 1280x720 frame, 12 tiles of 320x240, all generated (PIL, numpy, fixed seed; no game data; DejaVu fonts, the file names and a hash of the frame are
printed so two machines can tell whether they compare like with like): `text_small` (11-17 px sans, mono, serif, light on dark), `text_large` (20-44 px),
`text_light` (dark on light), `icons` (24/32 px outlines, filled glyphs), `thin_lines` (1 px combs, 1/2 px horizontals, a fan of angles, thin shapes),
`stems` (calibrated: 1/2/3 px vertical stems and a step edge, light on dark and dark on light, rows alike), `ui_panel`, `shaded_3d` (lit spheres, 3x3
supersampled), `foliage` (1400 small leaves, high-frequency colour noise), `gradients`, `anime_flat` (flats, 2 px outlines, cel shade), `checker_persp`
(textured floor in perspective). The frame is box-reduced to the scale (35/50/75 % of 1280x720) and every candidate brings it back to 1280x720;
bilinear is PIL's. Measured on the tile minus a 6 px border (the neighbouring tile bleeds into it).

## Metrics (`tests/vm/evalmetrics.py`)

Per tile, against the original: **PSNR** (dB), **SSIM** (7x7 luma), **edge** (mean Sobel gradient out/original; 1.0 = as sharp as the original, below
is soft, above is over-sharpened), **halo%** (pixels more than 3 levels outside the original's local 5x5 min..max). Rows `score text|3d|ui|smooth`
average the tiles (text = the three text tiles; 3d = shaded_3d, foliage, checker_persp; ui = ui_panel, icons, thin_lines, anime_flat).
Text-specific, on the `stems` tile: **contrast** (peak above the background, out/original) and **width** (FWHM, out/original) of 1, 2 and 3 px stems in
both polarities (1.0 = like the original; bilinear shows 0.3 contrast and 2.9x width for a 1 px stem at 35 %), **edge acuity** (10-90 % rise of a
step edge in output px, 1.0 = hard) and its overshoot in percent of the step. Higher is better for PSNR/SSIM/contrast, closer to 1.0 for edge/width,
lower for rise/halo/overshoot.

> 2026-11-23 (later): the text aid, the calm pass and the smaa-high, smaa-ultra, cmaa2-high settings were removed; CMAA2 ultra is the only anti-aliasing and the render scale starts at 60 % (65 % since the shimmer entry below). The tables below that mention them, and the 35 % / 50 % rows, are historical measurements.

## Baseline (2026-11-23, historical: measured before our own AA was removed; the shell's pipeline then was EASU + RCAS 0.2 stops with the text aid + our AA high, GLES = Vulkan, RTX 5060 Ti; the default is cmaa2-ultra now and Vulkan has no AA)

| scale | score | PSNR bil / fsr1 / fsr1 AA off | SSIM bil / fsr1 / AA off | edge bil / fsr1 / AA off |
|---|---|---|---|---|
| 35 % | text | 17.56 / 17.16 / 17.99 | 0.743 / 0.744 / 0.778 | 0.53 / 0.48 / 0.63 |
| 35 % | 3d | 27.97 / 27.94 / 29.06 | 0.841 / 0.826 / 0.871 | 0.74 / 0.71 / 0.82 |
| 35 % | ui | 20.72 / 20.77 / 21.19 | 0.808 / 0.824 / 0.835 | 0.56 / 0.58 / 0.64 |
| 35 % | smooth | 35.19 / 42.25 / 42.38 | 0.987 / 0.991 / 0.991 | 0.99 / 1.00 / 1.01 |
| 50 % | text | 19.37 / 19.36 / 20.28 | 0.829 / 0.854 / 0.879 | 0.65 / 0.67 / 0.79 |
| 50 % | 3d | 30.33 / 30.75 / 32.28 | 0.901 / 0.897 / 0.928 | 0.82 / 0.80 / 0.91 |
| 50 % | ui | 23.20 / 24.07 / 24.71 | 0.874 / 0.896 / 0.904 | 0.67 / 0.76 / 0.81 |
| 50 % | smooth | 37.37 / 47.04 / 47.11 | 0.991 / 0.996 / 0.996 | 0.99 / 1.01 / 1.01 |
| 75 % | text | 21.90 / 23.37 / 23.77 | 0.913 / 0.941 / 0.944 | 0.81 / 0.91 / 0.94 |
| 75 % | 3d | 33.16 / 34.19 / 34.30 | 0.950 / 0.957 / 0.959 | 0.91 / 0.95 / 0.98 |
| 75 % | ui | 25.93 / 27.87 / 28.05 | 0.940 / 0.959 / 0.960 | 0.84 / 0.93 / 0.95 |
| 75 % | smooth | 43.41 / 51.42 / 51.25 | 0.996 / 0.998 / 0.998 | 0.99 / 1.01 / 1.01 |

Text stems (1 px / 2 px, contrast, then width) and step edge rise, bilinear vs FSR 1 (default): 35 %: 0.32/0.64 and 2.9/1.47 vs 0.32/0.64 and 2.9/1.52, rise 4.58 vs 4.14 px; 50 %: 0.37/0.75 and 2.5/1.25 vs 0.43/0.87 and 2.1/1.00, rise 2.20 vs 0.81 px; 75 %: 0.87/0.87 and 1.5/1.13 vs 0.99/0.99 and 1.2/1.09, rise 2.36 vs 2.18 px.

What it shows: FSR 1 beats bilinear on smooth and UI content at every scale, on 3D at 50 and 75 % and on text at 75 %; at 35 % (text, 3D) and for the text at 50 % the
default AA pass pulls PSNR to bilinear's level or just below and SSIM/edge on the 3D tiles below it (AA off: above bilinear everywhere, text 17.99 / 20.28 dB, edge 0.63 / 0.79).
The AA gate keeps it from smearing stems (stem contrast and the step edge's rise equal with AA off); what it still softens are the glyph bodies and 3D detail. The GLES and Vulkan
backends give the same numbers (tests/vm/sharpen_parity.py). These are the numbers a change to the
EASU/RCAS port, the AA pass is measured by.

Limits: the input is a box-reduced copy of a full-resolution frame (a game renders at the lower size, with its own aliasing); the metrics are
signal measures (a person judges the look); colour AA effects show in luma metrics only.

## SMAA 1x and CMAA2 against our own AA (2026-11-23, RTX 5060 Ti, GLES, aliased synthetic source)
A corpus drawn natively at the source size WITHOUT anti-aliasing (text 11-44 px, 1 px lines at many angles, shaded spheres and polygons with stair-stepped edges, foliage noise; the script was a task scratch file, not kept: `tests/vm/evalfilter.py` box-reduces an already smooth image and therefore cannot show jaggies) goes through `sharpen-test` to 1280x720; the truth is the same scene at 4x supersampling, box reduced. PSNR in dB against the truth per region, textEdge = Sobel edge strength of the text region / the truth (1.0 = as sharp), 3dJag = mean abs colour error on the 3D edge pixels (lower = smoother, closer to the truth), halo = % of pixels outside the truth's 5x5 range by more than 8 levels, ms = GPU plus fence wait per frame at 1280x720 output (200 frames; RCAS and the text aid on; differences of 0.01 ms are noise). The cmaa2 levels differ only in the edge threshold and give identical pictures on this corpus (hard, high-contrast edges) apart from foliage.

```
== 35% (448x251 -> 1280x720), aliased source ==
filter        textPSNR textEdge linesPSNR  3dPSNR   3dJag foliage  halo%     ms       
off             13.32    0.98   13.83   23.27   50.16   20.95   1.92  0.122
low             13.54    0.89   14.19   23.57   49.10   21.83   1.71  0.141
high            13.67    0.84   14.34   23.69   48.67   22.47   1.58  0.173
smaa-low        14.34    0.68   14.61   23.90   48.25   22.28   1.60  0.153
smaa-medium     14.35    0.68   14.64   23.92   48.38   22.31   1.58  0.149
smaa-high       13.88    0.82   14.37   23.71   49.72   21.80   1.71  0.160
smaa-ultra      13.88    0.82   14.38   23.72   49.54   21.80   1.71  0.165
cmaa2-low       13.71    0.84   14.67   24.05   46.83   21.74   1.70  0.204
cmaa2-medium    13.71    0.84   14.67   24.05   46.83   21.80   1.68  0.178
cmaa2-high      13.71    0.84   14.67   24.05   46.83   21.82   1.66  0.187
cmaa2-ultra     13.71    0.84   14.67   24.05   46.83   21.82   1.66  0.181
== 50% (640x360 -> 1280x720), aliased source ==
filter        textPSNR textEdge linesPSNR  3dPSNR   3dJag foliage  halo%     ms       
off             17.34    0.99   15.69   26.28   33.97   24.26   0.34  0.114
low             17.52    0.94   16.01   26.62   33.11   25.19   0.27  0.150
high            17.60    0.91   16.20   26.82   32.75   26.11   0.23  0.169
smaa-low        18.09    0.79   16.57   27.00   32.44   25.90   0.18  0.158
smaa-medium     18.10    0.78   16.57   26.96   32.61   25.94   0.17  0.191
smaa-high       17.72    0.88   16.12   26.54   34.30   25.26   0.25  0.157
smaa-ultra      17.72    0.88   16.14   26.56   34.08   25.26   0.25  0.169
cmaa2-low       17.67    0.91   16.88   27.32   30.99   25.18   0.24  0.187
cmaa2-medium    17.67    0.91   16.88   27.32   30.99   25.25   0.23  0.184
cmaa2-high      17.67    0.91   16.88   27.32   31.00   25.27   0.22  0.186
cmaa2-ultra     17.67    0.91   16.88   27.32   30.99   25.28   0.22  0.183
== 75% (960x540 -> 1280x720), aliased source ==
filter        textPSNR textEdge linesPSNR  3dPSNR   3dJag foliage  halo%     ms       
off             18.59    1.01   18.72   29.70   21.32   26.82   0.21  0.111
low             18.70    0.99   18.86   29.88   20.90   27.36   0.19  0.139
high            18.72    0.99   18.89   29.92   20.87   27.49   0.19  0.153
smaa-low        19.15    0.90   19.71   30.54   19.55   28.48   0.08  0.164
smaa-medium     19.15    0.90   19.69   30.47   19.69   28.53   0.08  0.169
smaa-high       18.94    0.95   19.16   29.97   20.79   27.96   0.13  0.171
smaa-ultra      18.94    0.95   19.16   29.98   20.68   27.97   0.13  0.194
cmaa2-low       18.90    0.97   20.03   30.80   18.87   27.75   0.10  0.200
cmaa2-medium    18.90    0.97   20.03   30.80   18.87   27.82   0.10  0.204
cmaa2-high      18.90    0.97   20.03   30.80   18.86   27.85   0.10  0.197
cmaa2-ultra     18.90    0.97   20.03   30.80   18.86   27.85   0.10  0.197
```
Reading: both new filters remove more stair steps than our own `high` on this aliased source (3dJag at 50%: off 33.97, low 33.11, high 32.75, smaa-low 32.44, smaa-medium 32.61, smaa-high 34.30, cmaa2 31.0; smaa-high/ultra keep corners and so smooth less) and CMAA2 keeps the text edges as well as `high` (0.91 against 0.91; smaa-high 0.88, smaa-low/medium 0.79: luma detection and no corner detection smear text). CMAA2 is best on 3D, thin lines and halo at all three scales and costs about 0.015 ms more than `high` at 1280x720. On `evalfilter.py` (a smooth, box-reduced source, 50%, text score PSNR: off 20.28, high 19.36, smaa-low 18.84, smaa-high 19.63, cmaa2 19.93-20.04) they do not hurt text more than `high` does: the cost of any AA there is what it blurs of something that is not aliased.
Recommendation: the default stays `high` until a human has looked at a real window (PLAN). The candidate for a new default is `cmaa2-high` (needs ES 3.1 compute, runs as `smaa-high` where it is missing); `smaa-high` is the second choice, `smaa-low/medium` are not recommended for UI-heavy games (text).


## Anti-aliasing generations (2026-11-23)

Each generation built from its own commit (`953966a6` first AA, `f75c3bf5` ratio-dependent strength, `82e013e3` colour-aware + second pass, `21167569` main with SMAA/CMAA2) and driven with identical `in.raw` files through its own `tests/hals/sharpen-test.c` (FSR 1: `SHARPEN_MODE=0` in the old trees, sharpness 0.8706, `APKLAYER_SHARPEN_AA` = off|low|high|smaa-*|cmaa2-*), GLES backend (no Vulkan headers on the machine), RTX 5060 Ti. Input: the `evalcorpus.py` frame reduced to 35/50/75 % by nearest (aliased, like a game without AA) and by box; output 1280x720. Cell = **text edge** (mean Sobel out/original over the three text tiles, 1.0 = as sharp as the original) / **3D PSNR** (dB, `shaded_3d` tile against the corpus frame, which is 3x3 supersampled: the truth) / **3D jaggedness** (mean absolute RGB error, in levels, on the pixels where the truth's luma gradient exceeds 16) / **halo %** (pixels more than 3 levels outside the original's local 5x5 range, mean of all 12 tiles). GPU ms: 300 frames, one run at a time, box source. Commit `8cd20cb2` (default high) gives the same pictures as `f75c3bf5` high (identical output hashes), so it has no row of its own. The corpus has no hair/strap tile (`anime_flat` is the nearest thing), so none is reported. The old trees' "off" rows differ from main's "off" because the FSR port itself changed in between (RCAS clamp, text aid).

**nearest-reduced (aliased) source**: text edge / 3D PSNR dB / 3D jaggedness / halo %

| row | 35 % | 50 % | 75 % |
|---|---|---|---|
| bilinear | 0.67 / 36.74 / 2.9 / 0.35 | 0.77 / 36.25 / 3.3 / 0.01 | 0.84 / 40.90 / 1.7 / 0.01 |
| 1 first AA 953966a6: off | 0.78 / 37.90 / 2.8 / 2.21 | 0.94 / 35.23 / 3.7 / 1.76 | 0.95 / 40.58 / 1.8 / 3.63 |
| 1 first AA 953966a6: low | 0.71 / 38.23 / 2.7 / 1.41 | 0.83 / 35.58 / 3.6 / 1.05 | 0.83 / 41.45 / 1.7 / 1.79 |
| 1 first AA 953966a6: high | 0.56 / 38.69 / 2.6 / 0.75 | 0.64 / 36.17 / 3.5 / 0.72 | 0.63 / 42.47 / 1.6 / 0.62 |
| 2 ratio-dependent f75c3bf5: off | 0.78 / 37.90 / 2.8 / 2.21 | 0.94 / 35.23 / 3.7 / 1.76 | 0.95 / 40.58 / 1.8 / 3.63 |
| 2 ratio-dependent f75c3bf5: low | 0.61 / 38.55 / 2.6 / 0.90 | 0.81 / 35.65 / 3.6 / 0.96 | 0.88 / 41.14 / 1.7 / 2.16 |
| 2 ratio-dependent f75c3bf5: high | 0.56 / 38.69 / 2.6 / 0.75 | 0.69 / 36.02 / 3.5 / 0.77 | 0.83 / 41.49 / 1.7 / 1.69 |
| 3 colour-aware + 2 pass 82e013e3: off | 0.78 / 37.90 / 2.8 / 2.21 | 0.94 / 35.23 / 3.7 / 1.76 | 0.95 / 40.58 / 1.8 / 3.63 |
| 3 colour-aware + 2 pass 82e013e3: low | 0.60 / 38.80 / 2.6 / 0.73 | 0.80 / 35.71 / 3.6 / 0.67 | 0.87 / 41.32 / 1.7 / 1.95 |
| 3 colour-aware + 2 pass 82e013e3: high | 0.44 / 38.63 / 2.8 / 0.51 | 0.57 / 36.21 / 3.6 / 0.50 | 0.82 / 41.77 / 1.6 / 1.50 |
| 4/5 main 21167569: off | 0.79 / 38.06 / 3.0 / 0.06 | 0.93 / 35.48 / 3.8 / 0.02 | 0.95 / 40.58 / 2.2 / 0.05 |
| 4/5 main 21167569: low | 0.70 / 38.61 / 3.0 / 0.06 | 0.86 / 35.81 / 3.7 / 0.01 | 0.93 / 41.08 / 2.1 / 0.03 |
| 4/5 main 21167569: high | 0.64 / 38.45 / 3.1 / 0.10 | 0.80 / 36.06 / 3.7 / 0.01 | 0.92 / 41.20 / 2.1 / 0.03 |
| 4/5 main 21167569: smaa-low | 0.54 / 37.96 / 3.1 / 0.32 | 0.69 / 36.25 / 3.7 / 0.10 | 0.80 / 41.17 / 2.1 / 0.03 |
| 4/5 main 21167569: smaa-medium | 0.54 / 37.57 / 3.3 / 0.41 | 0.69 / 36.15 / 3.7 / 0.15 | 0.80 / 41.31 / 2.1 / 0.02 |
| 4/5 main 21167569: smaa-high | 0.64 / 36.14 / 3.5 / 0.30 | 0.80 / 36.76 / 3.7 / 0.07 | 0.87 / 41.19 / 2.1 / 0.03 |
| 4/5 main 21167569: smaa-ultra | 0.64 / 36.17 / 3.5 / 0.29 | 0.80 / 36.68 / 3.7 / 0.07 | 0.88 / 41.26 / 2.1 / 0.03 |
| 4/5 main 21167569: cmaa2-low | 0.67 / 37.94 / 3.1 / 0.06 | 0.83 / 36.10 / 3.7 / 0.02 | 0.90 / 40.92 / 2.2 / 0.04 |
| 4/5 main 21167569: cmaa2-medium | 0.67 / 37.87 / 3.1 / 0.05 | 0.83 / 36.17 / 3.7 / 0.02 | 0.90 / 41.06 / 2.2 / 0.03 |
| 4/5 main 21167569: cmaa2-high | 0.67 / 37.81 / 3.2 / 0.06 | 0.83 / 36.20 / 3.7 / 0.01 | 0.90 / 41.14 / 2.1 / 0.03 |
| 4/5 main 21167569: cmaa2-ultra | 0.68 / 37.92 / 3.2 / 0.06 | 0.83 / 36.24 / 3.7 / 0.01 | 0.90 / 41.20 / 2.1 / 0.03 |

**box-reduced source**: text edge / 3D PSNR dB / 3D jaggedness / halo %

| row | 35 % | 50 % | 75 % |
|---|---|---|---|
| bilinear | 0.53 / 36.91 / 3.1 / 1.46 | 0.65 / 39.74 / 2.4 / 0.01 | 0.81 / 42.79 / 1.3 / 0.00 |
| 1 first AA 953966a6: off | 0.64 / 39.33 / 2.5 / 1.66 | 0.80 / 43.84 / 1.6 / 0.84 | 0.92 / 44.60 / 1.1 / 3.35 |
| 1 first AA 953966a6: low | 0.60 / 39.48 / 2.5 / 1.34 | 0.72 / 44.14 / 1.6 / 0.42 | 0.81 / 45.75 / 1.0 / 1.63 |
| 1 first AA 953966a6: high | 0.50 / 39.52 / 2.5 / 1.06 | 0.57 / 43.89 / 1.6 / 0.26 | 0.62 / 46.22 / 1.0 / 0.69 |
| 2 ratio-dependent f75c3bf5: off | 0.64 / 39.33 / 2.5 / 1.66 | 0.80 / 43.84 / 1.6 / 0.84 | 0.92 / 44.60 / 1.1 / 3.35 |
| 2 ratio-dependent f75c3bf5: low | 0.54 / 39.53 / 2.5 / 1.13 | 0.70 / 44.14 / 1.6 / 0.38 | 0.85 / 45.38 / 1.1 / 2.01 |
| 2 ratio-dependent f75c3bf5: high | 0.50 / 39.52 / 2.5 / 1.06 | 0.61 / 44.02 / 1.6 / 0.28 | 0.81 / 45.79 / 1.0 / 1.55 |
| 3 colour-aware + 2 pass 82e013e3: off | 0.64 / 39.33 / 2.5 / 1.66 | 0.80 / 43.84 / 1.6 / 0.84 | 0.92 / 44.60 / 1.1 / 3.35 |
| 3 colour-aware + 2 pass 82e013e3: low | 0.51 / 39.55 / 2.5 / 1.05 | 0.69 / 44.22 / 1.6 / 0.26 | 0.85 / 45.55 / 1.0 / 1.79 |
| 3 colour-aware + 2 pass 82e013e3: high | 0.40 / 38.87 / 2.8 / 1.04 | 0.52 / 42.93 / 1.9 / 0.21 | 0.80 / 46.00 / 1.0 / 1.37 |
| 4/5 main 21167569: off | 0.63 / 39.05 / 2.9 / 0.62 | 0.79 / 43.70 / 1.8 / 0.01 | 0.94 / 44.64 / 1.5 / 0.02 |
| 4/5 main 21167569: low | 0.54 / 39.03 / 3.0 / 0.66 | 0.73 / 43.66 / 1.9 / 0.00 | 0.91 / 45.08 / 1.5 / 0.01 |
| 4/5 main 21167569: high | 0.48 / 38.54 / 3.1 / 0.74 | 0.67 / 42.97 / 2.0 / 0.02 | 0.91 / 45.18 / 1.5 / 0.01 |
| 4/5 main 21167569: smaa-low | 0.48 / 38.21 / 3.1 / 0.97 | 0.61 / 42.94 / 1.9 / 0.07 | 0.78 / 44.70 / 1.5 / 0.01 |
| 4/5 main 21167569: smaa-medium | 0.48 / 37.52 / 3.3 / 1.07 | 0.61 / 42.51 / 1.9 / 0.12 | 0.79 / 44.42 / 1.5 / 0.01 |
| 4/5 main 21167569: smaa-high | 0.54 / 36.45 / 3.5 / 0.87 | 0.70 / 39.55 / 2.3 / 0.04 | 0.87 / 43.21 / 1.6 / 0.01 |
| 4/5 main 21167569: smaa-ultra | 0.55 / 36.26 / 3.6 / 0.94 | 0.70 / 39.34 / 2.4 / 0.07 | 0.87 / 42.98 / 1.6 / 0.01 |
| 4/5 main 21167569: cmaa2-low | 0.56 / 38.45 / 3.0 / 0.68 | 0.72 / 42.84 / 1.9 / 0.01 | 0.89 / 44.68 / 1.5 / 0.01 |
| 4/5 main 21167569: cmaa2-medium | 0.56 / 38.29 / 3.1 / 0.71 | 0.72 / 42.55 / 1.9 / 0.01 | 0.89 / 44.61 / 1.5 / 0.01 |
| 4/5 main 21167569: cmaa2-high | 0.56 / 38.27 / 3.1 / 0.72 | 0.72 / 42.52 / 2.0 / 0.00 | 0.89 / 44.60 / 1.5 / 0.01 |
| 4/5 main 21167569: cmaa2-ultra | 0.57 / 38.14 / 3.2 / 0.74 | 0.73 / 42.40 / 2.0 / 0.00 | 0.89 / 44.59 / 1.5 / 0.01 |

**GPU ms per frame** (GLES, 300 frames, box source, 1280x720 output)

| row | 35 % | 50 % | 75 % |
|---|---|---|---|
| 1 first AA 953966a6: off | 0.153 | 0.135 | 0.134 |
| 1 first AA 953966a6: low | 0.172 | 0.182 | 0.180 |
| 1 first AA 953966a6: high | 0.193 | 0.200 | 0.180 |
| 2 ratio-dependent f75c3bf5: off | 0.138 | 0.146 | 0.168 |
| 2 ratio-dependent f75c3bf5: low | 0.193 | 0.186 | 0.186 |
| 2 ratio-dependent f75c3bf5: high | 0.200 | 0.177 | 0.185 |
| 3 colour-aware + 2 pass 82e013e3: off | 0.158 | 0.136 | 0.144 |
| 3 colour-aware + 2 pass 82e013e3: low | 0.197 | 0.191 | 0.198 |
| 3 colour-aware + 2 pass 82e013e3: high | 0.233 | 0.231 | 0.190 |
| 4/5 main 21167569: off | 0.163 | 0.161 | 0.157 |
| 4/5 main 21167569: low | 0.205 | 0.208 | 0.199 |
| 4/5 main 21167569: high | 0.269 | 0.266 | 0.213 |
| 4/5 main 21167569: smaa-low | 0.240 | 0.258 | 0.231 |
| 4/5 main 21167569: smaa-medium | 0.216 | 0.221 | 0.223 |
| 4/5 main 21167569: smaa-high | 0.236 | 0.228 | 0.264 |
| 4/5 main 21167569: smaa-ultra | 0.241 | 0.245 | 0.268 |
| 4/5 main 21167569: cmaa2-low | 0.242 | 0.252 | 0.273 |
| 4/5 main 21167569: cmaa2-medium | 0.283 | 0.258 | 0.298 |
| 4/5 main 21167569: cmaa2-high | 0.252 | 0.283 | 0.291 |
| 4/5 main 21167569: cmaa2-ultra | 0.258 | 0.272 | 0.312 |

Reading the numbers (high unless noted, nearest input unless noted):
- First AA (`953966a6`): 3D PSNR +0.8 / +0.9 / +1.9 dB over AA off at 35/50/75 % and the FSR halo gone (3.6 % to 0.6 % at 75 %), but a flat strength cost the text (edge 0.95 to 0.63 at 75 %). Ratio-dependent strength (`f75c3bf5`) gave the text back at high ratios (75 %: 0.63 to 0.83) for about 1 dB of 3D PSNR there. The colour-aware second pass (`82e013e3`) cut halos further and is a little better on 3D at 50/75 %, but softens the text at 35/50 % (0.44 / 0.57) and on box input is worse than AA off on 3D at 35/50 % (38.9 vs 39.3, 42.9 vs 43.8 dB).
- Current main (RCAS clamp, gate) has almost no halos (0.0-0.1 % on nearest input, 0.6-0.7 % at 35 % box) and keeps more text: high 0.64 / 0.80 / 0.92 against 0.44 / 0.57 / 0.82 for `82e013e3`. 3D PSNR is +0.4 / +0.6 / +0.6 dB over main's AA off on nearest input, but below AA off on box input at 35/50 %. Jaggedness is its weak spot: 3.1 / 3.7 / 2.1 against 2.6-2.8 / 3.5-3.6 / 1.6 for the old generations.
- SMAA and CMAA2 do not beat the built-in pass: SMAA low/medium soften the text (0.54 / 0.69 / 0.80) for a PSNR near the built-in's; SMAA high/ultra lose 2 dB or more of 3D PSNR (35 % nearest; 50-75 % box) and have the highest jaggedness; CMAA2 keeps the text between low and off (0.67 / 0.83 / 0.90) and about matches AA off on 3D with nearest input, but loses 0.6-1.3 dB on box input at 35/50 %.
- Best for text: main with AA off or low (0.63-0.95), CMAA2 next. Best for 3D by PSNR/jaggedness: the old high settings (`953966a6` / `f75c3bf5` high: 38.7 dB at 35 %, 42.5 dB at 75 % with 0.63 text edge), main high is the compromise (3D 38.5 / 36.1 / 41.2 dB with text 0.64 / 0.80 / 0.92 and no halo).
- Cost: the FSR pass alone is 0.13-0.16 ms per frame, built-in low/high 0.17-0.27 (main high is the slowest of the built-ins, with its second pass), SMAA 0.22-0.27, CMAA2 0.24-0.31. GPU time is not the limit; the choice is quality.
## SMAA / CMAA2 stronger on 3D (2026-11-23, RTX 5060 Ti, GLES, `tests/vm/evalalias.py`, aliased source)

Source: a 4x4 supersampled scene (12 tiles: text, icons, lines, shaded shapes, a city with lamp posts and wires, foliage, four character-like tiles with 1-3 px hair strands, belts, straps, buckles, torn trouser hems, a thin blade, specular arcs, hue-only edges, a hair close-up) taken by nearest sample at the source size; truth = its box reduction. Columns: txtEdge = Sobel edge strength of the text tiles / truth (1.0 = as sharp; the text protection check: must not fall more than ~0.02 against before), uiPSNR, 3dPSNR (shapes, city, foliage), 3dJag = mean abs colour error on the truth's edge pixels, 3dStair / chStair = roughness along the truth's edges on the 3D / character tiles (lower = fewer stair steps, but below the truth's own ~11 means blur), charPSNR / charJag / charEdg (sharpness) / charHalo (% pixels > 8 levels outside the truth's 5x5 range), ms = GPU plus fence per frame at 1280x720 output (100 frames). Bilinear, off, low, high are unchanged by this work (the numbers below are the new run).

Findings: thresholds 0.15..0.03 and SMAA's local contrast factor change nothing on this high-contrast corpus; CMAA2 shape blur 0.25 or SMAA corner rounding 0 smooth stairs only by blurring text as much (txtEdge -0.09..-0.17). What smooths without that is repeated runs (output of one run is the input of the next) with the text gate (the RCAS text test on the 3x3): CMAA2 text edge goes up, 3D stairs drop. Extra runs without the gate: -0.07 text edge at 35 %. SMAA runs more than twice go unstable (halo 0.4-0.8 %), so SMAA stops at two.

```
== 35% (BEFORE, then AFTER) ==
filter          txtEdge   uiPSNR   3dPSNR    3dJag  3dStair  charPSNR  charJag  chStair  charEdg charHalo      ms
off                0.82    19.05    24.72    18.61    10.33     22.02    21.86    10.93     0.90     0.39   0.200
low                0.73    19.17    24.91    18.45     7.40     22.27    21.50     7.82     0.77     0.21   0.203
high               0.68    19.14    24.48    19.23     6.81     21.97    22.22     7.41     0.67     0.26   0.256
smaa-high          0.65    19.75    24.72    19.24     8.05     22.17    21.92     8.65     0.77     0.28   0.230   before
smaa-ultra         0.65    19.74    24.69    19.31     8.06     22.17    21.93     8.63     0.77     0.28   0.250   before
cmaa2-high         0.69    19.43    24.90    18.66     8.44     22.27    21.73     8.78     0.79     0.23   0.249   before
cmaa2-ultra        0.69    19.43    24.90    18.68     8.45     22.27    21.73     8.79     0.79     0.24   0.243   before
smaa-high+          0.63    19.70    24.24    20.19     7.21     21.91    22.46     7.82     0.72     0.30   0.263   after
smaa-ultra+         0.61    19.77    24.11    20.50     7.01     21.88    22.60     7.57     0.70     0.44   0.304   after
cmaa2-high+         0.73    19.12    24.46    19.57     7.98     22.02    22.23     8.34     0.75     0.23   0.350   after
cmaa2-ultra+        0.71    19.11    24.24    20.10     7.55     21.92    22.54     7.94     0.72     0.25   0.333   after

== 50% (BEFORE, then AFTER) ==
filter          txtEdge   uiPSNR   3dPSNR    3dJag  3dStair  charPSNR  charJag  chStair  charEdg charHalo      ms
off                0.96    20.62    26.22    14.69    12.91     22.70    18.54    12.42     0.95     0.49   0.175
low                0.89    20.79    26.57    14.44    10.41     23.06    18.17    10.15     0.88     0.36   0.234
high               0.84    20.80    26.38    15.10     9.01     23.11    18.69     9.11     0.80     0.40   0.264
smaa-high          0.81    21.01    26.39    14.80     9.93     22.98    18.31    10.12     0.88     0.19   0.243   before
smaa-ultra         0.81    21.01    26.39    14.82     9.97     22.98    18.33    10.12     0.88     0.19   0.262   before
cmaa2-high         0.86    20.95    26.72    14.25    10.18     23.12    18.01    10.01     0.88     0.24   0.267   before
cmaa2-ultra        0.86    20.95    26.73    14.24    10.16     23.12    18.01    10.03     0.88     0.24   0.260   before
smaa-high+          0.81    21.01    26.39    14.80     9.93     22.98    18.31    10.12     0.88     0.19   0.429   after
smaa-ultra+         0.77    20.99    25.98    15.96     8.79     22.99    18.85     9.13     0.82     0.09   0.402   after
cmaa2-high+         0.90    20.64    26.40    14.76    10.01     22.95    18.34    10.06     0.87     0.30   0.306   after
cmaa2-ultra+        0.88    20.61    26.22    15.22     9.42     22.90    18.59     9.63     0.85     0.29   0.357   after

== 75% (BEFORE, then AFTER) ==
filter          txtEdge   uiPSNR   3dPSNR    3dJag  3dStair  charPSNR  charJag  chStair  charEdg charHalo      ms
off                1.01    24.22    29.53     9.56    14.68     26.47    11.60    14.76     1.02     0.31   0.178
low                0.98    24.29    29.87     9.30    13.02     26.69    11.46    13.25     0.99     0.28   0.215
high               0.98    24.29    29.90     9.34    12.68     26.72    11.51    12.96     0.98     0.28   0.228
smaa-high          0.92    24.75    30.41     8.97    11.84     27.08    11.12    12.41     0.98     0.17   0.260   before
smaa-ultra         0.92    24.74    30.35     9.01    11.87     27.07    11.15    12.40     0.98     0.17   0.307   before
cmaa2-high         0.95    24.74    30.70     8.69    12.19     27.09    10.98    12.23     0.98     0.20   0.286   before
cmaa2-ultra        0.95    24.74    30.70     8.69    12.17     27.08    10.98    12.24     0.98     0.20   0.278   before
smaa-high+          0.92    24.75    30.41     8.97    11.84     27.08    11.12    12.41     0.98     0.17   0.262   after
smaa-ultra+         0.91    24.72    30.35     9.09    11.65     27.08    11.28    12.09     0.97     0.17   0.319   after
cmaa2-high+         0.99    24.32    29.93     9.17    12.76     26.80    11.19    12.97     0.99     0.24   0.276   after
cmaa2-ultra+        0.97    24.25    29.91     9.24    11.65     26.74    11.31    12.16     0.97     0.20   0.354   after

```
Reading: cmaa2-ultra at 35 / 50 / 75 %: 3D stairs 8.45 / 10.16 / 12.17 -> 7.55 / 9.42 / 11.65 (own `high`: 6.81 / 9.01 / 12.68), text edge 0.69 / 0.86 / 0.95 -> 0.71 / 0.88 / 0.97 (up), 3dPSNR -0.65 / -0.51 / -0.79 dB, halo unchanged, +0.1 ms. cmaa2-high: 3D stairs 7.98 / 10.01 / 12.76, text up 0.04 / 0.04 / 0.04. smaa-ultra: stairs 8.06 / 9.97 / 11.87 -> 7.01 / 8.79 / 11.65, text edge -0.04 / -0.04 / -0.01 (outside the ~0.02 budget at 35 and 50 %: the cost of the second run; the gate on both runs would keep text at +0.1 but gives back the whole gain), halo 0.28 -> 0.44 at 35 %. smaa-high: 35 % only, text -0.02. cmaa2-high and ultra now differ (3 vs 4 runs at 35 %, 2 vs 3 at 50 %, 1 vs 2 at 75 %); smaa-high and ultra differ at 50 and 75 % (1 vs 2 runs), at 35 % by corner rounding. The lower levels (smaa-low/medium, cmaa2-low/medium) were left (the lead is removing them); smaa-medium now has colour edges.

## Shimmer (2026-11-23, RTX 5060 Ti, GLES; `tests/vm/shimmer.py`, `SHARPEN_SEQ` in sharpen-test)
Scene 640x360 output (foliage noise, hair strands, 1 px lines, text, circles), camera step 0.25 / 0.125 source px per frame, 16 frames, aliased source (point sampled). flick = RMS frame-to-frame change of the error against the ground truth; noise = still scene, +-2 LSB input noise, mean per-pixel std of the output. 65 %, regions foliage / hair / text:

| config | foliage flick | hair flick | text flick | noise (all) |
|---|---|---|---|---|
| bilinear | 4.35 | 24.04 | 23.73 | 0.97 |
| EASU only (RCAS 0, no AA) | 5.00 | 27.17 | 25.29 | 1.26 |
| EASU + RCAS (no AA) | 6.76 | 28.99 | 25.88 | 1.50 |
| default before (CMAA2 + EASU + RCAS) | 6.34 | 26.83 | 25.70 | 1.56 |
| default now (RCAS denoise on) | 6.17 | 26.59 | 25.64 | 1.53 |

At 75 %: foliage 5.65 -> 5.43; at 100 %: 4.81 -> 4.46; 150 % unchanged (the area downscale is stable: flick below bilinear's). CMAA2: deterministic, more runs lower the flicker (0 / 1 / 2 runs: 28.75 / 27.10 / 26.59 on hair), threshold 0.025 / 0.05 / 0.1 and the colour term do not matter; with a box-filtered (already smooth) source it adds 3-5 % flick (hair 14.2 -> 14.8 at 65 %). RCAS denoise strength -1.0 instead of -0.5: foliage 6.17 -> 5.98 (not adopted, off the reference). Quality (tests/vm/sharpen-eval.py, evalalias.py), before -> after: text edge 75 % 14.13 -> 14.10, 65 % 12.98 -> 12.95; photo edge 19.02 -> 18.79 (75 %), 17.65 -> 17.44 (65 %); evalalias 3dStair 65 % 10.51 -> 10.32, 75 % 11.20 -> 11.03, 100 % 14.37 -> 14.11; txtEdge 0.95 / 0.95 / 1.05 unchanged; PSNR within 0.05 dB (100 %: +0.1).

### Shimmer, second round (2026-11-23, same scene, GLES, RTX 5060 Ti): the five ideas
Candidates were built as shader / pass variants with test knobs (not kept), judged on tests/vm/shimmer.py (aliased source, flick = RMS frame-to-frame change of the error; 65 % / 75 %; foliage, hair, text), the quality metrics (sharpen-eval.py text / photo PSNR and edge, evalalias.py txtEdge, 3dPSNR) and, for the temporal one, tests/vm/ghost.py (new: a bar moving 0.25..12 px per frame and a scrolling text list at three contrasts; `trail` = largest error away from the bar, `band` = mean error 2..10 px behind it, `edge/truth` = output edge strength over the truth's). Base = main before this round (foliage 6.17 / 5.43, hair 26.59 / 24.15, text 25.64 / 22.79; sharpen-eval text edge 13.81 / 12.61, photo 16.93 / 15.44, PSNR text 24.20 / 22.77, photo 28.77 / 27.60 at 75 / 65 %; txtEdge 0.95). The ceilings: RCAS off (sharpness 0) foliage 5.30 (-14 %), hair 25.14 (-5 %); EASU replaced by bilinear hair 23.98 (-10 %), foliage 5.34 (-13 %); only history can go below that.

| candidate | foliage flick 65 / 75 % | hair 65 / 75 % | text 65 / 75 % | quality cost | verdict |
|---|---|---|---|---|---|
| a. RCAS denoise -1.0 (reference -0.5) | 5.98 / 5.21 (-3 / -4 %) | 26.34 / 23.92 (-1 %) | -0.2 % | none (txtEdge 0.95, PSNR +-0.01) | no: under 8 % |
| a. denoise noise term on the 3x3 (mean of 8) instead of the cross, -1.0 | 6.02 / 5.25 | 26.35 / 23.94 | -0.3 % | none | no |
| a. max(cross, 3x3), -1.0 | 5.96 / 5.18 (-3 / -5 %) | 26.29 / 23.88 | -0.4 % | none | no |
| b. EASU fades to the bilinear blend where the 4 texels span 0.03..0.12 luma | 5.86 / 5.10 (-5 / -6 %) | 26.56 / 24.13 | 0 | none | no |
| b. same, 0.05..0.2 | 5.51 / 4.74 (-11 / -13 %) | 26.49 / 24.07 (-0.4 %) | -0.1 % | photo edge -5.7 % (15.95 / 14.56), PSNR +-0.02, txtEdge 0.95 | no (see below) |
| b. same, 0.1..0.3 | 5.36 / 4.59 (-13 / -15 %) | 26.29 / 23.90 (-1 %) | -0.2 % | photo edge -7.5 %, photo PSNR -0.08 | no |
| **c. RCAS lobe x (1 - smoothstep(2.5, 4, TV / range)), TV = total variation of the 3x3 (12 neighbour pairs)** | **5.53 / 4.66 (-10 / -14 %)** | **25.46 / 23.05 (-4 / -5 %)** | -1.2 % | text edge -1.5 / -2.0 %, photo edge -6.6 / -6.3 %, PSNR text -0.05 dB, photo -0.12..-0.18 dB, txtEdge 0.94, no cost in ms | **adopted** |
| c. ramp 2..4 / 1.5..3 / 3..6 / 4..8 (65 % only) | 5.42 / 5.30 / 6.05 / 6.16 | 25.35 / 25.14 / 26.31 / 26.57 | | 2..4: text edge -1.8 / -2.2 %, photo edge -7.7 / -7.5 %; 1.5..3 gives the foliage of RCAS off | no (2.5..4 keeps edges better; 3..6 and up do nothing) |
| d. temporal, static gate 3..6 levels, weight 0.5, history clamped to the 3x3 min/max | 6.15 / 5.41 (0) | 25.94 / 23.48 (-2.4 / -2.8 %) | -1.1 % | none; still noise (circles) 1.36 -> 1.01 (-26 %); ghost: none | no (only the noise) |
| **d. same, gate 10..30 levels, weight 0.5** | **5.34 / 4.51 (-13 / -17 %)** | 25.94 / 23.48 (-2.4 / -2.8 %) | -1.2 % | ghost: bar trail unchanged (4 levels), band at 1 px/frame 0.67 -> 0.93; scrolling text edge/truth 0.889 -> 0.889 (contrast 205), 0.78 -> 0.78 (30), 0.85 -> 0.74 / 0.71 (14) | **adopted** (GLES only) |
| d. gate 10..30, weight 0.7 | 5.31 / 4.53 | 25.80 / 23.33 | | foliage err 7.04 -> 7.41 | no |
| d. gate 20..60 / 40..120, weight 0.5 | 5.07 / 4.25, 4.95 / 4.12 (-18 / -20 %) | 25.94 / 23.48, 25.31 / 22.87 (-5 %) | -1.2 % | scrolling text of contrast 30: edge/truth 0.83 -> 0.69 / 0.62 | no: blurs moving low-contrast detail |
| d. no gate, weight 0.25 (history clamped only) | 5.29 / 4.50 (-14 / -17 %) | 21.53 / 19.17 (-19 / -21 %) | -16 / -19 % | scrolling text (contrast 205) edge/truth 0.91 -> 0.77 at 3 px/frame, err up | no: visible smear on anything that moves |
| d. no gate, weight 0.5 | 4.91 / 4.08 (-20 / -25 %) | 17.71 / 15.48 (-33 / -36 %) | -27 / -31 % | scrolling text edge/truth 0.91 -> 0.63, foliage err 7.04 -> 7.36, bar err +28 % at 12 px/frame | no |
| e. EASU result mixed 0.3 / 0.5 with bilinear everywhere | 5.83 / 5.08, 5.63 / 4.87 (-5 / -9 %) | 25.70 / 23.26, 25.15 / 22.72 (-3 / -6 %) | -2.3, -3.7 % | text edge -2.4 / -3.8 %, photo edge -3.3 / -5.3 % (0.3 / 0.5), PSNR text -0.2 / -0.3 dB, txtEdge 0.93 | no |
| e. source blurred 0.15 / 0.3 with its 4 neighbours before EASU (65 and 75 %) | 5.88 / 5.14, 5.70 / 4.93 (-5 / -8 %) | 25.05 / 22.73, 23.50 / 21.30 (-6 / -12 %) | -5, -9 % | text edge 13.81 -> 13.02 / 12.25, txtEdge 0.89 / 0.85 (-0.06 / -0.10), text PSNR -0.26 / -0.63 dB, photo PSNR -0.3 / -0.7 dB | no: costs the text edge |

Combinations (65 / 75 %, flick): base foliage 6.17 / 5.43, hair 26.59 / 24.15; c alone 5.53 / 4.66, 25.46 / 23.05; d alone 5.34 / 4.51, 25.94 / 23.48; **c + d 5.07 / 4.21 (-18 / -22 %), hair 24.88 / 22.44 (-6.4 / -7.1 %), text 25.03 / 22.23 (-2.4 %), lines -0.5 %**; b + c + d 4.93 / 4.10 (b adds 3 % on top of c + d: not worth leaving the reference EASU). Whole picture 21.82 -> 21.12 (-3 %), still-scene noise (all) 1.525 -> 0.891 (-42 %). At 100 %: foliage 4.46 -> 2.38 (-47 %), hair -7.8 %, foliage err 4.94 -> 3.16. At 150 % unchanged (no temporal pass on a downscale: with it the foliage err rose 2.12 -> 2.82 for no flicker gain). Box-filtered source (65 / 75 %): foliage 5.33 / 4.66 -> 4.75 / 3.92 (-11 / -16 %), hair 14.62 / 12.84 -> 13.96 / 12.10 (-4.5 / -5.8 %); there the foliage err rises 6.86 -> 7.56 (the history lags the 0.25 px camera step: a slight blur of low-contrast detail).
Quality of the adopted pipeline, before -> after (75 / 65 %): sharpen-eval text PSNR 24.20 -> 24.15 / 22.77 -> 22.71, text edge 13.81 -> 13.60 / 12.61 -> 12.36, photo PSNR 28.77 -> 28.59 / 27.60 -> 27.48, photo edge 16.93 -> 15.82 / 15.44 -> 14.46; evalalias txtEdge 0.95 -> 0.94 / 0.95 -> 0.94 (100 %: 1.05 -> 1.04), 3dPSNR 30.25 -> 30.28 (75 %), 29.50 -> 29.55 (65 %), 31.71 -> 32.07 (100 %), uiPSNR +0.03 / +0.02 / +0.24, 3dStair 10.32 -> 9.58 (65 %), 11.03 -> 10.32 (75 %), 14.11 -> 12.89 (100 %). sharpen.py passes on GLES and Vulkan, sharpen_parity.py (GLES = Vulkan, one frame) unchanged (max 1 LSB).
Cost (SHARPEN_LOOPS=300, 960x540 -> 1280x720 and 2880x1620 -> 3840x2160): base 0.53 / 3.30 ms per frame; busy RCAS only 0.52 / 3.31 (free); with the temporal pass 0.63 / 4.15 (+0.09 ms at 720p, +0.85 ms at 4K: two copies of the output, one of the source, the blend with 9 + 16 fetches).
What was adopted, where it deviates from AMD: (c) RCAS's lobe is scaled down in busy texture (`BUSY_LO` / `BUSY_HI` in FS_CAS; both backends); (d) a temporal pass after the filter, GLES only (`sharpen_temporal.c`; the Vulkan backend keeps none: it would need two history images, a copy of the source and a fourth pipeline, no trivial change; it also has no CMAA2). EASU is untouched (b not adopted); AMD's reference has neither. Limits: the temporal pass trades a slight blur of moving low-contrast detail (changes under ~20 levels, e.g. a pan over grass with 10-level texture) for calm; a human has to judge whether that reads as ghosting in PGR (docs/PLAN.md). Aliased hair and 1 px lines stay close to the game's own flicker (hair -6 / -7 %): the history that would cure it (weight 0.25..0.5, no gate: -19..-36 %) also smears every moving edge.

## FSR 1 sharpness, third round (2026-11-25, RTX 5060 Ti, GLES; Vulkan parity checked)
Question: more output sharpness / PSNR from EASU and RCAS at render scales 65..100 without more shimmer (> 2 %) or halos, with AMD's ffx_fsr1.h as the baseline. Measured with `tests/vm/evalfilter.py` (AA off: FSR 1 alone; text / 3d / ui scores, step-edge rise), `sharpen-eval.py` (text / photo PSNR and edge), `shimmer.py` (aliased source, flick of foliage / hair / text, default pipeline = CMAA2 + FSR + temporal; 65 / 75 %). Base = main before this round.

Findings on the base: (1) RCAS is nearly neutral on all of these metrics: sharpness 0 -> 1 changes the edge ratio by 0.01 (text 0.85 -> 0.86; photo edge 17.15 -> 17.67 at 75 %), PSNR by +-0.05 dB; the 0.2-stop default (0.87) and "high" (1.0) differ by < 0.2 %. The slider therefore only moves shimmer (noise 0.78 -> 0.86) and halo, not visible sharpness; the loss against the original sits in EASU. (2) A plain Lanczos3 (PIL) on the same box-reduced corpus reaches text edge 0.97 / 3d 1.00 (EASU + RCAS: 0.86 / 0.94 at 65 %) at equal PSNR: EASU is conservative on clean, non-aliased content; its advantage is on aliased sources. The corpus favours linear filters, shimmer.py (aliased) is the counterweight.

| candidate (65 / 75 %) | PSNR text / 3d / ui (dB, base 21.90 / 33.39 / 25.78; 22.89 / 34.85 / 27.24) | photo edge (base 16.32 / 17.58) | shimmer, default pipeline: foliage / hair (base 5.07 / 24.88; 4.21 / 22.44) | verdict |
|---|---|---|---|---|
| RCAS HALO_OV 2 -> 5 / 10 / 25 / 100 % | +-0.01 / -0.03 / -0.06 / -0.20 3d | 16.38 / 16.44 / 16.52 / 16.54 (+0.4..+1.3 %) | +0.3 .. +0.8 % | no: halo@e x1.5..x2.5 for +1 % edge |
| RCAS lobe limit 0.1875 -> 0.3 / 0.5, gain 1.5 / 2 | text +0.03..0.04, 3d -0.3 .. -1.1 dB | 17.91 .. 18.31 (75 %, +2 .. +4 %) | foliage +4 .. +14 %, noise +5 .. +20 % | no: shimmer, 3d PSNR |
| RCAS slider mapping (0 / 0.25 / 0.5 / 0.7 / 1.0) | text 21.86 .. 21.91 | 16.2 .. 16.4 | noise 0.78 .. 0.86 | unchanged; a per-scale default makes no difference to the picture (stops 0.2 vs 0 = 0.2 %) |
| RCAS in x^2 ("linear-ish") space | text -0.02, 3d -0.03 | 16.16 (-1 %) | foliage -0.6 %, hair -0.5 % | no: a wash |
| EASU plain-area lobe 0.5 -> 0.4 everywhere | +0.19 / +0.20 / +0.32 | 17.24 (+5.6 %) | hair +5 %, foliage +1 .. +3 % | no: shimmer |
| EASU edge lobe 0.21 -> 0.15 | +0.11 / -0.01 / +0.15 | 16.82 (+3 %) | hair +2.3 %, foliage +1.9 % | no (3d PSNR flat) |
| EASU edge lobe 0.21 -> 0.30 | -0.18 / -0.28 / -0.23 | 15.79 | hair -1.5 %, foliage -1.8 % | no: softer |
| EASU length shaping len x1.3 / x0.8 | -0.15 .. / +0.11 | 16.55 / 16.52 | foliage +4.9 % / -1.4 % | no: only a sharpness / shimmer trade (the same slope as the lobe) |
| EASU plain lobe 0.5 - D * calm (12-tap TV gate, 2.5..4), D = 0.1 / 0.15 / 0.2 | +0.08 / +0.13 / +0.20 text, ui +0.12 / +0.20 / +0.30 | 16.54 / 16.69 / 16.87 | hair +0.6 / +1.1 / +1.9 %, foliage +0.6 / +0.9 / +1.6 % | works, the gate is too loose |
| gate 3..5, D = 0.2 | +0.31 / +0.18 / +0.45 | 17.54 (+7.5 %) | hair +4.5 %, foliage +3 % | no |
| **D = 0.3, gate 1.5..3.0 (adopted)** | **+0.13 / +0.15 / +0.24; 75 %: +0.24 / +0.15 / +0.50** | 16.42 / 17.69 (+0.6 %) | **foliage +0.2 / +0.2 %, hair +0.4 / +0.3 %, text +0.4 / +0.4 %, noise 0.883 vs 0.891 (-1 %)** | **adopted** |
| D = 0.4 gate 1.5..3.0 | text -0.0, SSIM down (text 0.9068 vs 0.9127) | | | no: over-sharp kernel |
| D = 0.3, gate 1.0..2.5 / D = 0.5, 1.0..2.5 | +0.03 / +0.01 | 16.33 | 0 | too strict: nothing left |

Adopted: EASU's plain-area lobe shrinks from 0.5 by 0.3 where the 12 kernel taps are calm (`CALM_D`, `CALM_LO`, `CALM_HI` in FS_UP; the same total-variation test as RCAS's busy fade, different thresholds). Where the texture is busy the reference kernel stays, so the shimmer cost (hair +0.3 %, foliage +0.2 %, text +0.4 %) is far below the 2 % limit; step-edge rise 2.43 -> 2.24 px (65 %), 2.39 -> 2.12 px (75 %); stem 1 px contrast / width unchanged; PSNR rises 0.13 .. 0.50 dB on text / 3d / UI, SSIM up (text 65 % 0.9127 -> 0.9148, 3d 0.9495 -> 0.9532, ui 0.9376 -> 0.9399). Halo energy (halo@e, % of pixels) rises 0.21 -> 0.24 (text), 0.10 -> 0.19 (3d) at 65 %: still 0.2 % of the pixels, halo% (> 3 levels) 0.006 unchanged in sharpen-eval. sharpen-eval 65 / 75 %: text PSNR 22.89 -> 22.95 / 24.33 -> 24.42, photo 28.23 -> 28.24 / 29.50 -> 29.50, photo edge 16.32 -> 16.42 / 17.58 -> 17.69, text edge 12.71 / 13.89 unchanged. Vulkan: `sharpen_vk_spv.h` regenerated (glslang 16.6), `sharpen_parity.py` max 1 LSB, `sharpen.py` passes on GLES and Vulkan (note: the Vulkan backend only builds with the Vulkan headers: `-Dprebuilts=...`; without them sharpen-test silently runs GLES and the parity test compares GLES with itself). `easu-ref.py` now runs the shader with `CALM_D = 0` (then it equals the reference, max 2 levels at a tie as before).
Not tried: a pre-EASU edge-aware detail restore (it costs a full-resolution pass on the source and every measured sharpening of the kernel behaves like the lobe rows above: sharpness is bought with shimmer, except where the texture is calm, which is what the adopted gate exploits). Limits: the gains are PSNR / SSIM / edge on box-reduced synthetic content (a calm area is sharper only where there is detail to restore); for real aliased game frames the gate keeps the reference kernel in busy areas; a human has to judge PGR. The 1:1 case (evalfilter 100 %) shows EASU itself blurs slightly at ratio 1 (PSNR 35 dB against the original); the pass is not meant to run there.
## AA, third round (2026-11-25, RTX 5060 Ti, GLES; nothing adopted)
Goal: cut the residual stair steps / crawl of hair and 1 px lines on aliased 3D sources without costing text (text edge loss < 2 %) or blurring UI. Yardsticks: `tests/vm/evalalias.py` (aliased scene, 60 % source, columns txtEdge / 3dPSNR / 3dJag / 3dStair / charJag / chStair / charEdge) and `tests/vm/shimmer.py --scales 65 --configs default` (aliased source, flick = crawl, edge = edge strength). Candidates were built with temporary env knobs (not kept).

What the corpus shows first: the nearest-sampled source has already lost the 1 px wires and part of the hair strands (dashed in the source); no AA can bring them back (only history / jitter could). What CMAA2 can do is round the steps of what is left, and it does (3dStair 11.95 -> 9.26, charStair 11.86 -> 9.46 at 60 %), at 3dJag +0.26 and txtEdge -0.06 against FSR alone. Note `Stair` falls with any blur (bilinear 10.5, truth 11.1), so a lower Stair is only a gain when txtEdge and Jag hold.

**A. CMAA2 parameters (evalalias, 60 %; base = shipped: threshold 0.05, colour factor 0.6, 1.4 x ratio runs, max 4)**

| variant | txtEdge | 3dPSNR | 3dJag | 3dStair | charJag | chStair | charEdge |
|---|---|---|---|---|---|---|---|
| FSR alone (AA off) | 0.97 | 28.33 | 12.14 | 11.95 | 15.45 | 11.86 | 0.98 |
| base | 0.91 | 28.28 | 12.40 | 9.26 | 15.54 | 9.46 | 0.92 |
| threshold 0.03 | 0.91 | 28.31 | 12.40 | 9.25 | 15.56 | 9.43 | 0.92 |
| threshold 0.02 | 0.91 | 28.33 | 12.38 | 9.27 | 15.57 | 9.44 | 0.92 |
| colour factor 1.0 | 0.91 | 28.29 | 12.41 | 9.26 | 15.55 | 9.45 | 0.92 |
| colour factor 0 (luma only) | 0.91 | 28.28 | 12.41 | 9.27 | 15.53 | 9.46 | 0.92 |
| runs 2.0 x ratio | 0.89 | 28.04 | 12.82 | 8.78 | 15.93 | 8.99 | 0.90 |
| runs 2.5 x ratio, max 6 | 0.88 | 27.80 | 13.28 | 8.44 | 16.33 | 8.64 | 0.87 |

Threshold and colour factor are saturated on this high-contrast corpus (differences <= 0.03); more runs trade fidelity for smoothness (PSNR -0.2..-0.5 dB, Jag +0.4..0.9, text edge -0.02..-0.03): rejected. Shimmer 65 % (flick / hair edge): base hair 24.88 / 27.64; runs 2.0 24.59 / 25.39; runs 2.8 max 6 24.41 / 23.77 (the 2 % flick gain costs 14 % of the hair's edge strength); threshold 0.025 foliage err 7.10 -> 6.91, flick 5.07 -> 4.99 (noise level); colour factor 1.2 no change.

**B. Edge-directed stair smoother after EASU, before RCAS** (a new GLES pass at output resolution: Sobel direction of the 3x3, average with taps at +-1 and +-2 px along the tangent, clamped to the 3x3 range, faded in with contrast, off on axis-aligned edges, ~0.1 ms at 720p). evalalias 65 %, then shimmer 65 % (hair flick 24.88 base):

| variant | txtEdge | 3dPSNR | 3dJag | 3dStair | charJag | shimmer hair flick / edge | text flick |
|---|---|---|---|---|---|---|---|
| base (CMAA2 only) | 0.94 | 29.55 | 10.35 | 9.58 | 12.47 | 24.88 / 27.64 | 25.03 |
| pass, strength 1.0 (60 % table: txtEdge 0.83, 3dJag 12.89 vs 12.40) | - | - | - | - | - | 23.08 / 23.67 | 24.16 |
| strength 0.5 | 0.89 | 29.41 | 10.60 | 9.00 | 12.75 | 23.75 / 25.46 | 24.36 |
| contrast floor x2.7 | 0.87 | 29.29 | 10.71 | 8.94 | 12.92 | 23.59 / 25.40 | 24.17 |
| axis limit 0.15 | 0.88 | 29.23 | 10.83 | 9.08 | 13.00 | 23.65 / 25.08 | 24.44 |

Hair flick -5..-7 %, but txtEdge -0.05..-0.07 (limit 0.02), 3dJag +0.25..0.5, hair edge strength -8..-14 % and PSNR -0.15..-0.26 dB: it blurs what it should only straighten, and at output resolution the CMAA text gate is not available. Rejected.

Result: no change adopted, CMAA2 ultra and the SMAA fallback stay as they are. Open: a text-safe version of B would need the gate computed at source resolution and handed on (a mask texture), for a measured gain of about 5 % crawl; a real cut of hair/line crawl needs sub-pixel history (the temporal pass's area), not more spatial AA. Not tried: CMAA2 extra edge classes for 1 px lines (the source has already dropped most of them), Vulkan AA port.
### Shimmer, third round (2026-11-25, same scene and tools, GLES, RTX 5060 Ti): no candidate adopted
Tried as shader variants of the temporal pass with test knobs (not kept) and one EASU variant; judged on shimmer.py (65 / 75 %), ghost.py (bar at 1 / 4 / 12 px per frame, scrolling text at contrast 205 and 14, 1 and 3 px per frame). Base (adopted pipeline of round 2): foliage flick 5.07 / 4.21, hair 24.88 / 22.44, text 25.03 / 22.23, lines 36.95 / 33.99, foliage err 7.10 / 5.98; bar band at 1 px/frame 0.93; scroll edge/truth 0.889 / 0.889 (contrast 205), 0.735 / 0.705 (contrast 14).

| candidate | foliage 65 / 75 % | hair 65 / 75 % | text 65 / 75 % | ghost | verdict |
|---|---|---|---|---|---|
| variance clip (YCoCg 3x3 mean +- gamma sigma, clipped along the ray to the history) instead of min/max, gate 10..30, W 0.5, gamma 1.0 / 0.6 | 5.09 / 5.13 | 24.89 / 24.92 | 25.06 / 25.08 | unchanged | no effect: the source gate, not the clamp, limits the blend |
| gate tolerance grows with local range (+0.3 x range) | 5.02 / 4.16, foliage err 7.10 -> 7.38 | 24.89 | 25.03 | unchanged | no (err up) |
| no gate, variance clip gamma 1.0, W 0.4 | 4.88 / 4.00 (-4 / -5 %) | 19.35 / 16.98 (-22 / -24 %) | 20.49 / 17.47 (-18 / -21 %) | scroll edge/truth 0.889 -> 0.734 (contrast 205), 0.735 -> 0.687 (14); bar err at 12 px +15 % | no: smears moving detail (same trade-off as round 2; the clip is only slightly better than min/max at equal flicker) |
| motion gate on the 3x3 luma mean (history vs this frame, divided by local range + floor 8; smoothstep 0.1..0.3) instead of the source gate, W 0.5 | 5.03 / 4.10 | 22.99 / 19.68 (-8 / -12 %) | 24.26 / 20.96 | scroll 0.807 / 0.850, contrast 14: 0.706 / 0.757 | no: moving text edge -9 % |
| same, 0.15..0.4 | 4.94 / 4.04 (-3 %), err +4 % | 21.62 / 18.24 (-13 / -19 %) | 23.43 / 20.05 | scroll 0.773 / 0.824, contrast 14: 0.665 / 0.697 | no |
| mean gate AND source gate (30..100 / 60..160 levels), 0.1..0.3 / 0.15..0.4, floor 3 | 5.09 / 4.15, 4.99 / 4.07 | 24.16 / 21.38 (-3 / -5 %), 23.59 / 20.81 (-5 / -7 %) | 25.04 / 22.22, 24.98 / 22.18 | scroll 0.885 (unchanged), contrast 14: 0.738 / 0.791, 0.699 / 0.748 | no: hair gain under 8 %, foliage err +3 % for the second |
| EASU fades to bilinear where the 4 texels span 0.04..0.12 luma AND the edge confidence `len` is low (below 0.05..0.3, and 0.15..0.5) | 5.08 / 4.21, 5.07 / 4.21 (0) | 24.24 / 21.85, 23.78 / 21.37 (-3 / -5 %) | 24.52, 24.16 (-2 / -3 %) | scroll 0.889 -> 0.855 / 0.838 | no: foliage does not move, text edge -4 / -6 % |

Findings: with the source-change gate the pass is already near the best trade; every variant that cuts hair by more than ~8 % does it by also accumulating over moving edges (scroll edge/truth falls 6 .. 17 %), because a 0.25 px camera step and a 1 px scroll step both change a thin high-contrast line by a large amount, and neither the per-pixel source change nor the 3x3 mean change separates them well enough at the hair's contrast. The variance clip costs 9 extra fetches and, at equal weight, blends as much as min/max (it only matters once the gate is off). A real fix needs motion vectors (the apps' frames come without) or a longer-term history (sign alternation of the residual over 3+ frames: another full-resolution history image), left for later. The adopted pipeline of round 2 is unchanged; no source file was changed.
