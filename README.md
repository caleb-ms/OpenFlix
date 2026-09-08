<div align="center">

# 🎬 OpenFlix

**An offline-first, open-source local media player with a modern streaming aesthetic.**

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](https://opensource.org/licenses/Apache-2.0)
[![Website](https://img.shields.io/badge/Website-openflix.calebms.com-E50914?style=for-the-badge)](https://openflix.calebms.com)

</div>

---

## 📖 Overview

**OpenFlix** transforms raw video files scattered across your device into a polished, streaming-service-grade library. Built natively with **Kotlin** and **Jetpack Compose**, OpenFlix parses your device storage, organizes shows and movies into clean titles, and enriches them with high-resolution posters, synopses, and cast details via **The Movie Database (TMDB)**.

Most importantly, it is **100% offline-first and private**. Your media and watch histories never leave your device.

---

## 📱 Screenshots

<div align="center">
  <img src="https://cdn.calebms.com/openflix/assets/mockup-profiles.jpg" width="22%" alt="Profile Selection" />
  <img src="https://cdn.calebms.com/openflix/assets/mockup-home.jpg" width="22%" alt="Home Screen" />
  <img src="https://cdn.calebms.com/openflix/assets/mockup-detail.jpg" width="22%" alt="Show Details & Episodes" />
</div>

---

## ✨ Key Features

- 👤 **Multi-Profile Support ("Who's Watching?")** – Share one device across family members or housemates. Switch profiles seamlessly with isolated watch states.
- 📦 **Smart Local Media Organization** – Scans chosen folders, parses filenames into grouped seasons and episodes, and eliminates messy folder hierarchies.
- 🎯 **Per-Profile Playback Persistence** – Built on **Android Room**, OpenFlix remembers playback position per profile down to the exact second.
- 🔒 **Zero Telemetry / Privacy-First** – No tracking, no user accounts, and zero proprietary server connections. Everything is stored locally.
- 🔑 **Bring Your Own TMDB Key** – Connect your free TMDB API key to dynamically fetch official cover art, episode synopses, and genre tags.
- 📺 **Fluid Modern Interface** – A familiar, dark-mode streaming UI built entirely with Jetpack Compose.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Local Persistence:** Room Database (Profiles, Watch History, Resume States)
- **Metadata Provider:** The Movie Database (TMDB) API
- **Architecture:** MVVM / Clean Architecture with unidirectional data flow

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug / Iguana or later
- JDK 17+
- Android SDK (API Level 26+ recommended)

### Build from Source

1. Clone the repository:
   git clone https://github.com/caleb-ms/openflix.git
   cd openflix

2. Open in Android Studio:
   - Select File > Open and choose the openflix directory.
   - Allow Gradle to sync project dependencies.

3. Run on Device or Emulator:
   - Select your target device and click Run 'app' (Shift + F10).

---

## ⚙️ Configuration & Setup

1. Launch OpenFlix and select or create your profile.
2. Navigate to the My OpenFlix tab.
3. Under Library, tap Add Media Folder to grant storage permissions to your video directory.
4. (Optional) Under Advanced, paste your free TMDB API Key and tap Sync Now to download posters, episode names, and descriptions.

---

## 📄 License

OpenFlix is distributed under the Apache License 2.0. See the LICENSE file for complete details.

Copyright 2026 Caleb MS Group

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

---

## 🌐 Community & Links

- Official Site: https://openflix.calebms.com
- Source Code: https://github.com/caleb-ms/openflix
