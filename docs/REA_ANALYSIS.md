# Freenotes 3.29.0 — REA Reverse Engineering Analysis

This document summarizes the static analysis performed on `Freenotes 3.29.0.apk` (`ai.note.notepad.memo.journal.freenote`, `versionCode: 275`, `minSdkVersion: 29`) using **REA (`morluto/rea`)** and ELF/APK inspection to inform our clean-room native Android tablet recreation.

## 1. Package & Distribution Architecture

- **Format**: Split APK bundle (`base.apk`, `split_config.arm64_v8a.apk`, `split_extra_asset_pack.apk`).
- **Protection**: `classes.dex` (96 KB) is a stub loader paired with `libnesec.so` / `assets/nedata.db` (runtime DEX packer). All core native rendering libraries, stroke fixtures, prediction parameters, templates, and 976 XML UI layouts remain statically inspectable.

## 2. Core Native Subsystems (from `split_config.arm64_v8a.apk`)

| Native Library | Observed Role / Exported Symbols | Clean-Room Equivalent in This Repo |
| --- | --- | --- |
| `libclipper2.so` | `com.wsc.notepadRender.render.maskPath.base.Clipper2NativeLib` & `Clipper2CriticalNative` — stroke point lists (`locationx`, `locationy`, `sizewidth`, `power`, `timeOffset`), pencil linear vertices, dashed/dotted highlighter vertices, fill-shape vertices, `HardwareBuffer` rendering | [`StrokeGeometry.kt`](../app/src/main/java/com/johnlrt/freenotes/engine/StrokeGeometry.kt) & [`HandwritingCanvas.kt`](../app/src/main/java/com/johnlrt/freenotes/ui/editor/HandwritingCanvas.kt) |
| `libforecast.so` & `libtrack_prediction.so` | Stylus touch prediction (`com.oplus.forecast.NativeForecast`, `com.vivo.trackpredictor.core.TrackPredCore`) tuned via `assets/optparam1130_best.cfg` (240Hz TurboTouch + **One-Euro Filter**: `mincutoff=16`, `beta=0.01`, `freq=120`) | [`OneEuroFilter.kt`](../app/src/main/java/com/johnlrt/freenotes/engine/OneEuroFilter.kt) & [`StylusPredictor.kt`](../app/src/main/java/com/johnlrt/freenotes/engine/StylusPredictor.kt) |
| `libgraphics-core.so` | AndroidX `graphics-core` low-latency rendering | Jetpack Compose Canvas + `MotionEvent` historical batching & `input-motionprediction` |
| `libpdfium.so` / `libpdfiumandroid.so` | PDF viewing, page rendering, and export | Android native `PdfRenderer` & `PdfDocument` in [`PdfEngine.kt`](../app/src/main/java/com/johnlrt/freenotes/data/PdfEngine.kt) |
| `libmp3lame.so` | Voice note audio recording | [`AudioRecorderManager.kt`](../app/src/main/java/com/johnlrt/freenotes/data/AudioRecorderManager.kt) |
| `libdigitalink.so` & `libtextsegmenterlib.so` | Handwriting recognition & text segmentation | Extensible stroke model preserving `(x, y, pressure, timeOffset)` |

## 3. Stroke Prediction & Smoothing Parameters (`assets/optparam1130_best.cfg`)

Directly observed in `base.apk`:
```ini
processor: one-euro-vector:
one-euro-mincutoff: 16
one-euro-beta: 0.01
one-euro-freq: 120
processor-enable: true
```
And stroke test fixtures in `assets/ipad_stroke_fixtures/{pointy,round,round_pressure_range}/*.csv` confirm the per-sample schema: `(phase, timestamp, force, location_x, location_y)`.

## 4. UI & Feature Blueprint (from 976 `res/layout/` files & `assets/template/`)

- **Paper Templates**: 3 paper colors (`white`, `yellow`/cream, `black`/dark) × 2 orientations (`Vertical`, `Horizontal`) across Blank, Ruled, Grid, Dotted, Cornell, Planner, and Music Staff layouts.
- **Editor & Toolbars**:
  - Primary toolbar (`fragment_toolbar.xml`) + Movable Favorites toolbar (`fragment_toolbar_sticky.xml`)
  - Layer management (`fragment_layer_setting.xml`)
  - Page sorting & multi-page overview (`fragment_sort_page.xml`, `fragment_multi_page.xml`)
  - Zoom lock (`fl_zoom_lock.xml`) & Canvas orientation (`fragment_canvas_orientation.xml`)
  - Active Recall Tape tool, Lasso selection, Shape snapping, Text boxes, Images/Stickers, Laser pointer
  - Voice Notes (`activity_fd_voice_note.xml`) & Nested Folder File Manager (`activity_file_manager.xml`)
