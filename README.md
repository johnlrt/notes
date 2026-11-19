# FreeNotes Tablet (Android — Kotlin + Jetpack Compose + Stylus)

A clean-room native Android tablet note-taking & PDF annotation app recreated from our **REA (`morluto/rea`)** static analysis of `Freenotes 3.29.0.apk`.

See [`docs/REA_ANALYSIS.md`](docs/REA_ANALYSIS.md) for the full reverse-engineering breakdown of native libraries, One-Euro stabilization parameters (`optparam1130_best.cfg`), stroke geometry, and tablet UI layouts.

## Key Features

- **Low-Latency Stylus Engine**
  - Raw `MotionEvent` historical batching (120Hz/240Hz stylus fidelity)
  - **1€ One-Euro Filter** (`mincutoff = 16`, `beta = 0.01`, configurable stability slider)
  - Speculative velocity/acceleration tip prediction (`StylusPredictor`)
  - Hardware **Palm Rejection** (Stylus-only mode lets fingers smoothly pan & zoom)
  - **S Pen / Stylus side-button** (`BUTTON_STYLUS_PRIMARY`) temporary eraser shortcut
- **Writing & Study Tools**
  - Fountain Pen (pressure & speed-sensitive tapered nib), Ballpoint, Pencil, Art Pen (nib angle & contrast)
  - Behind-Ink **Highlighter** (Solid, Dashed, Dotted + Straight-Line snap)
  - **Eraser** (Whole-Stroke & Pixel/Split stroke modes + Erase Highlighter Only)
  - **Active Recall Tape Tool** (draw tape over notes, tap to reveal/cover, plus one-tap Reveal/Cover All)
  - **Lasso Tool** (freehand polygon selection: drag to move, recolor, duplicate, delete)
  - **Shape Auto-Snap** (Line, Rectangle, Ellipse, Triangle, Arrow), **Text Boxes**, **Fading Laser Pointer**
- **Tablet Workspace**
  - Multi-tab open notebooks & Split-Screen Dual-Page study view
  - Movable **Floating Favorites Toolbar** (`StickyToolbar`)
  - **Layers Panel** (add, show/hide, lock/unlock, delete)
  - **Paper Templates** (Blank, Ruled, Wide Ruled, Grid, Dotted, Cornell, To-Do Planner, Music Staff) in White, Cream, and Dark paper × Portrait/Landscape
  - **PDF Import & Annotated PDF Export** (native `PdfRenderer` & `PdfDocument`)
  - **Voice Notes** (record lectures while writing + playback with ±10s seek & 0.75x–2.0x speed)
  - **Pomodoro Focus Timer** & **Nested Folder Library**

## Build & Run

Open the project in **Android Studio Ladybug+** (JDK 17, Android SDK 35, `minSdk 29`) or build via Gradle:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

GitHub Actions (`.github/workflows/android-ci.yml`) automatically runs unit tests and publishes `freenotes-tablet-debug-apk` on every push.