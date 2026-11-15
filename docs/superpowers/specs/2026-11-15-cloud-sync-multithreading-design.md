# Cloud Sync Multithreading & Global Queue Design

## 1. Problem Statement & Motivation
Currently in Courser:
1. **Unintended Startup Sync**: Opening the app automatically triggers a full cloud sync across all Google Drive folders and songs because `HomeScreen` and `LibraryScreen` inject `GDriveDashboardViewModel`, which calls `syncAllFoldersAndSongs()` inside its `init` block. This violates Google Drive API guidelines, drains user battery/mobile data, and slows down app cold start.
2. **Competing Sync Threads**: If a user selects new course folders while a sync is already running (or hits sync multiple times), separate unconstrained coroutines are launched on `appScope`. These concurrent jobs race against each other, corrupting UI progress indicators and creating database race conditions in `incrementalSyncMusicData`.
3. **Sequential Network Bottlenecks**: Single-folder syncing crawls subdirectories and paginates audio files sequentially with a small `pageSize=100`, rather than taking advantage of Drive API v3's maximum `pageSize=1000` and bounded concurrent subfolder queries.

## 2. Goals & Non-Goals
### Goals:
- Eliminate automatic full cloud sync on app launch. Sync must be user-intent driven (folder selection, manual pull/button refresh).
- Ensure global synchronization: only **one** sync worker loop executes at any time. Subsequent sync requests are enqueued into a thread-safe singleton queue and processed sequentially.
- Accelerate cloud sync via bounded multithreading:
  - Increase `pageSize` to 1000 for Drive file and folder listings.
  - Explore subfolder trees concurrently with bounded concurrency (`Semaphore(4..6)`).
  - Probe audio durations concurrently with bounded permits.
  - Implement exponential backoff for rate limits (HTTP 429 / 403 / 503).

### Non-Goals:
- Modifying local MediaStore `SyncWorker` scheduling logic (local sync is already managed via WorkManager).
- Changing Google OAuth login/token lifecycle.

## 3. Architecture & Approaches

### Approach A: Standalone Background Service / WorkManager
- Run all Drive syncs via Android `WorkManager` with `ExistingWorkPolicy.APPEND_OR_REPLACE`.
- *Trade-off*: WorkManager adds overhead, delayed startup, and serialization complexity for interactive UI operations where immediate real-time progress flow is needed in `CourseFolderSelectionScreen` and `PlayerViewModel`.

### Approach B: Global In-Memory Coroutine Queue in `@Singleton CourseFolderManager` (Selected)
- Use a Kotlin `Channel<SyncRequest>(Channel.UNLIMITED)` inside the singleton `CourseFolderManager` with a single dedicated consumer loop launched on `appScope`.
- Immediate UI reactivity, zero serialization overhead, natural deduplication, and direct `StateFlow` progress emission to existing UI components.

## 4. Key Components

### 4.1 Global Queue & Request Types
```kotlin
sealed interface SyncRequest {
    data class ApplyBatch(
        val addedLocalFolders: List<File>,
        val removedLocalPaths: List<String>,
        val addedDriveFolders: List<Pair<String, String>>,
        val removedDriveIds: List<String>
    ) : SyncRequest
    data class SyncDriveFolder(val folderId: String) : SyncRequest
    data object SyncAllDriveFolders : SyncRequest
}
```

### 4.2 Rate Limits & Google Cloud API Compliance
- **Page Size**: Set `pageSize=1000` in `GDriveApiService.listAudioFiles` and `listFolders`.
- **Concurrency**: Bounded by `Semaphore(6)` to stay safely under 10–12 QPS per user.
- **Exponential Backoff**: Catch HTTP 429/403 in `executeGet` with retries (`delay(1000 * 2^attempt + jitter)`).

### 4.3 UI & Startup Fixes
- Remove `syncAllFoldersAndSongs()` from `GDriveDashboardViewModel.init`.
- `HomeScreen` and `LibraryScreen` observe login state safely without triggering background scans.
