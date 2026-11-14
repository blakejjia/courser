# Legacy Cleanup & Full Course/Playlist Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Completely remove all legacy compatibility wrappers (`AlbumDetailScreen.kt`, `PlaylistDetailScreen.kt`, `AppDataBackupManager.kt`), delete obsolete legacy ViewModel (`AlbumDetailViewModel.kt`), and fully migrate all navigation call sites from `Screen.AlbumDetail` to `Screen.PlaylistDetail`.

**Architecture:** In Courser, 1 Course = 1 Folder = 1 Playlist. Albums are no longer a distinct domain concept from Playlists/Courses. All course viewing routes directly to `PlaylistDefaultScreen` via `Screen.PlaylistDetail`, and course editing routes to `PlaylistEditScreen` via `Screen.PlaylistEdit`. Obsolete files and wrappers are removed directly without leaving deprecated shims behind.

**Tech Stack:** Kotlin 2.4, Jetpack Compose, Navigation Compose, Media3 ExoPlayer, Dagger Hilt.

**Spec:** Direct user instruction to delete all legacy compatibility wrappers, delete all legacy files, and perform full migration to the new architecture.

## Global Constraints
- Target SDK: 37, Min SDK: 30.
- All Gradle commands must run with `--no-daemon --console=plain` and `BypassSandbox: true`.
- Zero broken imports or unresolved references; project must compile with `./gradlew :app:compileDebugKotlin`.

## Review Focus
1. Any leftover references to `AlbumDetailScreen` or `PlaylistDetailScreen` in imports.
2. Any screens still calling `Screen.AlbumDetail.createRoute(albumId)` instead of `Screen.PlaylistDetail.createRoute(courseId)`.
3. Unused imports in `AppNavigation.kt` and `MainActivity.kt`.
4. Ensure `Screen.PlaylistDetail` handles all course IDs previously passed as album IDs (they are already identical string identifiers in Courser).
5. Ensure clean compilation of both `:app` and `:shared`.

---

### Task 1: Migrate All Call Sites of `Screen.AlbumDetail` to `Screen.PlaylistDetail`

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/components/scoped/PlayerAlbumNavigationEffect.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/components/scoped/SheetActionHandlers.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/ArtistDetailScreen.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/DailyMixScreen.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/GenreDetailScreen.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/RecentlyPlayedScreen.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/SearchScreen.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/MainActivity.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/navigation/Screen.kt`

- [ ] **Step 1: Update navigation call sites in UI components and screens**
Replace `Screen.AlbumDetail.createRoute(...)` with `Screen.PlaylistDetail.createRoute(...)` and `Screen.AlbumDetail.route` with `Screen.PlaylistDetail.route`.

- [ ] **Step 2: Update `Screen.kt` and `MainActivity.kt`**
In `Screen.kt`, remove `Screen.AlbumDetail` (or alias it to `Screen.PlaylistDetail` if any external deep link relies on it). In `MainActivity.kt`, remove `Screen.AlbumDetail.route` from `routesWithHiddenNavigationBar`.

---

### Task 2: Remove Obsolete Legacy Wrappers and Legacy Files

**Files:**
- Delete: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/AlbumDetailScreen.kt`
- Delete: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/PlaylistDetailScreen.kt`
- Delete: `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/AlbumDetailViewModel.kt`
- Delete: `app/src/main/java/com/theveloper/pixelplay/data/backup/AppDataBackupManager.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/navigation/AppNavigation.kt`

- [ ] **Step 1: Delete `AlbumDetailScreen.kt`, `PlaylistDetailScreen.kt`, `AlbumDetailViewModel.kt`, and `AppDataBackupManager.kt`**
Remove the physical files from the repository.

- [ ] **Step 2: Clean up `AppNavigation.kt`**
Remove `import com.theveloper.pixelplay.presentation.screens.AlbumDetailScreen` and `import com.theveloper.pixelplay.presentation.screens.PlaylistDetailScreen`.
Remove the `composable(route = Screen.AlbumDetail.route)` block.

---

### Task 3: Build & Verification

- [ ] **Step 1: Verify compilation with Gradle**
Run `./gradlew :app:compileDebugKotlin --no-daemon --console=plain` with `BypassSandbox: true`.
Verify exit code 0.

- [ ] **Step 2: Commit all changes**
Git commit with descriptive message.
