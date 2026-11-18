# AI Naming & Reorder Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide an expandable, Material Design 3 setting in `PlaylistEditScreen` allowing users to toggle AI reorder and AI cleaned titles with zero redundant API calls, rerun AI reordering, rename "Edit Playlist" to "Edit Cover", and remove "Export Playlist".

**Architecture:** 
1. DataStore layer persists `AI_REORDERED_SONG_IDS_BY_PLAYLIST` and `USE_AI_REORDER_PLAYLISTS` in `UserPreferencesRepository`.
2. `PlaylistViewModel` coordinates cache-first toggling: first-time toggle executes AI analysis; subsequent toggles switch purely in-memory/DataStore without network calls; disabling AI reorder reverts to default natural file order.
3. `PlaylistEditScreen` renders an M3 Expressive expandable card with three child items (Use AI Reorder, Use AI Cleaned Titles, Rerun AI Reorder), updates "Edit Playlist" to "Edit Cover", and eliminates the M3U export action.

**Tech Stack:** Kotlin 2.4, Jetpack Compose (M3 Expressive), AndroidX DataStore Preferences, Kotlinx Coroutines & Flow, AndroidX Media3 ExoPlayer, Dagger Hilt.

**Spec:** Approved chat specification from 2026-11-18.

## Global Constraints

- Android compile SDK: 37, min SDK: 30.
- Gradle execution rule: Always run Gradle commands with `--no-daemon --console=plain` (e.g., `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`).
- Database rule: No Room schema modifications required for DataStore preferences changes.
- Compose performance: Use `remember`, `derivedStateOf`, and `animateContentSize` for fluid 60/120fps transitions.

## Review Focus

1. **First-time trigger**: Toggling "Use AI Reorder" when no AI ordering is cached must trigger AI reorder via `reorderPlaylistWithAi` with loading UI.
2. **Offline/Zero-token toggle**: Toggling "Use AI Reorder" or "Use AI Cleaned Titles" when cached data exists must update UI instantly without calling `AiHandler`.
3. **Natural order reversion**: Turning OFF "Use AI Reorder" must restore original track order (natural folder filename sequence or current sort option).
4. **Independent title toggle**: Turning OFF "Use AI Cleaned Titles" must restore original audio tags while preserving AI reorder sequence.
5. **Rerun freshness**: Clicking "Rerun AI Reorder" must re-execute AI analysis and refresh both ordering and title caches.

---

### Task 1: String Resources & Translations

**Files:**
- Modify: `app/src/main/res/values/strings_screens.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings_screens.xml`

**Interfaces:**
- Produces:
  - `R.string.playlist_action_edit_cover`
  - `R.string.playlist_action_ai_course_group_title`
  - `R.string.playlist_action_ai_course_group_desc`
  - `R.string.playlist_action_use_ai_reorder`
  - `R.string.playlist_action_use_ai_reorder_desc`
  - `R.string.playlist_action_rerun_ai_reorder`
  - `R.string.playlist_action_rerun_ai_reorder_desc`
  - `R.string.playlist_ai_first_time_hint`

- [x] **Step 1: Add string resources in `strings_screens.xml`**

```xml
    <string name="playlist_action_edit_cover">Edit Cover</string>
    <string name="playlist_action_ai_course_group_title">AI Course Assistant</string>
    <string name="playlist_action_ai_course_group_desc">Sequential lesson reordering and naming</string>
    <string name="playlist_action_use_ai_reorder">Use AI Reorder</string>
    <string name="playlist_action_use_ai_reorder_desc">Apply AI sequential order; disable to restore natural order</string>
    <string name="playlist_action_rerun_ai_reorder">Rerun AI Reorder</string>
    <string name="playlist_action_rerun_ai_reorder_desc">Re-analyze course tracks to refresh order and titles</string>
    <string name="playlist_ai_first_time_hint">Enable AI Reorder to analyze lesson tracks and generate optimized titles.</string>
```

- [x] **Step 2: Add Chinese string translations in `values-zh-rCN/strings_screens.xml`**

```xml
    <string name="playlist_action_edit_cover">编辑封面</string>
    <string name="playlist_action_ai_course_group_title">AI 课程智能编排</string>
    <string name="playlist_action_ai_course_group_desc">理解课程逻辑并优化播放顺序与名称</string>
    <string name="playlist_action_use_ai_reorder">使用 AI 排序结果</string>
    <string name="playlist_action_use_ai_reorder_desc">开启应用推荐课节顺序；关闭恢复默认文件名排序</string>
    <string name="playlist_action_rerun_ai_reorder">让 AI 重新排序</string>
    <string name="playlist_action_rerun_ai_reorder_desc">重新分析当前课程所有音轨，刷新排序与标题结果</string>
    <string name="playlist_ai_first_time_hint">点击开启「使用 AI 排序结果」，AI 将自动梳理章节顺序并规范小节名称。</string>
```

- [x] **Step 3: Verify resources compile**

Run: `./gradlew :app:checkDebugAarMetadata --no-daemon --console=plain`
Expected: BUILD SUCCESSFUL

---

### Task 2: DataStore Persistence for AI Reorder State

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/preferences/UserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/theveloper/pixelplay/data/preferences/PlaylistPreferencesRepository.kt`

**Interfaces:**
- Produces:
  - `UserPreferencesRepository.aiReorderedSongIdsByPlaylistFlow: Flow<Map<String, List<String>>>`
  - `UserPreferencesRepository.useAiReorderPlaylistsFlow: Flow<Set<String>>`
  - `UserPreferencesRepository.setAiReorderedSongIds(playlistId: String, songIds: List<String>)`
  - `UserPreferencesRepository.setUseAiReorder(playlistId: String, enabled: Boolean)`
  - Corresponding delegates in `PlaylistPreferencesRepository`

- [x] **Step 1: Add preferences keys and flows in `UserPreferencesRepository.kt`**

In `PreferencesKeys`:
```kotlin
val AI_REORDERED_SONG_IDS_BY_PLAYLIST = stringPreferencesKey("ai_reordered_song_ids_by_playlist")
val USE_AI_REORDER_PLAYLISTS = stringSetPreferencesKey("use_ai_reorder_playlists")
```

In `UserPreferencesRepository`:
```kotlin
val aiReorderedSongIdsByPlaylistFlow: Flow<Map<String, List<String>>> =
    pref { preferences ->
        decodeJsonPref(preferences, PreferencesKeys.AI_REORDERED_SONG_IDS_BY_PLAYLIST, emptyMap())
    }

val useAiReorderPlaylistsFlow: Flow<Set<String>> =
    pref { it[PreferencesKeys.USE_AI_REORDER_PLAYLISTS] ?: emptySet() }.distinctUntilChanged()

suspend fun setAiReorderedSongIds(playlistId: String, songIds: List<String>) {
    editJsonMap<List<String>>(PreferencesKeys.AI_REORDERED_SONG_IDS_BY_PLAYLIST) { put(playlistId, songIds) }
}

suspend fun setUseAiReorder(playlistId: String, enabled: Boolean) {
    dataStore.edit { preferences ->
        val current = preferences[PreferencesKeys.USE_AI_REORDER_PLAYLISTS] ?: emptySet()
        preferences[PreferencesKeys.USE_AI_REORDER_PLAYLISTS] = if (enabled) {
            current + playlistId
        } else {
            current - playlistId
        }
    }
}
```

- [x] **Step 2: Expose methods in `PlaylistPreferencesRepository.kt`**

```kotlin
val aiReorderedSongIdsByPlaylistFlow: Flow<Map<String, List<String>>> =
    userPreferencesRepository.aiReorderedSongIdsByPlaylistFlow

val useAiReorderPlaylistsFlow: Flow<Set<String>> =
    userPreferencesRepository.useAiReorderPlaylistsFlow

suspend fun setAiReorderedSongIds(playlistId: String, songIds: List<String>) =
    userPreferencesRepository.setAiReorderedSongIds(playlistId, songIds)

suspend fun setUseAiReorder(playlistId: String, enabled: Boolean) =
    userPreferencesRepository.setUseAiReorder(playlistId, enabled)
```

- [x] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: BUILD SUCCESSFUL

---

### Task 3: PlaylistViewModel State & Toggle Logic

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlaylistViewModel.kt`

**Interfaces:**
- Consumes:
  - `playlistPreferencesRepository.aiReorderedSongIdsByPlaylistFlow`
  - `playlistPreferencesRepository.useAiReorderPlaylistsFlow`
  - `playlistPreferencesRepository.setAiReorderedSongIds`
  - `playlistPreferencesRepository.setUseAiReorder`
- Produces:
  - `PlaylistUiState.hasAiReorderData: Boolean`
  - `PlaylistUiState.isUsingAiReorder: Boolean`
  - `PlaylistViewModel.toggleUseAiReorder(playlistId: String)`
  - `PlaylistViewModel.rerunAiReorder(playlistId: String)`
  - `PlaylistViewModel.toggleUseAiCleanedTitles(playlistId: String)`

- [x] **Step 1: Update `PlaylistUiState` with AI reorder states**

In `PlaylistUiState`:
```kotlin
val hasAiReorderData: Boolean = false,
val isUsingAiReorder: Boolean = false,
```

- [x] **Step 2: Update `loadPlaylistDetails` in `PlaylistViewModel.kt` to apply AI reorder and titles conditionally**

When loading songs for folder or custom playlists:
```kotlin
val allAiReorderedIds = playlistPreferencesRepository.aiReorderedSongIdsByPlaylistFlow.first()
val aiReorderedSongIds = allAiReorderedIds[playlistId].orEmpty()
val useAiReorderPlaylists = playlistPreferencesRepository.useAiReorderPlaylistsFlow.first()
val isUsingAiReorder = playlistId in useAiReorderPlaylists && aiReorderedSongIds.isNotEmpty()

val effectiveSongs = if (isUsingAiReorder) {
    val songMap = baseSongs.associateBy { it.id }
    aiReorderedSongIds.mapNotNull { songMap[it] } + baseSongs.filter { it.id !in aiReorderedSongIds }
} else {
    baseSongs
}
```
And update `_uiState` with `hasAiReorderData = aiReorderedSongIds.isNotEmpty()` and `isUsingAiReorder = isUsingAiReorder`.

- [x] **Step 3: Implement `toggleUseAiReorder` and `rerunAiReorder` in `PlaylistViewModel.kt`**

```kotlin
fun toggleUseAiReorder(playlistId: String) {
    viewModelScope.launch {
        val allAiReordered = playlistPreferencesRepository.aiReorderedSongIdsByPlaylistFlow.first()
        val cachedIds = allAiReordered[playlistId].orEmpty()
        if (cachedIds.isEmpty()) {
            // First time: run AI reorder which will compute and cache both order & titles
            reorderPlaylistWithAi(playlistId)
        } else {
            // Subsequent toggle: switch locally without API call
            val useReorderPlaylists = playlistPreferencesRepository.useAiReorderPlaylistsFlow.first()
            val newUsing = playlistId !in useReorderPlaylists
            playlistPreferencesRepository.setUseAiReorder(playlistId, newUsing)
            loadPlaylistDetails(playlistId)
        }
    }
}

fun rerunAiReorder(playlistId: String) {
    reorderPlaylistWithAi(playlistId)
}
```

- [x] **Step 4: Update `reorderPlaylistWithAi` to save `aiReorderedSongIds` and auto-enable both toggles**

On success:
```kotlin
playlistPreferencesRepository.setAiReorderedSongIds(playlistId, newSongOrderIds)
playlistPreferencesRepository.setUseAiReorder(playlistId, true)
if (cleanedTitles.isNotEmpty()) {
    playlistPreferencesRepository.setAiCleanedTitles(playlistId, cleanedTitles)
    playlistPreferencesRepository.setUseAiCleanedTitles(playlistId, true)
}
```

- [x] **Step 5: Verify unit tests and compilation**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: BUILD SUCCESSFUL

---

### Task 4: UI Refactoring in `PlaylistEditScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/theveloper/pixelplay/presentation/screens/PlaylistEditScreen.kt`

**Interfaces:**
- Consumes:
  - `uiState.hasAiReorderData`
  - `uiState.isUsingAiReorder`
  - `uiState.hasAiCleanedTitles`
  - `uiState.isUsingAiCleanedTitles`
  - `playlistViewModel.toggleUseAiReorder`
  - `playlistViewModel.toggleUseAiCleanedTitles`
  - `playlistViewModel.rerunAiReorder`

- [x] **Step 1: Build the M3 Expandable `AiCourseReorderGroup` composable**

Create an expandable container component in `PlaylistEditScreen.kt`:
```kotlin
@Composable
private fun AiCourseReorderSection(
    uiState: PlaylistUiState,
    playlistId: String,
    onToggleUseAiReorder: () -> Unit,
    onToggleUseAiTitles: () -> Unit,
    onRerunAiReorder: () -> Unit
)
```
- Outer surface in `MaterialTheme.colorScheme.surfaceContainerHigh` with `RoundedCornerShape(18.dp)`.
- Header row with `Icons.Rounded.AutoAwesome` (tinted primary), title `stringResource(R.string.playlist_action_ai_course_group_title)`, subtitle `stringResource(R.string.playlist_action_ai_course_group_desc)`, and animated chevron icon (`animateFloatAsState` rotation).
- Expandable column wrapped in `AnimatedVisibility(visible = isExpanded)` with `expandVertically() + fadeIn()` and `shrinkVertically() + fadeOut()`.
- Inside the expansion:
  - If `!uiState.hasAiReorderData`: First-time guidance card (`playlist_ai_first_time_hint`).
  - Child 1: `PlaylistSubSwitchItem` for "Use AI Reorder" (`uiState.isUsingAiReorder`).
  - Child 2: `PlaylistSubSwitchItem` for "Use AI Cleaned Titles" (`uiState.isUsingAiCleanedTitles`), enabled when `uiState.hasAiCleanedTitles`.
  - Child 3: `PlaylistSubActionItem` for "Rerun AI Reorder" with refresh icon.

- [x] **Step 2: Update `showPlaylistOptionsSheet` in `PlaylistEditScreen.kt`**

1. Replace the legacy `PlaylistActionItem(AI Reorder)` and `PlaylistSwitchItem(Use AI Cleaned Title)` with `AiCourseReorderSection`.
2. Replace "Edit Playlist" label with `stringResource(R.string.playlist_action_edit_cover)` ("Edit Cover").
3. Delete the `Export Playlist` item and remove `m3uExportLauncher` usage from this screen.

- [x] **Step 3: Compile and run verification**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: BUILD SUCCESSFUL

---

### Task 5: End-to-End Verification and Unit Tests

**Files:**
- Create/Modify: `app/src/test/java/com/theveloper/pixelplay/presentation/viewmodel/PlaylistViewModelAiReorderTest.kt`

- [x] **Step 1: Write unit tests covering AI reorder toggle behavior**
- [x] **Step 2: Run all unit tests**
- [x] **Step 3: Build Debug APK**

Run: `./gradlew :app:assembleDebug --no-daemon --console=plain`
Expected: BUILD SUCCESSFUL
