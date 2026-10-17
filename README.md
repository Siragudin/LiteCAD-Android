<div align="center">

# LiteCAD

**A lightweight, offline 2D CAD editor for Android.**
Draft, dimension and annotate technical drawings on the go — then export to PDF or DXF.

![Version](https://img.shields.io/badge/Version-1.0.0-blue?logo=android&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-Android%2010%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![AGP](https://img.shields.io/badge/AGP-9.4.1-02303A?logo=gradle&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-29-3DDC84)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

</div>

---

LiteCAD is a production-ready, hand-held 2D drafting tool built with Kotlin and Jetpack Compose. It is deliberately **small, offline, private, and subscription-free**: no accounts, no analytics, and zero network permissions required. Full architectural and usage details can be inspected in the project files and referenced documentation.

This project is a deep architectural redesign and official mobile port of the desktop version:
👉 **[LiteCAD for Windows (.NET 10 / WPF)](https://github.com)** — Check out the original PC core repository! Projects saved in the `.sit` format can be seamlessly exchanged between the mobile app and the desktop editor.

<p align="center">
  <img src="docs/screen_home.png" width="230" alt="Home Screen">
  <img src="docs/screen_editor_light.png" width="230" alt="Editor Light">
  <img src="docs/screen_editor_dark.png" width="230" alt="Editor Dark">
</p>


## ✨ Features

- **Drawing & Editing Tools** — Comprehensive drafting support including lines, axes, rectangles, circles, sectors, arcs, as well as modifiers like move, rotate, mirror, stretch, offset, extend, and eraser.
- **Precision & Ergonomics** — Features object snapping, Ortho mode, a magnification loupe (`CadMagnifier`), and a dynamic MRU (Most Recently Used) tool overlay for rapid workflows.
- **Annotations & Export** — Linear dimensions, text notes, elevation leaders, and face fills, with direct vector export to PDF and ASCII DXF R12 (`AC1009`).
- **Infrastructure & UI** — Multi-step undo/redo history, spatial grid, local `.sit` project container management, and dark/light theme support.

## 🧱 Tech Stack & Architecture

Built with a **single-activity, 100% Jetpack Compose** architecture using a unidirectional data flow. The lightweight stack includes Kotlin 2.2.10, Navigation Compose 2.8.5, and Gson 2.10.1 for local storage without heavy third-party networking dependencies.

## 🔒 Privacy

- **No Network State** — Zero internet permissions or analytics trackers. All data remains securely on your device.

## 🚀 Getting Started

```bash
git clone https://github.com/<your-username>/LiteCAD.git
cd LiteCAD

# Build and test debug configuration
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## 📜 License

This project is open-source and distributed under the **MIT License**.
