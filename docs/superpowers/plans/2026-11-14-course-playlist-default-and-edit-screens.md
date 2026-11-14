# Course Playlist Default & Edit Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple Courser's course/playlist presentation into two dedicated, cohesive screens:
1. **Default Course Screen (`PlaylistDefaultScreen`)**: Clean, low-cognitive-burden reading screen adapted from the legacy Album view, displaying the collapsible header, artwork/title, play/resume FAB, and lesson list. Features an **Edit button** on the top right.
2. **Course Edit Screen (`PlaylistEditScreen`)**: Full-featured playlist management screen (drag-and-drop song reordering, add songs, remove songs, change metadata, rename, delete).

**Architecture:** 
- In Courser, 1 Course = 1 Playlist = 1 Folder. "Album" and "Playlist" are the exact same domain concept.
- `PlaylistDefaultScreen` replaces `AlbumDetailScreen` as the default destination for all course tiles (`Screen.PlaylistDetail`), loading `playlistId: String` via `PlaylistViewModel`.
- An Edit button (`Icons.Rounded.Edit`) in the top right of `PlaylistDefaultScreen` navigates to `Screen.PlaylistEdit` (`PlaylistEditScreen`).
- Both screens share `PlaylistViewModel` state (`currentPlaylistDetails` and `currentPlaylistSongs`), ensuring edits made in `PlaylistEditScreen` immediately reflect upon popping back.

**Tech Stack:** Kotlin 2.4 / JVM 21, Jetpack Compose, Material 3 Expressive, Room 2.8.4, Hilt DI.

---

## Global Constraints
- Target SDK 37, Min SDK 30.
- Compose Stability: mark custom UI models as stable or use existing stable domain models (`Playlist`, `Song`).
- Material Design 3 Expressive motion and color scheme tokens (`surfaceContainerHigh`, `primary`, etc.).
- Comply with AGENTS.md rule 7 (`CourserLazyColumn` / player-aware insets) and rule 8 (Gradle `--no-daemon --console=plain` with `BypassSandbox: true`).

---

## Task Decomposition

### Task 1: Add `Screen.PlaylistEdit` Route
**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/navigation/Screen.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/navigation/AppNavigation.kt`

**Steps:**
- [ ] Add `Screen.PlaylistEdit : Screen("playlist_edit/{playlistId}")` with helper `createRoute(playlistId: String)`.
- [ ] Register `composable(Screen.PlaylistEdit.route)` in `AppNavigation.kt`.
- [ ] Ensure `Screen.PlaylistDetail` routes to `PlaylistDefaultScreen`.

---

### Task 2: Create `PlaylistDefaultScreen` (Refactored from `AlbumDetailScreen`)
**Files:**
- Create/Refactor: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/PlaylistDefaultScreen.kt`
- Keep `AlbumDetailScreen` as compatibility wrapper or alias.

**Features:**
- [ ] Accept `playlistId: String`, `navController: NavController`, `playerViewModel: PlayerViewModel`, `playlistViewModel: PlaylistViewModel`.
- [ ] Trigger `playlistViewModel.loadPlaylistDetails(playlistId)` on enter.
- [ ] Collect `currentPlaylistDetails` and `currentPlaylistSongs` from `PlaylistViewModel.uiState`.
- [ ] Render collapsible top bar with course cover art (`SmartImage`), gradient overlay, course title, and lesson count (`"${songs.size} total"`).
- [ ] Add **Edit button** (`Icons.Rounded.Edit`) in the top right of the top bar linking to `Screen.PlaylistEdit.createRoute(playlistId)`.
- [ ] Add Large Play/Resume FAB in the header that invokes `playlistViewModel.playPlaylist(playlist.id, playerViewModel)`.
- [ ] Render lessons with `EnhancedSongListItem`, displaying lesson progress, duration, and tap-to-play with resume.

---

### Task 3: Refactor `PlaylistDetailScreen` to `PlaylistEditScreen`
**Files:**
- Create/Refactor: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/PlaylistEditScreen.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/PlaylistDetailScreen.kt` (maintain backward compatibility).

**Features:**
- [ ] Preserve all existing editing capabilities:
  - Add songs sheet trigger
  - Drag-and-drop reorder mode
  - Remove songs mode
  - AI Reorder trigger
  - Rename / cover change dialogs
- [ ] Set back button to navigate back (`navController.popBackStack()`) returning to `PlaylistDefaultScreen`.

---

### Task 4: Navigation Wiring & Tile Click Destinations
**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/components/PlaylistContainer.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/TodaysLearnScreen.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/SearchScreen.kt`

**Steps:**
- [ ] Verify that clicking on any `CourseTile` across all screens navigates to `Screen.PlaylistDetail.createRoute(playlistId)`, which now lands on `PlaylistDefaultScreen`.
- [ ] Verify that tapping the Edit button on `PlaylistDefaultScreen` navigates to `PlaylistEditScreen`.

---

### Task 5: Compilation, Unit Tests & Packaging Verification
**Verification Steps:**
- [ ] Run `./gradlew :app:compileDebugKotlin --no-daemon --console=plain` with `BypassSandbox: true`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModelTest" --no-daemon --console=plain` with `BypassSandbox: true`.
- [ ] Run `./gradlew :app:assembleDebug --no-daemon --console=plain` with `BypassSandbox: true`.
- [ ] Commit all changes with conventional commit messages on `feature/playlist-course-pages`.
