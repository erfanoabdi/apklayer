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

Options: `--filter label[:ENV=VAL,...]` (repeatable; the environment of the pass: `APKLAYER_SHARPEN_AA` off|low|high (the `render-aa` knob),
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

## Baseline (2026-11-23, the shell's default pipeline: EASU + RCAS 0.2 stops with the text aid + AA high, GLES = Vulkan, RTX 5060 Ti)

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
