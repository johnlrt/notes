# FreeNotes Tablet (Android — Kotlin + Jetpack Compose + Stylus)

A clean-room native Android tablet note-taking & PDF annotation app recreated from our **REA (`morluto/rea`)** static analysis of `Freenotes 3.29.0.apk`.

See [`docs/REA_ANALYSIS.md`](docs/REA_ANALYSIS.md) for the full reverse-engineering breakdown of native libraries, One-Euro stabilization parameters (`optparam1130_best.cfg`), stroke geometry, and tablet UI layouts.

## Key Features (Full Freenotes Parity)

- **Low-Latency Stylus & Polygonal Ink Ribbon Engine**
  - Raw `MotionEvent` historical batching (120Hz/240Hz stylus fidelity)
  - **Closed Polygonal Ink Ribbons** (`StrokeGeometry.buildRibbonOutline`) with midpoint smoothing and rounded start/end caps (eliminates segment joint artifacts)
  - **1€ One-Euro Filter** (`mincutoff = 16`, `beta = 0.01`, configurable stability slider) + speculative velocity/acceleration tip prediction (`StylusPredictor`)
  - Hardware **Palm Rejection** (Stylus-only mode lets fingers smoothly pan & zoom) & **S Pen / Stylus side-button** (`BUTTON_STYLUS_PRIMARY`) temporary eraser shortcut
- **Writing, Drawing & Precision Tools**
  - Custom realistic vector nib icons tinted to active ink color (`FreenotesIcons`)
  - **Fountain Pen** (pressure & speed-sensitive tapered nib), **Ballpoint**, **Pencil** (grainy alpha), **Art Pen** (nib angle & calligraphy contrast)
  - Behind-Ink **Highlighter** (Solid, Dashed, Dotted + Straight-Line snap)
  - **Eraser** (Whole-Stroke & Pixel/Split stroke modes + Erase Highlighter Only)
  - **Interactive HSV Color Wheel** + Brightness slider + Hex input + Saved Custom Swatches
  - **On-Canvas Magnetic Transparent Ruler** (cm/mm ticks, drag/rotate, snaps stylus strokes cleanly to ruler edge)
  - **Zoom Writing Window** (bottom magnifier panel with green Auto-Advance margin zone + `←`, `→`, `↵` controls)
  - **Shape Auto-Snap** (Line, Rectangle, Ellipse, Triangle, Arrow) with **Fill Color** and **Dashed Stroke** options
  - **Lasso Tool** (freehand polygon selection: drag to move, recolor, duplicate, delete, and **ML Kit Handwriting-to-Text OCR**)
  - **Fading Laser Pointer**, **Text Boxes**, **Stickers Pack**, and **Clickable Internal/Web Hyperlinks**
  - **Gallery Image Import & Cropper** (Rectangular crop + Freehand Lasso polygon mask crop to transparent PNG)
- **Study & Productivity Suite**
  - **Active Recall Tape Tool** (draw tape over notes, tap to reveal/cover, plus one-tap Reveal/Cover All)
  - **SM-2 Spaced-Repetition Flashcards** (create cards, flip to reveal answer, rate Again/Hard/Good/Easy)
  - **Floating PiP Lecture Video Player** (draggable overlay with Play/Pause and **Capture Frame** directly onto the note canvas)
  - **Outline, Bookmarks & In-Notebook Search Panel** (jump to bookmarked pages or search text/hyperlinks/bookmarks)
  - **Voice Notes** (record lectures while writing + playback with ±10s seek & 0.75x–2.0x speed)
  - **Pomodoro Focus Timer**
- **Tablet Workspace, Paper & Export**
  - **Continuous Vertical Multi-Page Scroll** + **Horizontal Paging** toggle
  - Multi-tab open notebooks & **Split-Screen Dual-Page** study view
  - Movable **Floating Favorites Toolbar** (`StickyToolbar`) & **Layers Panel** (add, show/hide, lock/unlock, delete)
  - **Paper Templates** (Blank, Ruled, Wide Ruled, Grid, Dotted, Cornell, To-Do Planner, Music Staff) in White, Cream, and Dark paper × Portrait/Landscape
  - **PDF Import**, **Dark-Mode PDF Color Inversion**, **Annotated PDF Export**, **Page PNG Export**, and **Full Notebook JSON Backup / Restore**
  - Nested Folder Library with search, favorites, and Freenotes Cobalt-Blue (`#2563EB`) / Alabaster / Obsidian Material 3 theme

## Build & Run

Open the project in **Android Studio Ladybug+** (JDK 17, Android SDK 35, `minSdk 29`) or build via Gradle:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

GitHub Actions (`.github/workflows/android-ci.yml`) automatically runs unit tests and publishes `freenotes-tablet-debug-apk` on every push.