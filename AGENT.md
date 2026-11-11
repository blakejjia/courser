# AGENT.md: PixelPlayer-gcloud Knowledge Base & Architecture Guide

> **Target Audience**: AI Agents (and Human Contributors) working on the `PixelPlayer-gcloud` codebase.
> **Last Updated**: November 2026 (Reflects App Version `0.7.5-beta`, Room DB Version `42`, SDK 37 target).

---

## 1. Project Overview & Mission

**PixelPlayer-gcloud** (also branded as **PixelPlay**) is a modern, high-performance offline & multi-cloud music player for Android 11+ (API 30–37) and Wear OS.

Originating as a fork/extension of the open-source PixelPlayer project, the distinguishing superpower of this repository is **deep multi-cloud streaming and unified library management**:
- It unifies local storage (Android MediaStore / Scoped Storage) with **Google Drive**, **Navidrome / Subsonic**, **Jellyfin**, **NetEase Cloud Music (网易云音乐)**, **QQ Music (QQ音乐)**, and **Telegram** (via TDLib).
- It streams cloud audio directly into a customized **Media3 ExoPlayer** pipeline via embedded local HTTP proxies (Ktor CIO), enabling features like gapless playback, crossfade, and equalizer across all sources.
- Built 100% with modern Kotlin, Jetpack Compose, Material Design 3 (Material You), and Android Jetpack architecture components.

---

## 2. Multi-Module Architecture

The project is structured as a multi-module Gradle project:

```
PixelPlayer-gcloud/
├── app/                  # Main Android phone application (Compose UI, Services, Data, Cloud)
├── wear/                 # Standalone & Companion Wear OS app (Tiles, Local/Remote Playback)
├── shared/               # Shared DTOs, communication protocols, and models for Phone ↔ Wear
├── baselineprofile/      # Macrobenchmark & Baseline Profile generator for DEX startup optimization
├── docs/                 # Architectural specifications (e.g., performance-report.md)
└── remote-config/        # GitHub-hosted dynamic configuration (announcements, feature flags)
```

### Module Responsibilities:
- **`:app`**: Contains the full phone application, including `MusicService` (Jetpack Media3 `MediaLibraryService`), `DualPlayerEngine`, Room database, cloud integration repositories, Ktor streaming proxies, and Compose UI screens.
- **`:shared`**: Pure Kotlin/Android library containing serialization models (`WearPlayerState`, `WearBrowseRequest`, `WearBrowseResponse`, `WearTransferRequest`, etc.) passed via Google Play Services Wearable Data Layer (`WearDataPaths`).
- **`:wear`**: Standalone Wear OS player with its own `WearPlaybackService`, local Room database (`WearMusicDatabase`), and support for watch-local playback or remote-controlling the phone.
- **`:baselineprofile`**: Uses `androidx.benchmark.macro` to profile and generate startup and scrolling baseline profiles to eliminate JIT lag on release builds.

---

## 3. Technology Stack & Key Libraries

| Domain | Technology / Library | Version | Notes / Purpose |
|---|---|---|---|
| **Language & Platform** | Kotlin / JVM 21 | Kotlin 2.4.0 / Java 21 | Language version 2.4, target JVM 21 |
| **Android Build** | AGP / Gradle | AGP 9.2.1 / Gradle 9.5+ | Compile SDK: 37, Min SDK: 30, Target: 37 |
| **UI Framework** | Jetpack Compose | BOM 2026.06.00 / M3 1.5.0-alpha22 | Material Design 3 Expressive motion |
| **Audio Engine** | AndroidX Media3 ExoPlayer | 1.10.1 | Includes FFmpeg & MIDI extensions |
| **Dependency Injection**| Dagger Hilt | 2.59.2 | With KSP2 (`ksp.useKSP2=true`) |
| **Database** | Room | 2.8.4 | Database Version: 42, with SQLite FTS4 |
| **Local Proxy Server** | Ktor CIO | 3.5.0 | Embedded HTTP proxy for cloud stream conversion |
| **Networking** | Retrofit 3.0 + OkHttp 5.4 | OkHttp 5.4.0 | Multi-provider HTTP calls, LRCLIB, Deezer, etc. |
| **Image Loading** | Coil Compose | 2.7.0 | Custom 40MB memory cache + 100MB disk cache |
| **Audio Tagging** | TagLib + JAudiotagger | TagLib 1.0.6, JAudiotagger 3.0.1 | Rich audio metadata & album art editing |
| **AI Integrations** | Google GenAI (Gemini/Gemma), Groq | GenerativeAI SDK 0.9.0 | AI playlists, lyrics translation, metadata |
| **Telegram Client** | TDLib (Telegram Database Library) | Custom tdlib JNI | Channel streaming and caching |
| **Widgets** | AndroidX Glance | 1.3.0-alpha01 | Interactive home screen appwidgets |

---

## 4. Cloud & Streaming Engine (Core Innovation)

### 4.1 The Local Proxy Pattern (`CloudStreamProxy`)
ExoPlayer expects standard, seekable HTTP/HTTPS URLs (supporting `Range: bytes=...` requests). Cloud providers (Telegram, Google Drive, NetEase, QQ Music) often use proprietary APIs, chunked downloads, dynamic tokens, or specific headers (e.g. `Referer: https://y.qq.com/`).

To solve this, PixelPlayer implements an embedded **Ktor CIO** HTTP proxy server running locally on `127.0.0.1`:
- **Base Class**: `com.theveloper.pixelplay.data.stream.CloudStreamProxy<K>`
- **Security Check**: `CloudStreamSecurity` validates local loopback requests and blocks SSRF/unauthorized origin attacks.
- **URI Mapping**:
  - `telegram://{chatId}/{fileId}` → `http://127.0.0.1:<port>/telegram/{fileId}`
  - `netease://{songId}` → `http://127.0.0.1:<port>/netease/{songId}`
  - `qqmusic://{songMid}` → `http://127.0.0.1:<port>/qqmusic/{songMid}`
  - `navidrome://{songId}` → `http://127.0.0.1:<port>/navidrome/{songId}`
  - `jellyfin://{itemId}` → `http://127.0.0.1:<port>/jellyfin/{itemId}`
  - `gdrive://{fileId}` → `http://127.0.0.1:<port>/gdrive/{fileId}`

### 4.2 Cloud Providers Implementation Map

| Provider | Data Directory | Repository | Stream Proxy | Auth & Features |
|---|---|---|---|---|
| **Google Drive** | `data/gdrive/` | `GDriveRepository` | `GDriveStreamProxy` | Google Identity Services OAuth; streams audio files directly from user Drive. |
| **Telegram** | `data/telegram/` | `TelegramRepository` | `TelegramStreamProxy` | Uses TDLib (`TelegramClientManager`); scans audio in channels/topics; caches files (`TelegramCacheManager`). |
| **Navidrome** | `data/navidrome/` | `NavidromeRepository`| `NavidromeStreamProxy` | Subsonic API protocol; server playlist and album synchronization. |
| **Jellyfin** | `data/jellyfin/` | `JellyfinRepository` | `JellyfinStreamProxy` | Emby/Jellyfin REST API; token auth; remote library sync. |
| **NetEase** | `data/netease/` | `NeteaseRepository` | `NeteaseStreamProxy` | `NeteaseEncryption` (WEAPI, EAPI crypto); QR-code/cookie login; lossless streaming. |
| **QQ Music** | `data/qqmusic/` | `QqMusicRepository` | `QqMusicStreamProxy` | `QQSignGenerator` (evaluates JS signatures); QR login; Referer-gated streaming. |

---

## 5. Playback & Audio Pipeline

The playback pipeline lives under `data/service/player/` and `data/service/`:

```
data/service/
├── MusicService.kt                  # MediaLibraryService (Media3 Session Token, foreground notifications)
├── CastSyncCoordinator.kt           # Google Cast synchronization
├── WidgetUpdateManager.kt           # Glance Widget synchronization
└── player/
    ├── DualPlayerEngine.kt          # Dual ExoPlayer engine (Master + Auxiliary)
    ├── TransitionController.kt      # Gapless and crossfade transition coordinator
    ├── AudioDecoderPolicy.kt        # Hardware vs software decoder selection
    ├── HiFiCapabilityChecker.kt      # Hi-res audio output checks (up to 192kHz/24bit)
    ├── HiResSampleRateCapAudioProcessor.kt # Resampling processor when HAL caps out
    ├── SurroundDownmixProcessor.kt  # Downmixing 5.1/7.1 to stereo
    └── CastPlayer.kt                # Chromecast playback adapter
```

### 5.1 Why `DualPlayerEngine`?
Standard ExoPlayer crossfade is difficult because a single player cannot easily decode two audio streams and apply independent volume envelopes.
- `DualPlayerEngine` maintains two initialized `ExoPlayer` instances (`playerA` and `playerB`).
- When Track A nears its end, Track B is pre-buffered on the auxiliary player.
- During the transition window, `TransitionController` modulates volume envelopes (`envelope(time)`) between the outgoing and incoming player.
- Once completed, player roles swap seamlessly.

### 5.2 `ResolvingDataSource` Integration
In `DualPlayerEngine`, ExoPlayer's `DefaultMediaSourceFactory` is wrapped with a Media3 `ResolvingDataSource`:
```kotlin
val resolver = object : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme in CLOUD_PROXY_SCHEMES) {
            val resolved = resolvedUriCache.get(uri.toString())
            if (resolved != null) return dataSpec.buildUpon().setUri(resolved).build()
        }
        return dataSpec
    }
}
```
Before playback begins, `resolveCloudUriAsync(...)` pre-warms the respective proxy and caches the loopback URL.

### 5.3 Audio Offload & Device Workarounds
Direct Audio Offload saves significant battery by offloading decoding to the DSP, but vendor HALs are notoriously buggy:
- **Xiaomi devices on SDK 36+**: Offload disabled due to HAL stall bugs.
- **Google Pixel on SDK 37+**: Offload disabled for Opus formats due to a HAL position jump bug (~49s skip).
- **Lava / MediaTek devices on SDK 35+**: Offload disabled to avoid playback stutter.
- Implemented in `shouldDisableAudioOffloadByDefaultForDevice` in `DualPlayerEngine.kt`.

---

## 6. Data Layer & Database (Room v42)

The local SQLite database (`PixelPlayDatabase`) is at **version 42**.

### 6.1 Database Rules:
1. **Never use destructive migration in release**: Release builds crash rather than wipe user data (`fallbackToDestructiveMigration` is only enabled if `BuildConfig.DEBUG == true`).
2. **Every schema change requires an incremental migration**: From `MIGRATION_41_42` to future migrations.
3. **FTS Table**: `SongSearchFtsEntity` provides fast full-text search across songs, titles, artists, and lyrics.

### 6.2 Key Entities:
- `SongEntity`: Core metadata for local and cloud tracks.
- `ArtistEntity`, `AlbumEntity`, `SongArtistCrossRef`: Normalized multi-artist relationships.
- `TelegramSongEntity`, `TelegramChannelEntity`, `TelegramTopicEntity`: Telegram channels and audio caches.
- `NeteaseSongEntity`, `NeteasePlaylistEntity`: NetEase-specific tracks and user playlists.
- `QqMusicSongEntity`, `QqMusicPlaylistEntity`: QQ Music tracks and playlists.
- `NavidromeSongEntity`, `NavidromePlaylistEntity`: Subsonic server entities.
- `JellyfinSongEntity`, `JellyfinPlaylistEntity`: Jellyfin media entities.
- `GDriveSongEntity`, `GDriveFolderEntity`: Google Drive synced folders.
- `FavoritesEntity`, `PlaylistEntity`, `PlaylistSongEntity`: User organization.
- `SongEngagementEntity`: Listening habits, play count, skip count for Daily Mix and Stats.
- `AiCacheEntity`, `AiUsageEntity`: LLM query cache and token budget tracking.

---

## 7. UI, Theming & Jetpack Compose Best Practices

### 7.1 Compose Architecture
- Follows **Unidirectional Data Flow (UDF)**.
- Large ViewModels are decomposed into dedicated `*StateHolder`s:
  - `MainViewModel` coordinates: `PlaybackStateHolder`, `QueueStateHolder`, `LyricsStateHolder`, `ConnectivityStateHolder`, `ThemeStateHolder`, `CastStateHolder`, `DailyMixStateHolder`.
- **Compose Stability**: `app/compose_stability.conf` explicitly marks domain models (`Song`, `Artist`, `Album`, `Lyrics`, etc.) as stable, allowing the Compose compiler to skip recompositions when inputs don't change.

### 7.2 Dynamic Theming (Material You)
- Colors adapt dynamically based on album artwork or system wallpaper (`Palette` API).
- `ColorSchemeProcessor` calculates high-contrast, accessible light and dark palettes from extracted bitmap swatches.
- `ThemePreferencesRepository` lets users select theme styles (Monochrome, Vibrant, Expressive, etc.).

---

## 8. Directory & Package Map

```
app/src/main/java/com/theveloper/pixelplay/
├── data/
│   ├── ai/                 # Gemini, Gemma, Groq, OpenRouter clients & prompt builders
│   ├── backup/             # Full database & settings backup/restore (.pixelbackup)
│   ├── database/           # Room DB, DAOs, Entities, Migrations
│   ├── diagnostics/        # PerformanceMetrics (lock-free runtime monitoring)
│   ├── equalizer/          # EqualizerManager & audio presets
│   ├── gdrive/             # Google Drive API & stream proxy
│   ├── jellyfin/           # Jellyfin API & stream proxy
│   ├── media/              # TagLib metadata editor, AudioMetadataReader
│   ├── model/              # Domain models (Song, Album, Artist, Lyrics, etc.)
│   ├── navidrome/          # Subsonic API & stream proxy
│   ├── netease/            # NetEase API, crypto, stream proxy
│   ├── network/            # Retrofit services (LRCLIB, Deezer, APIs)
│   ├── observer/           # MediaStore ContentObserver
│   ├── preferences/        # DataStore repositories (User, Theme, Equalizer, Playlists)
│   ├── qqmusic/            # QQ Music API, sign generator, stream proxy
│   ├── repository/         # Unified MusicRepository, SongRepository, LyricsRepository
│   ├── service/            # MusicService, Notification providers, receivers
│   │   └── player/         # DualPlayerEngine, TransitionController, Decoders
│   ├── stats/              # Playback statistics, listening time trackers
│   ├── stream/             # CloudStreamProxy, CloudStreamSecurity
│   ├── telegram/           # TDLib integration, TelegramRepository, TelegramStreamProxy
│   └── worker/             # SyncWorker (WorkManager background sync)
├── di/                     # Hilt DI modules (AppModule, BackupModule, Qualifiers)
├── presentation/
│   ├── components/         # Reusable Compose widgets, bottom sheets, sliders
│   ├── gdrive/             # Google Drive auth & dashboard screens
│   ├── jellyfin/           # Jellyfin auth & dashboard screens
│   ├── navidrome/          # Navidrome auth & dashboard screens
│   ├── netease/            # NetEase auth & dashboard screens
│   ├── qqmusic/            # QQ Music auth & dashboard screens
│   ├── telegram/           # Telegram auth, channel selector, dashboard screens
│   ├── navigation/         # Compose Navigation graph & routes
│   ├── screens/            # Home, Library, Player, Queue, Settings, Stats, Equalizer screens
│   └── viewmodel/          # ViewModels and modular StateHolders
├── ui/
│   ├── glancewidget/       # Glance Home Screen AppWidgets (4x1, 4x2, etc.)
│   └── theme/              # Typography, Shape, Dynamic Themes
└── utils/                  # AlbumArtUtils, MediaItemBuilder, Chinese/Japanese Pinyin/Romaji
```

---

## 9. Build, Testing & Development Guide

### 9.1 Build Requirements
- **JDK**: Version 21 (Temurin or OpenJDK recommended)
- **Android SDK**: Compile SDK 37, Min SDK 30, Build-tools 35.0.0+
- **Gradle**: 9.5.1 via `./gradlew` wrapper

### 9.2 Key Commands
```powershell
# Build Phone Debug APK (standard)
./gradlew :app:assembleDebug

# Build Phone Debug APK with ABI splits (arm64-v8a and armeabi-v7a)
./gradlew :app:assembleDebug -Ppixelplay.enableAbiSplits=true

# Build Wear OS APK
./gradlew :wear:assembleDebug

# Run Unit Tests (JUnit Platform / Jupiter + Vintage)
./gradlew :app:testDebugUnitTest

# Run specific unit test class
./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.DualPlayerEngineTest"

# Generate Compose Compiler Metrics & Reports
./gradlew :app:assembleRelease -Ppixelplay.enableComposeCompilerReports=true
```

### 9.3 Credentials & Secrets
Configured in `local.properties` (never commit these):
```properties
TELEGRAM_API_ID=2040
TELEGRAM_API_HASH=b18441a1ff607e10a989891a5462e627
```
*(If omitted, defaults in `app/build.gradle.kts` fall back to standard Telegram dev keys).*

---

## 10. Developer Guardrails & Gotchas for AI Agents

1. **Threading with ExoPlayer**:
   - `ExoPlayer` is **thread-confined** to the Main thread. Never interact with `playerA` or `playerB` directly from `Dispatchers.IO` or background threads; switch to `Dispatchers.Main`.
   - All network proxy operations and Room DB operations must run on `Dispatchers.IO`.

2. **Database Migrations (Rule of Iron)**:
   - When modifying `PixelPlayDatabase` entities: **DO NOT** wipe tables.
   - Increment `version` in `@Database(version = N, ...)`.
   - Add a `MIGRATION_X_Y = object : Migration(X, Y) { ... }` in `PixelPlayDatabase.kt`.
   - Register it in `AppModule.kt` inside `providePixelPlayDatabase`.

3. **Cloud Streaming Schemas**:
   - When adding a new cloud music source, inherit from `CloudStreamProxy<K>`.
   - Add its URI scheme string to `REMOTE_MEDIA_SCHEMES` and `CLOUD_PROXY_SCHEMES` in `DualPlayerEngine.kt`.
   - Register resolution logic in `DualPlayerEngine.resolveCloudUriAsync`.

4. **Performance Instrumentation**:
   - As documented in `docs/performance-report.md`, hooks into `PerformanceMetrics` must be $O(1)$ and lock-free/cheap. Never do disk I/O, bitmap decode, or DB queries inside a timing probe.

5. **Scoped Storage Compliance**:
   - PixelPlayer targets API 37 and does **not** rely on `MANAGE_EXTERNAL_STORAGE`.
   - Modifications/deletions of local audio files use `MediaStore.createWriteRequest()` or `createDeleteRequest()`.

6. **Compose Recomposition Hygiene**:
   - Always verify new models are treated as stable. Add custom UI models to `app/compose_stability.conf` if they contain external or non-primitive types to prevent unnecessary screen redraws during audio playback.
