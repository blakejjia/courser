# Courser 🎓

<p align="center">
  <!-- PLACEHOLDER: App Logo / Banner (e.g. assets/courser_banner.png or assets/PixelPlayer.svg) -->
  <img src="assets/PixelPlayer.svg" alt="Courser Logo" height="180"/>
</p>

<p align="center">
  <strong>The Distraction-Free Course & Lecture Audio Player for Android</strong><br>
  Engineered for structured listening, deep study, and seamless Google Drive cloud synchronization.<br>
  Built 100% with modern Kotlin, Jetpack Compose, and Material Design 3 Expressive.
</p>

<p align="center">
  <a href="https://github.com/blakejjia/PixelPlayer-gcloud/releases/latest">
    <img src="https://img.shields.io/github/v/release/blakejjia/PixelPlayer-gcloud?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest Release">
  </a>
  <img src="https://img.shields.io/badge/Android-11%2B%20(API%2030--37)-green?style=for-the-badge&logo=android" alt="Android 11+">
  <img src="https://img.shields.io/badge/Kotlin-2.4.0%20%7C%20JVM%2021-purple?style=for-the-badge&logo=kotlin" alt="Kotlin 100%">
  <img src="https://img.shields.io/badge/License-Proprietary%20%2F%20MIT%20Base-blue?style=for-the-badge" alt="License">
</p>

---

## 📸 Screenshots & Showcase

<p align="center">
  <!-- PLACEHOLDER: Screenshot 1 - Home Screen & Today's Learn -->
  <img src="assets/screenshot1.jpg" alt="Today's Learn & Course Shelf" width="22%" style="border-radius:18px; margin: 4px;"/>
  <!-- PLACEHOLDER: Screenshot 2 - Course Detail & Lesson List -->
  <img src="assets/screenshot2.jpg" alt="Course Detail & Progress" width="22%" style="border-radius:18px; margin: 4px;"/>
  <!-- PLACEHOLDER: Screenshot 3 - Modern Player Screen -->
  <img src="assets/screenshot3.jpg" alt="Player Screen & Seeking" width="22%" style="border-radius:18px; margin: 4px;"/>
  <!-- PLACEHOLDER: Screenshot 4 - Library & Search -->
  <img src="assets/screenshot4.jpg" alt="Course Library & Search" width="22%" style="border-radius:18px; margin: 4px;"/>
</p>

> _(Screenshots above reflect current design. Replace with your updated Courser high-res captures in `assets/`.)_

---

## 💡 Why Courser? (How We Differ from Traditional Music Players)

While general music players (like standard PixelPlayer) are built around tracks, albums, and artists, **Courser is purpose-built for learners, students, and audiobook enthusiasts**.

| Dimension | Traditional Music Player (PixelPlayer) | Courser 🎓 |
|---|---|---|
| **Core Architecture** | Artist / Album / Song music hierarchy | **Course-Centric Hierarchy**: 1 selected folder = 1 course (recursive multi-directory indexing) |
| **Daily Habit Tracker** | Daily Mix playlist | **Today's Learn**: Dedicated daily learning journey highlighting enrolled courses |
| **Cloud Integration** | Commercial music platforms | **Google Drive Direct Sync**: Native differential directory sync & streaming proxy |
| **Progress Persistence**| Simple track position | **Smart Course Progress**: Continuous listening progress, 90% auto-complete threshold, and instant resume |
| **Offline Learning** | Manual per-song download | **One-Click Course Download**: Download all lessons of an entire course for full offline study |
| **Interface & Navigation** | 5+ tabs music clutter (Songs, Albums, Artists, Genres) | **Distraction-Free 3-Tab UI**: `Home`, `Library`, and `Settings` |
| **AI Assistant** | AI playlist generation | **AI Course Refinement**: AI Title Cleaning & deterministic course lesson reordering |

---

## ✨ Key Features

### 📚 Course-Centric Organization
* **1 Folder = 1 Course**: Point Courser to any folder on local storage or Google Drive; all audio tracks across subdirectories are recursively indexed into a unified course syllabus.
* **Today's Learn Hub**: Jump straight back into your ongoing courses without digging through file trees.
* **Unified Course Library**: Fast full-text search across all your local and cloud-synced courses with real-time filtering.

### ☁️ Deep Google Drive Integration
* **Differential Cloud Sync**: Automatically tracks folder additions, removals, and modifications without re-downloading entire catalogs.
* **Embedded Ktor Streaming Proxy**: Exposes a secure local loopback proxy (`127.0.0.1`) that translates Google Drive chunked streams into standard, seekable `Range: bytes=...` streams for ExoPlayer.
* **Course Download Manager**: Download whole courses locally with resilient multi-threaded queuing and transparent offline playback bypass.

### 🎧 Pro-Grade Audiophile Engine
* **DualPlayerEngine**: Employs master + auxiliary dual `ExoPlayer` instances for true gapless playback, intelligent pre-buffering, and buttery-smooth crossfade transitions.
* **Precise Seeking & Resume**: Remembers the exact millisecond where you stopped listening across every course.
* **Speed & Pitch Controls**: Smooth variable speed playback (0.5x – 3.0x) with pitch correction for efficient lecture review.
* **Hardware Decoder Policy**: Automatic vendor HAL bug workarounds and direct audio offload tuning for maximum battery efficiency.

### 🎨 Material Design 3 Expressive UI
* **Dynamic Material You**: Colors adapt harmoniously to your course cover artwork or system wallpaper via Android's `Palette` API.
* **Floating Mini-Player Insets**: Powered by `CourserLazyColumn`, ensuring floating player sheets and bottom bars never obscure course items.
* **Home Screen Widgets**: AndroidX Glance interactive widgets for one-tap course resumption right from your launcher.

### 🤖 AI Learning Enhancements
* **AI Title Cleaning**: Automatically cleans messy file names (e.g., `[2024-Lecture-01]_Intro_v2.mp3` → `01. Introduction`) using Gemini / Groq / OpenAI LLM providers.
* **Intelligent Lesson Reordering**: Restores logical pedagogical sequence even when track tags or file name prefixes are inconsistent.

---

## 🛠️ Technology Stack

```
Courser Architecture
├── :app                 # Main application (Compose UI, Services, Room DB, Cloud Proxy)
├── :shared              # Shared serialization models & protocols (Phone ↔ Wear)
├── :wear                # Standalone & Companion Wear OS app
└── :baselineprofile     # Macrobenchmark & Baseline Profile generator (DEX startup boost)
```

| Component | Technology | Version | Purpose |
|---|---|---|---|
| **Language & Toolchain** | Kotlin / Java | 2.4.0 / JVM 21 | Modern type-safe concurrency with Coroutines & Flow |
| **Build System** | AGP / Gradle | AGP 9.2.1 / Gradle 9.5.1 | High-speed configuration cache builds |
| **UI Framework** | Jetpack Compose | BOM 2026.06.00 / M3 1.5.0 | Material Design 3 Expressive components |
| **Audio Pipeline** | AndroidX Media3 ExoPlayer | 1.10.1 | Dual player engine with FFmpeg extensions |
| **Local Proxy Server** | Ktor CIO | 3.5.0 | Embedded HTTP loopback proxy for cloud streaming |
| **Database** | Room | 2.8.4 (v42) | SQLite FTS4 full-text search & relational entities |
| **Dependency Injection**| Dagger Hilt | 2.59.2 | Dependency injection with KSP2 |
| **Cloud & Networking** | Google Drive API + OkHttp | OkHttp 5.4.0 | OAuth token auto-refresh & differential sync |
| **Image Loading** | Coil Compose | 2.7.0 | Custom 40MB memory + 100MB disk art cache |
| **Audio Tagging** | TagLib + JAudiotagger | TagLib 1.0.6 | Fast audio metadata & cover extraction |

---

## 📱 Requirements

* **Android 11+** (API Level 30 through 37)
* Compatible with ARM64 (`arm64-v8a`), ARM32 (`armeabi-v7a`), and x86_64 emulator architectures

---

## 🚀 Building & Development

### 1. Clone the Repository
```bash
git clone https://github.com/blakejjia/PixelPlayer-gcloud.git
cd PixelPlayer-gcloud
```

### 2. Configure Credentials (Optional)
Create a `local.properties` file in the root directory if you want to configure your custom Google Drive OAuth credentials:
```properties
GDRIVE_WEB_CLIENT_ID=your_client_id.apps.googleusercontent.com
GDRIVE_WEB_CLIENT_SECRET=your_client_secret
```

### 3. Build with Gradle
```bash
# Verify compilation
./gradlew :app:compileDebugKotlin --no-daemon --console=plain

# Build Universal Debug APK
./gradlew :app:assembleDebug -Ppixelplay.enableAbiSplits=false --no-daemon --console=plain

# Build Universal Release APK
./gradlew :app:assembleRelease -Ppixelplay.enableAbiSplits=false --no-daemon --console=plain
```

---

## ⬇️ Downloads & Automated Releases

All build artifacts are automatically tested and packaged via GitHub Actions:
* **GitHub Releases**: Download pre-built universal and architecture-specific `.apk` files from the [Releases Page](https://github.com/blakejjia/PixelPlayer-gcloud/releases).
* **Continuous Integration**: Every push to `master` automatically produces installable APK artifacts via our [Build Courser APK Workflow](.github/workflows/build-apk.yml).

---

## 📄 License & Attribution

Courser is an open-source project. 
* Portions of the playback pipeline and UI foundations originate from [PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer) (Copyright © Theo Vilardo and community contributors, originally under the MIT License and subsequent updates).
* Specialized course management, Google Drive sync, Today's Learn engine, and educational UI enhancements are Copyright © 2026 Courser Contributors.
* See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for detailed third-party licensing terms.

<p align="center">
  Crafted for focused, lifelong learners 🎓
</p>
