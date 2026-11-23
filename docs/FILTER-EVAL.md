# Render filter yardstick (`tests/vm/evalfilter.py`)

One command measures the whole render filter (FSR 1 with its text aid and the AA pass, `bridge/shell/sharpen*.c`) against bilinear on a generated corpus. Every
filter change (EASU/RCAS, AA, text aid, shader edits) is judged with it: run before, run after, quote the table. This is the yardstick; `tests/vm/sharpen-eval.py`
(two pictures, PSNR/SSIM/edge/steepness/halo, `--env` for the knobs) stays as the small quick check and for the numbers older changelog entries quote.

    ninja -C b bridge/shell/sharpen-test                 # once (or set SHARPEN_TEST=<binary>)
    python3 tests/vm/evalfilter.py                        # 35/50/75 %, bilinear vs the current default pipeline ("fsr1"), ~3 s
    python3 tests/vm/evalfilter.py --json before.json     # ... change the shader, rebuild ...
    python3 tests/vm/evalfilter.py --compare before.json  # mean differences current - saved
    python3 tests/vm/evalfilter.py --filter 'fsr1-noaa:APKLAYER_SHARPEN_AA=off' --filter 'notext:APKLAYER_SHARPEN_TEXT=off' --scales 50 --only text_small,text_large
    python3 tests/vm/evalfilter.py --save /tmp/x          # ref.png and every output as PNG, to look at

Options: `--filter label[:ENV=VAL,...]` (repeatable; the environment of the pass: `APKLAYER_SHARPEN_AA` off|smaa-<level>|cmaa2-<level> (the `render-aa` knob; default cmaa2-ultra; the old own levels `low` and `high` were removed on 2026-11-23 and read as cmaa2-low and cmaa2-high),
`APKLAYER_SHARPEN_TEXT` off|on (`render-text`), `_CALM`, `_BACKEND` gles|vulkan), `--sharp` (the RCAS constant exp2(-stops): 0.8706 = the default 0.2 stops), `--scales`, `--only`, `--check` (exit 1 if the first
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
EASU/RCAS port, the text aid or the AA pass is measured by.

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
