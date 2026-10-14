# LiteCAD for Android 📱📐

<p align="center">
  <img src="https://shields.io" alt="Android Platform">
  <img src="https://shields.io" alt="Current Version">
  <img src="https://shields.io" alt="License">
</p>

**LiteCAD for Android** is a lightweight mobile 2D CAD application designed for precise technical drawing, planning, and drafting directly on smartphones and tablets. 

This project is a deep architectural redesign and official mobile port of the desktop version [LiteCAD for Windows (.NET 10 / WPF)](https://github.com). The graphics engine has been completely rewritten from scratch in **Kotlin**, utilizing a highly optimized custom **Android Canvas** to deliver buttery-smooth panning, zooming, and strict adherence to ESKD/GOST engineering drawing standards.

---

## 📜 License

This project is open-source and distributed under the **MIT License**. Feel free to use, modify, distribute, or build upon this codebase for both personal and commercial applications. Freedom to create!

---

## 🔥 Key Architectural Features

Unlike basic mobile sketching apps, LiteCAD-Android is built on a legitimate engineering topology framework:
* **Topology Engine:** Features automatic close-vertex welding, edge splitting at intersections, collinear overlap suppression, and dynamic closed-loop face rebuilding (`PolygonBuilder` + `syncFaces`).
* **Advanced Snap System:** Instant, real-time snapping to critical geometry: *Endpoint, Midpoint, Intersection, AxisIntersection, OnEdge, and Alignment*.
* **Precision Constraints:** Full orthogonal drafting support (**Ortho**), live floating numeric input panels, and instant active-tool measurement overlays (*L / W×H / R / H / θ / Δ*).
* **Target Lens (`CadMagnifier`):** A custom overlay loupe UI element that magnifies the drawing region directly beneath the user's finger, enabling perfect positioning despite touch screen limitations.

---

## 🛠️ Current Feature Set (v0.6.7)

### 📐 Drafting Tools
* **Line** — Full Ortho constraint support, precise input, and vertex snapping.
* **Axis** — An independent system primitive rendered as a standardized chain-dot (dash-dot) line.
* **Rectangle** — Generates exact geometries via precise dimensional input (Width × Height).
* **Circle** — True geometric circle layout approximated with a smooth 36-segment loop and precise radius input.
* **Sector** — Two-stage drafting utilizing active radius and angle drag tracking.
* **Arc** — True three-point calculation passing through start, end, and sagitta (deflection depth).

### ✏️ Modifiers & Editing
* **Move** — Structural shifting of selected vertices and edges, featuring an interactive **"Copy"** toggle.
* **Rotate** — Dual-phase rotation with 90° Ortho-snapping and exact numeric angular input.
* **Mirror** — Reflection of selected components across any user-defined reflection axis.
* **Offset (Equidistant)** — Shifts a chosen edge outwards while automatically computing intersection paths along adjacent vectors.
* **Extend** — Smart edge projection stretching vectors to the nearest boundary intersection, utilizing visual error flashes for invalid paths.
* **Eraser** — Fast geometry purging that automatically suppresses and updates corresponding boundary faces.

### 📝 Annotations & Dimensioning
* **Dimension** — Linear drafting dimension strings complete with proper end ticks and dynamic value texts.
* **Leader** — Two-stage elevation indicators featuring standalone color properties and full Undo/Eraser pipeline integration.

### 💼 Infrastructure & UI Flow
* **Undo/Redo Stack** — Deep chronological state history tracking up to 10 operations backward using `DocumentSnapshot`.
* **Native File Storage:** Packaged under a compact `.sit` file extension (a secure ZIP container housing `project.json` and a rendered `preview.png` for screen caching).
* **PDF Export Engine:** Automated blueprint scaling to fit A4 sheets, automated page orientation detection, and **hardcoded physical lineweights per ESKD standards** (Edge: 0.18mm, Axis: 0.125mm, Dimension: 0.065mm). Outputs clean files directly to the system *Downloads* directory via MediaStore.

---

## 🚀 Roadmap to v1.0.0

- [ ] Core integration of solid face filling (`faceFillStyles`) on the active `CadCanvas`.
- [ ] Implementation of a lightweight, highly compatible **DXF Exporter** (ensuring cross-compatibility with AutoCAD, AutoCAD clones, and the Windows version of LiteCAD).
- [ ] Integration of adaptive tool rounding (1cm / 10cm / 1m snapping steps) for frictionless finger-drafting without typing.
- [ ] Official deployment of the stable production build to the **RuStore** application marketplace.

---

## 💻 Tech Stack & Development Environment

* **Language:** 100% Kotlin
* **UI & Graphics:** Android Custom Views, Canvas API, Android Jetpack
* **Data Layer:** GSON / JSON serialization
* **IDE:** Android Studio
* **Build Automation:** Gradle (Optimized for independent `./gradlew app:assembleDebug` builds)

### Development Standards:
* **UI & Core Architecture Documentation:** Russian (Русский)
* **Commit Messages & Logs:** English (Английский)
* **Git Pipeline:** High-discipline feature branches with atomic single-commit merges.

---

**Developed with ❤️ by Siragudin Guseynov.**  
*If you find this project interesting or want to support its mobile development journey, please consider giving a ⭐ to the original [Windows Desktop Repository](https://github.com)!*
