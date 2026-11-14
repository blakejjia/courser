# AI Playlist Reorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable users to reorder any playlist (both user playlists and folder/course playlists) sequentially using the AI provider, credentials, and settings configured in the app, accessible via an AI Reorder button in playlist options.

**Architecture:** Leverage existing `AiHandler` and `AiSystemPromptEngine` to send structured song metadata to the LLM for logical sequence deduction. Persist the resulting order via `PlaylistPreferencesRepository` for both user and folder playlists, orchestrated by `PlaylistViewModel`, and surface the trigger in `PlaylistDetailScreen`.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, Dagger Hilt, AndroidX Room/DataStore.

**Spec:** `docs/superpowers/specs/2026-11-14-ai-playlist-reorder-design.md`

## Global Constraints
- Target SDK: 37, Min SDK: 30, Kotlin 2.4 / JVM 21.
- Gradle execution must use `--no-daemon --console=plain` with `BypassSandbox: true`.
- ExoPlayer operations must stay on Main; network/DB operations on IO.
- Material 3 Expressive UI consistency.

## Review Focus
- Unconfigured API key / provider: Should display friendly error guiding user to Settings rather than crashing or silent failure.
- Markdown code blocks in AI output: `AiResponseCleaner` must cleanly handle ` ```json ... ``` ` and partial responses.
- Handling of folder playlists: Must persist custom order across app restarts just like normal playlists.
- Large playlists: Truncate or paginate payload safely to avoid model context overflow.

---

### Task 1: Prompt Engine & Prompt Type
**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/ai/AiSystemPromptEngine.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Steps:**
- [ ] Add `COURSE_REORDER` to `enum class AiSystemPromptType`.
- [ ] In `AiSystemPromptEngine.buildPrompt`, define the system prompt layer for `COURSE_REORDER` instructing the LLM to output pure JSON mapping song ID to `{ "rank": <int> }`.
- [ ] Add string resources for AI reorder title, description, loading, and error states.

---

### Task 2: AI Playlist Reorder Service
**Files:**
- Create: `app/src/main/java/com/theveloper/pixelplay/data/ai/AiPlaylistReorderService.kt`
- Test: `app/src/test/java/com/theveloper/pixelplay/data/ai/AiPlaylistReorderServiceTest.kt`

**Steps:**
- [ ] Create `AiPlaylistReorderService` injecting `AiHandler`.
- [ ] Implement `reorderSongs(songs: List<Song>): Result<List<String>>`:
  - Build JSON input `[{"id": "...", "name": "..."}]`.
  - Call `aiHandler.generateContent(...)` with `AiSystemPromptType.COURSE_REORDER`.
  - Parse output with `AiResponseCleaner` into a map of `id -> rank`.
  - Sort original songs by `rank` (with fallback to original index if unranked).
  - Return the sorted list of song IDs.
- [ ] Write unit tests for successful parsing, fallback on unranked songs, and markdown JSON stripping.

---

### Task 3: Universal Playlist Order Persistence
**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/preferences/PlaylistPreferencesRepository.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/preferences/UserPreferencesRepository.kt`

**Steps:**
- [ ] Support folder playlist custom song ordering in `PlaylistPreferencesRepository` so that `folder_playlist:...` IDs can store and retrieve their custom song order override.
- [ ] Ensure `reorderSongsInPlaylist(playlistId, newSongOrderIds)` works for both standard playlists and folder playlist IDs.
- [ ] When loading playlist songs for folder playlists, apply the saved order override if present.

---

### Task 4: ViewModel Integration
**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlaylistViewModel.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlaylistUiState.kt` (or internal state)

**Steps:**
- [ ] Inject `AiPlaylistReorderService` and `AiPreferencesRepository` into `PlaylistViewModel`.
- [ ] Add `isAiReordering: Boolean` and `aiReorderMessage: String?` to `PlaylistUiState`.
- [ ] Implement `reorderPlaylistWithAi(playlistId: String)`:
  - Validate API key is present; if not, set an informative message.
  - Set `isAiReordering = true`.
  - Call `aiPlaylistReorderService.reorderSongs(currentPlaylistSongs)`.
  - On success, call `reorderSongsInPlaylist(playlistId, orderedSongIds)` and refresh.
  - Reset `isAiReordering = false`.

---

### Task 5: Playlist Detail UI Integration
**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/PlaylistDetailScreen.kt`

**Steps:**
- [ ] Enable the `MoreVert` button for all playlists (remove `!isFolderPlaylist` restriction on `showPlaylistOptionsSheet`).
- [ ] In `showPlaylistOptionsSheet`, add `PlaylistActionItem` for "AI 智能排序" (AI Reorder) with `Icons.Rounded.AutoAwesome`.
- [ ] When `isAiReordering` is true, show a non-dismissible loading dialog with a spinner and "AI 正在分析并排序曲目...".
- [ ] Show a Snackbar when reordering completes or fails.

---

### Task 6: Verification & Compilation
**Files:**
- Test: Run unit tests and compilation check via `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`.
- Verify no regressions.
