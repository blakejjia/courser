# Cloud Sync Multithreading & Global Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate redundant cloud sync on app launch, enforce a single global queue for course sync requests to prevent concurrent execution, and accelerate Google Drive sync through bounded multithreaded requests and `pageSize=1000`.

**Architecture:** A thread-safe `Channel`-based request queue in `@Singleton CourseFolderManager` ensures only one sync worker executes at any time, enqueuing and deduplicating subsequent requests. Inside `GDriveRepository` and `GDriveApiService`, folder tree traversal and duration probing utilize bounded concurrency (`Semaphore(4..6)`) and `pageSize=1000` with exponential backoff on HTTP 429/403. `GDriveDashboardViewModel.init` auto-sync is removed.

**Tech Stack:** Kotlin Coroutines (`Channel`, `Semaphore`, `Mutex`), Flow (`StateFlow`), OkHttp 5.4, Google Drive API v3, Jetpack Compose, Room Database v42.

**Spec:** `docs/superpowers/specs/2026-11-15-cloud-sync-multithreading-design.md`

## Global Constraints
- Target JVM 21, Kotlin 2.4.0, Android compileSdk 37.
- Always run Gradle with `--no-daemon --console=plain` and `BypassSandbox: true`.
- Room migrations: No schema changes required for this task.
- Threading: Database and network calls on `Dispatchers.IO`, player interactions on `Dispatchers.Main`.

## Review Focus
1. Rapidly tapping "Apply Changes" or navigating back does not launch duplicate competing sync jobs.
2. App startup (navigating to Home or Library) does not trigger any network requests to Google Drive when already logged in.
3. Subfolder exploration completes without hitting Google Drive HTTP 429 rate limit errors.
4. Natural sorting of course lessons is strictly preserved across concurrent folder queries.
5. In-flight sync progress does not reset to 0% if additional folders are added to the queue mid-sync.

---

### Task 1: Eliminate Auto-Sync on App Startup

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/gdrive/dashboard/GDriveDashboardViewModel.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/HomeScreen.kt:116`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt:113`
- Test: `app/src/test/java/com/theveloper/pixelplay/presentation/gdrive/dashboard/GDriveDashboardViewModelTest.kt`

**Interfaces:**
- `GDriveDashboardViewModel`: Retains `syncAllFoldersAndSongs()`, `syncFolder(folderId)`, `folders`, and `isLoggedIn`, but does NOT call `syncAllFoldersAndSongs()` in `init`.
- `HomeScreen` & `LibraryScreen`: Decoupled from full dashboard lifecycle or observing without side-effects.

- [ ] **Step 1: Write unit test verifying `GDriveDashboardViewModel` initialization does not invoke sync**

```kotlin
@Test
fun init_doesNotTriggerAutoSync() = runTest {
    val repository = mockk<GDriveRepository>(relaxed = true)
    every { repository.getFolders() } returns flowOf(emptyList())
    every { repository.isLoggedInFlow } returns MutableStateFlow(true)
    
    val viewModel = GDriveDashboardViewModel(repository)
    
    coVerify(exactly = 0) { repository.syncAllFoldersAndSongs() }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.presentation.gdrive.dashboard.GDriveDashboardViewModelTest" --no-daemon --console=plain`
Expected: FAIL (currently calls `syncAllFoldersAndSongs()` in `init`).

- [ ] **Step 3: Remove `syncAllFoldersAndSongs()` from `GDriveDashboardViewModel.init` and clean up screen usages**

In `GDriveDashboardViewModel.kt`:
```kotlin
init {
    // Do not auto-sync on init. Sync must be user-intent driven.
}
```
In `HomeScreen.kt` and `LibraryScreen.kt`:
Inject `GDriveRepository` directly or pass login state without triggering dashboard initialization side-effects.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.presentation.gdrive.dashboard.GDriveDashboardViewModelTest" --no-daemon --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/theveloper/pixelplay/presentation/gdrive/dashboard/GDriveDashboardViewModel.kt app/src/main/java/com/theveloper/pixelplay/presentation/screens/HomeScreen.kt app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt app/src/test/java/com/theveloper/pixelplay/presentation/gdrive/dashboard/GDriveDashboardViewModelTest.kt
git commit -m "fix: prevent automatic Google Drive full sync on app startup"
```

---

### Task 2: API Page Size Maximization & Rate Limit Backoff in `GDriveApiService`

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/gdrive/GDriveApiService.kt:44-80,189-206`
- Test: `app/src/test/java/com/theveloper/pixelplay/data/gdrive/GDriveApiServiceTest.kt`

**Interfaces:**
- `GDriveApiService.listAudioFiles(folderId: String, pageToken: String?): String`: Queries with `pageSize=1000`.
- `GDriveApiService.listFolders(parentId: String, pageToken: String?): String`: Queries with `pageSize=1000`.
- `GDriveApiService.executeGet(url: String): String`: Retries on HTTP 429 and 503 with exponential backoff and jitter.

- [ ] **Step 1: Write unit tests for pageSize and rate limit backoff in `GDriveApiServiceTest`**

```kotlin
@Test
fun listAudioFiles_usesPageSize1000() = runTest {
    // Assert request URL contains pageSize=1000
}

@Test
fun executeGet_retriesWithBackoffOnHttp429() = runTest {
    // Mock 429 response followed by 200 response; assert retry succeeded
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.gdrive.GDriveApiServiceTest" --no-daemon --console=plain`
Expected: FAIL.

- [ ] **Step 3: Implement `pageSize=1000` and exponential backoff retry in `GDriveApiService`**

Update `listAudioFiles` and `listFolders` to use `pageSize=1000`.
In `executeGet`, add retry loop:
```kotlin
var attempts = 0
val maxAttempts = 3
while (attempts < maxAttempts) {
    val response = okHttpClient.newCall(request).execute()
    if (response.code == 429 || response.code == 503) {
        attempts++
        response.close()
        val delayMs = (1000L * (1L shl attempts)) + (0..300).random()
        kotlinx.coroutines.delay(delayMs)
        continue
    }
    // ...
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.gdrive.GDriveApiServiceTest" --no-daemon --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/theveloper/pixelplay/data/gdrive/GDriveApiService.kt app/src/test/java/com/theveloper/pixelplay/data/gdrive/GDriveApiServiceTest.kt
git commit -m "perf: set Drive API pageSize to 1000 and add exponential backoff for 429/503"
```

---

### Task 3: Bounded Multithreaded Subfolder Exploration in `GDriveRepository`

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/gdrive/GDriveRepository.kt:479-545`
- Test: `app/src/test/java/com/theveloper/pixelplay/data/gdrive/GDriveRepositorySyncTest.kt`

**Interfaces:**
- `GDriveRepository.syncFolderSongs(folderId: String, resetOnComplete: Boolean): Result<Int>`: Explores subdirectories in parallel using a bounded `Semaphore(6)` while preserving natural ordering of relative paths.

- [ ] **Step 1: Write unit test verifying concurrent subfolder exploration and natural order**

```kotlin
@Test
fun syncFolderSongs_concurrentTraversal_preservesNaturalPathOrdering() = runTest {
    // Mock API returning nested folders and audio files
    // Verify naturalCompare order is strictly preserved
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.gdrive.GDriveRepositorySyncTest" --no-daemon --console=plain`
Expected: FAIL.

- [ ] **Step 3: Implement concurrent folder exploration with `Semaphore(6)` in `GDriveRepository`**

Parallelize level-by-level or branch exploration using coroutines bounded by `Semaphore(6)`:
```kotlin
val crawlSemaphore = Semaphore(6)
// Process folder items concurrently with permit, gathering audio files and subfolders
```
Ensure sorting via `naturalCompare(a.first, b.first)` is preserved prior to DB insertion.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.gdrive.GDriveRepositorySyncTest" --no-daemon --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/theveloper/pixelplay/data/gdrive/GDriveRepository.kt app/src/test/java/com/theveloper/pixelplay/data/gdrive/GDriveRepositorySyncTest.kt
git commit -m "perf: parallelize Google Drive folder crawling with bounded semaphore"
```

---

### Task 4: Global Singleton Sync Queue & Coordinator in `CourseFolderManager`

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/repository/CourseFolderManager.kt:72-108,471-540`
- Test: `app/src/test/java/com/theveloper/pixelplay/data/repository/CourseFolderManagerSyncQueueTest.kt`

**Interfaces:**
- `CourseFolderManager.enqueueBatchChanges(addedLocalFolders, removedLocalPaths, addedDriveFolders, removedDriveIds)`
- `CourseFolderManager.enqueueSyncAllDriveFolders()`
- Internal queue worker loop: Only one active coroutine processes requests sequentially from `Channel<SyncRequest>(Channel.UNLIMITED)`.

- [ ] **Step 1: Write unit test verifying sequential queue execution and deduplication**

```kotlin
@Test
fun enqueueBatchChanges_whenAlreadySyncing_enqueuesAndExecutesSequentially() = runTest {
    // Send two batch changes simultaneously
    // Verify that worker processes them sequentially without launching duplicate sync threads
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.repository.CourseFolderManagerSyncQueueTest" --no-daemon --console=plain`
Expected: FAIL.

- [ ] **Step 3: Implement `Channel<SyncRequest>` and single worker loop in `CourseFolderManager`**

```kotlin
sealed interface CourseSyncRequest {
    data class Batch(
        val addedLocal: List<File>,
        val removedLocal: List<String>,
        val addedDrive: List<Pair<String, String>>,
        val removedDrive: List<String>
    ) : CourseSyncRequest
    data class DriveFolder(val folderId: String) : CourseSyncRequest
    data object AllDriveFolders : CourseSyncRequest
}

private val syncChannel = Channel<CourseSyncRequest>(Channel.UNLIMITED)

init {
    appScope.launch(Dispatchers.IO) {
        for (request in syncChannel) {
            processSyncRequest(request)
        }
    }
}
```
Replace unconstrained `appScope.launch` in `applyBatchChangesInBackground` with `syncChannel.trySend(request)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.repository.CourseFolderManagerSyncQueueTest" --no-daemon --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/theveloper/pixelplay/data/repository/CourseFolderManager.kt app/src/test/java/com/theveloper/pixelplay/data/repository/CourseFolderManagerSyncQueueTest.kt
git commit -m "feat: serialize course syncing via global singleton request queue"
```

---

### Task 5: Full Project Verification & Regression Testing

**Files:**
- Verification only

- [ ] **Step 1: Run Kotlin compile check**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: BUILD SUCCESSFUL (0 errors).

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --console=plain`
Expected: BUILD SUCCESSFUL (All unit tests pass).

- [ ] **Step 3: Commit full branch integration state**

```bash
git commit --allow-empty -m "chore: complete cloud sync multithreading and global queue integration"
```
