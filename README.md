<div align="center">

# LiteCAD

**A lightweight, offline 2D CAD editor for Android.**
Draft, dimension and annotate technical drawings on the go — then export to PDF or DXF.

![Platform](https://img.shields.io/badge/Platform-Android%2010%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![AGP](https://img.shields.io/badge/AGP-9.4.1-02303A?logo=gradle&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-29-3DDC84)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

</div>

---

LiteCAD is a hand-held 2D drafting tool built with Kotlin and Jetpack Compose. It is the
**official mobile port** of the desktop **LiteCAD for Windows** (.NET / WPF) and shares its
`.sit` project format, so drawings move freely between phone and PC.

It is deliberately **small, offline and subscription-free**: no account, no analytics, no
network permission. Everything is rendered with Compose `Canvas` and stored as a portable,
versioned project file.

<!-- Add screenshots here, e.g.
<p align="center">
  <img src="docs/screen_home.png" width="200">
  <img src="docs/screen_editor.png" width="200">
  <img src="docs/screen_help.png" width="200">
</p>
-->

## ✨ Features

- **Drawing tools** — line, axis (construction line), rectangle, circle, sector, arc.
- **Editing tools** — select (tap & marquee), move, rotate, mirror, stretch, offset
  (equidistant), extend, eraser.
- **Annotation** — linear dimensions (offset & rounding), text notes (size / color /
  rotation), leaders (elevation marks), face fill.
- **Precision** — real-time snapping (endpoint, midpoint, intersection, axis intersection,
  on-edge, alignment), Ortho mode, and exact numeric input for length, radius, angle, size
  and distance.
- **History** — multi-step undo / redo, toggleable grid.
- **Projects** — save / load in the `.sit` format, autosave, thumbnails on the home screen,
  share and import via the system file picker.
- **Export** — PDF (vector, via `android.graphics.pdf`) and DXF R12 (`AC1009`) ASCII.
- **In-app Help** — a built-in "Tool reference" screen with an expandable (accordion)
  guide for every tool, designed as onboarding for new users.
- **Magnifier lens** — an overlay loupe that enlarges the area right under your finger for
  pixel-accurate placement on a touch screen.
- **Engineering-grade engine** — automatic vertex welding, edge splitting, collinear-overlap
  suppression and dynamic closed-loop face rebuilding; PDF output follows ESKD/GOST
  lineweights.
- **Look & feel** — dark / light editor theme, Russian & English localization, touch-first UI.

## 🧱 Tech Stack

| Layer | Technology | Version |
| --- | --- | --- |
| Language | Kotlin | 2.2.10 |
| Build | Android Gradle Plugin | 9.4.1 |
| Build | Gradle (wrapper) | 9.6 |
| UI | Jetpack Compose (BOM) | 2026.02.01 |
| UI | Material 3 | via Compose BOM |
| UI | Material Icons (Extended) | 1.7.5 |
| Navigation | Navigation Compose | 2.8.5 |
| Images | Coil (`coil-compose`) | 2.7.0 |
| Serialization | Gson | 2.10.1 |
| AndroidX | Core KTX | 1.19.1 |
| AndroidX | AppCompat | 1.7.0 |
| AndroidX | Activity Compose | 1.13.0 |
| AndroidX | Lifecycle Runtime KTX | 2.11.0 |
| Testing | JUnit / AndroidX Test / Espresso | 4.13.2 / 1.3.0 / 3.7.0 |

| SDK | Value |
| --- | --- |
| `compileSdk` | 37 |
| `targetSdk` | 37 |
| `minSdk` | 29 (Android 10) |
| Java source/target | 11 |

> No third-party architecture or networking frameworks are used on purpose — no Hilt, no
> Room, no Retrofit, no ViewModel. The app is intentionally dependency-light.

## 🏗 Architecture

A **single-activity, 100% Jetpack Compose** app with a **unidirectional data flow**:

```
Gestures  ──▶  ToolManager (active Tool handler)  ──▶  CadDocument mutation
                    │                                        │
                    ▼                                        ▼
             ToolSettings / Viewport              DocumentHistoryService (undo/redo snapshot)
                                                             │
                                                             ▼
                                          Compose re-composition (revision counter)
```

- **UI layer** (`ui/`) — screens (`HomeScreen`, `EditorScreen`, `HelpScreen`,
  `SettingsScreen`…), the custom drawing surface (`ui/canvas`), theme and vector icons.
  Navigation is wired with **Navigation Compose** (`NavHost` + `composable` routes).
- **State holders** — `ToolManager` owns the active tool and the pending-input state;
  `Viewport`/`Camera` hold the pan/zoom transform; `ToolSettings` holds per-tool options.
- **Services** (`services/`) — `SnapService` (snapping), `DocumentHistoryService`
  (undo/redo), `EditService`, selection picking, spatial grid.
- **Geometry** (`geometry/`, `leaders/`, `math/`) — pure, framework-free algorithms
  (topology, polygons, dimensions, arcs, containment).
- **Persistence** (`storage/`) — Gson-based serialization with a dedicated **DTO layer**
  separate from the domain model, wrapped in the `.sit` container.
- **Settings** (`settings/`) — app-level prefs (theme, language, autosave) in
  `SharedPreferences`.

### Rendering

Rendering is done entirely with **Jetpack Compose `Canvas` + `DrawScope`** (no OpenGL, no
custom `View`). A `CadRenderer` draws edges, axes, polygons, texts, dimensions and leaders,
while a `Camera`/`Viewport` pair converts between world coordinates (mm) and screen
coordinates. Text is measured with Compose's `TextMeasurer`.

### Data & persistence

Everything is stored **locally** under `filesDir/LiteCAD/Projects`. The domain model is
`CadDocument` (vertices, edges, axes, polygons, dimensions, texts, leaders, fill styles),
which is serialized to a DTO graph and written as JSON inside the `.sit` archive. A
`revision` counter on the document drives recomposition.

### 📦 Project format (`.sit`)

A `.sit` file is a plain **ZIP archive** — portable and easy to inspect or parse on any
platform:

```
project.sit
├── project.json    # the drawing (versioned schema, formatVersion = 1)
├── preview.png     # thumbnail shown on the home screen
└── manifest.json   # format = "LiteCad", schema version, project name, timestamp
```

Because the payload is human-readable JSON with a versioned header, the same file can be
exchanged with the **PC/desktop version of LiteCAD** and external tooling.

## 🔒 Privacy

- **No** `INTERNET` permission — the app cannot access the network.
- No ads, no trackers, no accounts.
- All data lives on the device; uninstalling removes it.

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (latest stable) with support for AGP 9.x.
- **JDK 17+** — a compatible JDK is bundled with recent Android Studio.
- **Android SDK Platform 37** + Build-Tools installed via the SDK Manager.
- The **Gradle wrapper** is committed, so no local Gradle install is needed.

### Build & run

```bash
git clone https://github.com/<your-username>/LiteCAD.git
cd LiteCAD

# Build a debug APK
./gradlew assembleDebug

# Install on a connected device / emulator
./gradlew installDebug

# Run the unit tests
./gradlew testDebugUnitTest
```

> On Windows use `gradlew.bat` instead of `./gradlew`.
> In Android Studio you can simply open the project and press **Run**.

The `release` build type enables R8 optimization and scoped repackaging.

## 📁 Project structure

```
app/src/main/java/com/sit/litecad/
├── MainActivity.kt          # single activity + NavHost
├── ui/
│   ├── canvas/              # Compose Canvas surface, renderer, camera/viewport
│   ├── screens/             # Home, Editor, Help, Settings, About, panels
│   ├── theme/               # colors, typography, theme
│   └── icons/               # custom vector icons
├── tools/                   # Tool interface + one handler per tool, ToolManager
├── services/                # snapping, history (undo/redo), editing, selection
├── geometry/                # topology, polygons, dimensions, arcs
├── leaders/                 # leader / elevation geometry
├── math/                    # Point2, Vector2, 2D geometry helpers
├── model/                   # CadDocument and element models
├── storage/                 # .sit read/write (Gson + DTO layer)
├── pdf/                     # PDF export
├── dxf/                     # DXF R12 export
└── settings/                # SharedPreferences (theme, language, autosave)
```

## 🗺 Roadmap

- Dedicated **tablet / landscape layout** (tool panels moved to the screen sides to maximize
  the drafting canvas).
- Tighter round-trip interoperability with the desktop build via the `.sit` format.
- Expanded DXF interoperability.

## 📄 License

Released under the **MIT License**. See [LICENSE](LICENSE) for details.

```
Copyright (c) 2026 Siragudin Guseynov
```
