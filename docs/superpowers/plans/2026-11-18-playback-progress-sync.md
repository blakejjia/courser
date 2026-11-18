# Playback Progress Synchronization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide a reliable, debounced, event-driven playback progress persistence pipeline that guarantees progress is preserved on seeks, periodic checkpoints, track transitions, track completions, and app lifecycle termination without thread violations or database thrashing.

**Architecture:** Maintain in-memory tracking of the current track and position in `CourseProgressTracker`. Enforce ExoPlayer Main-thread query safety. Distinguish lightweight throttle/debounce sync (periodic 15s heartbeat, seek debounce 500ms) from milestone flush (pause, track change, completion, service unload). In `MusicService`, capture transition events (auto-transition, seek/skip) to properly finalize outgoing tracks and evaluate completion ($\ge 90\%$ or within 5s) before starting new tracks.

**Tech Stack:** Kotlin, Jetpack Media3 ExoPlayer, Coroutines (SupervisorJob, Flow), Room Database, SQLite.

## Global Constraints
- Target SDK 37, Min SDK 30, Kotlin 2.4.0, Java 21.
- ExoPlayer is thread-confined to the Main thread: never call `player.currentPosition` from `Dispatchers.IO`.
- Gradle commands must use `--no-daemon --console=plain` and `BypassSandbox: true`.
- Room DB writes must execute on `Dispatchers.IO`.

## Review Focus
1. **Thread Safety**: Heartbeat or flush must never access ExoPlayer off `Dispatchers.Main`.
2. **Zero Overwrite Protection**: Position `0` must never overwrite valid progress unless a track is genuinely at start or explicitly reset upon completion.
3. **Track Transition Finalization**: Moving to next track (auto or manual) must record previous track's final position/completion before the new track starts.
4. **Auto-transition Completion**: A track transitioning via `AUTO_TRANSITION` when reaching end must be marked as completed if $\ge 90\%$ or within 5s of end.
5. **No DB Thrashing**: Seeking/scrubbing must be debounced (500ms), and 15s heartbeats must not re-query full course track counts.

---

### Task 1: Refactor `CourseProgressTracker` for Safe, Debounced & Event-Driven Sync

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/service/CourseProgressTracker.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/repository/CourseProgressRepository.kt`

- [x] **Step 1.1**: Update `CourseProgressRepository` to support lightweight cursor updates that skip full `refreshCompletedCount` during periodic heartbeats, while preserving full refresh during completions and track switches.
- [x] **Step 1.2**: In `CourseProgressTracker`, replace background `player.currentPosition` calls with safe Main-thread position retrieval (`withContext(Dispatchers.Main)` or caller-supplied position).
- [x] **Step 1.3**: Add a 500ms debounced seek mechanism: `onSeek(positionMs)` debounces before dispatching to `saveCurrentPosition`.
- [x] **Step 1.4**: Redesign heartbeat to run every 15s on Main thread to sample position, only triggering persistence if position advanced by $\ge 3000\text{ms}$ or was explicitly requested.
- [x] **Step 1.5**: Implement `onTrackLeaving(songId: String, finalPositionMs: Long, durationMs: Long)` in `CourseProgressTracker` to test completion threshold ($\ge 90\%$ or within 5s of duration), mark completed if eligible, or save resume bookmark otherwise.
- [x] **Step 1.6**: Implement `flushImmediate(player: Player?)` to synchronously/immediately commit in-memory position on pause, unload, or service destruction.

---

### Task 2: Wire Transition & Lifecycle Hooks in `MusicService`

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/service/MusicService.kt`

- [x] **Step 2.1**: In `MusicService.playerListener.onPositionDiscontinuity`, handle `DISCONTINUITY_REASON_AUTO_TRANSITION` by calling `courseProgressTracker.onTrackLeaving` for the outgoing media item with its final duration/position.
- [x] **Step 2.2**: In `MusicService.playerListener.onMediaItemTransition`, ensure any active track that is being transitioned away from has its progress finalized via `courseProgressTracker.onTrackLeaving` before `courseProgressTracker.onTrackStarted` is called for the new track.
- [x] **Step 2.3**: In `MusicService.playerListener.onPlaybackStateChanged`, keep `STATE_ENDED` calling `courseProgressTracker.onTrackLeaving` for the final track of the queue.
- [x] **Step 2.4**: In `MusicService.playerListener.onIsPlayingChanged`, ensure pausing (`isPlaying == false`) immediately flushes the active position.
- [x] **Step 2.5**: In `MusicService.stopPlaybackAndUnload` and `onDestroy`, call `courseProgressTracker.flushImmediate(player)` before stopping the player or clearing media items.

---

### Task 3: Unit Testing `CourseProgressTracker` & Progress Persistence Logic

**Files:**
- Create: `app/src/test/java/com/theveloper/pixelplay/CourseProgressTrackerTest.kt`

- [x] **Step 3.1**: Write unit tests for `CourseProgressTracker` verifying:
  - Immediate save on pause
  - Seek debouncing behavior
  - `onTrackLeaving` marks completion when position $\ge 90\%$ or within 5s of duration
  - `onTrackLeaving` saves resume position when under completion threshold
  - Heartbeat writes only when progress delta $\ge 3000\text{ms}$
  - `flushImmediate` persists current state
- [x] **Step 3.2**: Run the unit test suite:
  `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.CourseProgressTrackerTest" --no-daemon --console=plain` (with `BypassSandbox: true`).

---

### Task 4: Full Project Build & Regression Verification

**Files:**
- None (build verification)

- [x] **Step 4.1**: Compile Kotlin code:
  `./gradlew :app:compileDebugKotlin --no-daemon --console=plain` (with `BypassSandbox: true`).
- [x] **Step 4.2**: Run `SongProgressTest`:
  `./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.SongProgressTest" --no-daemon --console=plain` (with `BypassSandbox: true`).
