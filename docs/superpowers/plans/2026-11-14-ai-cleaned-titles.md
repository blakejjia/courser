# AI Cleaned Titles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to toggle AI-cleaned song/lesson titles under Playlist Options for playlists that have been reordered by AI.

**Architecture:** 
1. `AiPlaylistReorderService` requests both sequential `rank` and `cleaned_title` (stripping tags, extensions, website names, keeping lesson numbering and clean title).
2. `UserPreferencesRepository` stores `aiCleanedTitlesByPlaylist` (`Map<playlistId, Map<songId, String>>`) and `useAiCleanedTitlesPlaylists` (`Set<playlistId>`).
3. `PlaylistViewModel` applies cleaned titles to `currentPlaylistSongs` when enabled and exposes `hasAiCleanedTitles` / `isUsingAiCleanedTitles`.
4. `PlaylistEditScreen` displays a Switch row in the playlist options sheet only when cleaned titles exist for that playlist.

**Tech Stack:** Kotlin 2.4, Jetpack Compose, Material Design 3 Expressive, DataStore, Dagger Hilt.

**Spec:** User requested switch in playlist options to use AI cleaned titles if AI already reordered the list.

## Global Constraints
- Target SDK: 37, Min SDK: 30.
- All Gradle commands must run with `--no-daemon --console=plain` and `BypassSandbox: true`.

---

### Task 1: Enhance AI Service with Cleaned Title Extraction

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/ai/AiSystemPromptEngine.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/ai/AiPlaylistReorderService.kt`
- Modify: `app/src/test/java/com/theveloper/pixelplay/data/ai/AiPlaylistReorderServiceTest.kt`

- [ ] **Step 1: Write/update tests for `AiPlaylistReorderServiceTest`**
Verify that `reorderSongs` extracts both order and cleaned titles.

- [ ] **Step 2: Update `AiSystemPromptEngine.COURSE_REORDER` prompt**
Update strategy and output schema to ask for `{"song_id": {"rank": 1, "cleaned_title": "..."}}`.

- [ ] **Step 3: Update `AiPlaylistReorderService`**
Return `AiReorderResult(val orderedSongIds: List<String>, val cleanedTitles: Map<String, String>)`. Parse `cleaned_title` with fallbacks.

- [ ] **Step 4: Run unit tests**
Verify tests pass.

---

### Task 2: Persist AI Cleaned Titles in Preferences

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/preferences/UserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/preferences/PlaylistPreferencesRepository.kt`

- [ ] **Step 1: Add keys and flows in `UserPreferencesRepository`**
Add `AI_CLEANED_TITLES_BY_PLAYLIST` and `USE_AI_CLEANED_TITLES_PLAYLISTS`.
Add accessors: `setAiCleanedTitles(playlistId, titlesMap)`, `setUseAiCleanedTitles(playlistId, enabled)`.

- [ ] **Step 2: Delegate in `PlaylistPreferencesRepository`**
Expose flows and update methods through `PlaylistPreferencesRepository`.

---

### Task 3: ViewModel Support for Cleaned Titles

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlaylistViewModel.kt`

- [ ] **Step 1: Update `PlaylistUiState`**
Add `hasAiCleanedTitles: Boolean = false` and `isUsingAiCleanedTitles: Boolean = false`.

- [ ] **Step 2: Save cleaned titles in `reorderPlaylistWithAi`**
Persist the returned `cleanedTitles`.

- [ ] **Step 3: Apply titles in `loadPlaylistDetails` and song list updates**
If `isUsingAiCleanedTitles` is true, map `song.copy(title = cleanedTitles[song.id] ?: song.title)`.

- [ ] **Step 4: Add `toggleUseAiCleanedTitles(playlistId)`**
Persist toggle state and immediately update `currentPlaylistSongs`.

---

### Task 4: UI Switch in Playlist Options Sheet & String Resources

**Files:**
- Modify: `app/src/main/res/values/strings_screens.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings_screens.xml`
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/PlaylistEditScreen.kt`

- [ ] **Step 1: Add localized strings**
`playlist_action_use_ai_cleaned_title`, `playlist_action_use_ai_cleaned_title_desc`.

- [ ] **Step 2: Add switch item in `showPlaylistOptionsSheet` in `PlaylistEditScreen.kt`**
Show only when `uiState.hasAiCleanedTitles` is true.

---

### Task 5: Compilation, Testing & Merge

- [ ] **Step 1: Compile and test**
Run `./gradlew :app:compileDebugKotlin --no-daemon --console=plain` with `BypassSandbox: true`.

- [ ] **Step 2: Merge into `master`**
Checkout `master`, merge `feature/ai-cleaned-titles`, and delete feature branch.
