# Course Progress UI & State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Streamline Courser's course item list by removing relative timestamps, removing individual bottom progress bars, replacing the options button with the progress percentage / completion indicator, graying out completed items, and dynamically tracking latest playback position (treating $\ge 90\%$ as completed, and reverting to $40\%$ if re-listened).

**Architecture:**
- **Domain Layer (`Song.kt`)**: Align `isCompletedTrack` and `playbackPercentage` with the $\ge 90\%$ threshold and latest resume position.
- **Data Layer (`EngagementDao.kt`, `CourseProgressRepository.kt`)**: Persist `is_completed` dynamically on position updates (resetting to 0 when re-listened below 90%, and 1 when $\ge 90\%$).
- **UI Layer (`EnhancedSongListItem.kt`)**: Remove subtitle timestamps, remove bottom progress bar, render progress percentage or gray checkmark in the trailing position, and dim/gray out completed items.

**Tech Stack:** Kotlin 2.4 / JVM 21, Jetpack Compose, Material 3 Expressive, Room 2.8.4, Hilt DI.

**Spec:** `docs/superpowers/specs/2026-11-15-course-progress-ui-design.md`

---

## Global Constraints
- Target SDK 37, Min SDK 30.
- Compose Stability: maintain stable models and efficient recomposition.
- AGENTS.md rule 8: Run Gradle with `--no-daemon --console=plain` and unsandboxed elevation (`BypassSandbox: true`).

---

## Task Decomposition

### Task 1: Update `Song.kt` Domain Model & Unit Tests
**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/model/Song.kt`
- Create/Modify: `app/src/test/java/com/theveloper/pixelplay/SongProgressTest.kt`

**Steps:**
- [ ] In `Song.kt`, define `isCompletedTrack: Boolean`:
  - Returns `true` if `duration > 0L && (resumePositionMs.toFloat() / duration) >= 0.90f`.
  - If `duration <= 0L`, falls back to `isCompleted`.
  - If `duration > 0L && (resumePositionMs.toFloat() / duration) < 0.90f`, returns `false` (reverts if user re-listened).
- [ ] In `Song.kt`, adjust `playbackPercentage`:
  - When `isCompletedTrack` is true: returns `1.0f`.
  - When `duration > 0L`: returns `(resumePositionMs.toFloat() / duration).coerceIn(0f, 1f)`.
  - Otherwise `0f`.
- [ ] Write unit tests in `SongProgressTest.kt` covering:
  - Track with 93% position is completed (`isCompletedTrack == true`, `playbackProgressPercent == 100`).
  - Track re-listened to 40% reverts to not completed (`isCompletedTrack == false`, `playbackProgressPercent == 40`).
  - Track with 0 position is unplayed.

---

### Task 2: Update `EngagementDao.kt` & `CourseProgressRepository.kt` Position Updates
**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/database/EngagementDao.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/repository/CourseProgressRepository.kt`

**Steps:**
- [ ] In `EngagementDao.kt`:
  - Update `updateResumePosition(songId: String, positionMs: Long, timestamp: Long, isCompleted: Boolean)` to update `is_completed = :isCompleted` so resuming at 40% clears completion and $\ge 90\%$ marks completion.
- [ ] In `CourseProgressRepository.kt`:
  - In `saveTrackResumePosition(songId: String, positionMs: Long, durationMs: Long = 0L)`:
    Calculate `isCompleted = durationMs > 0L && isCompletionThresholdReached(positionMs, durationMs)` and pass to `engagementDao.updateResumePosition`.
  - In `saveCursor`: pass `songDurationMs` when saving resume position so `isCompleted` is accurately synchronized.

---

### Task 3: Refactor `EnhancedSongListItem.kt` UI
**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/components/subcomps/EnhancedSongListItem.kt`

**Steps:**
- [ ] Remove relative timestamp and subtitle progress row:
  - In the Column below `song.title`, render only `song.displayArtist`.
  - Remove the `song.isCompleted` / `song.playbackPercentage` subtitle row that appended `formatTimeAgo`.
- [ ] Remove the bottom `LinearProgressIndicator` (lines 429-438).
- [ ] Replace the trailing 3-dots `MoreVert` button with progress/status badge:
  - When in selection mode: show selection circle as before.
  - When not in selection mode:
    - If `song.isCompletedTrack`: show gray checkmark icon (`Icons.Rounded.CheckCircle`) with muted color.
    - If in progress (`song.playbackPercentage > 0f` and not completed): show text `${song.playbackProgressPercent}%` in `MaterialTheme.typography.labelMedium` with secondary/subtle container.
    - If unplayed (`0%`): show nothing.
- [ ] Dim/gray out completed items:
  - If `song.isCompletedTrack && !isCurrentSong`:
    - Apply muted gray color to title and artist (e.g., `contentColor.copy(alpha = 0.5f)`).
    - Tint container color to a subdued muted shade so the item visually reads as "already played".
  - If currently playing (`isCurrentSong`): maintain high-contrast active highlight container and playing EQ icon.

---

### Task 4: Verification & Build
**Steps:**
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.SongProgressTest" --no-daemon --console=plain` (with `BypassSandbox: true`).
- [ ] Run `./gradlew :app:compileDebugKotlin --no-daemon --console=plain` (with `BypassSandbox: true`).
- [ ] Verify no regressions and confirm all criteria from user request are satisfied.
