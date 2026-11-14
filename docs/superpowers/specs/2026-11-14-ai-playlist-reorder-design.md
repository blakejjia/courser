# AI Playlist Reorder Design Specification

## 1. Intent & Overview
Provide AI-powered sequential reordering for playlists (both user-created playlists and folder-based course playlists) in Courser (`PixelPlayer-gcloud`). The feature uses the user's configured AI credentials and prompt settings from the Settings screen (supporting Gemini, DeepSeek, Groq, OpenAI, etc.), submits the track list to the AI to deduce the most logical sequential order (handling lesson numbers, chapter titles, prefixes, audiobooks, dates), and automatically updates the playlist order.

## 2. Requirements & Behavior
- **Universal Support**: Treat all playlists the same—both standard user playlists and folder/course playlists (`folder_playlist:...`).
- **AI Credentials & Provider**: Reads API key, model, and provider directly from `AiPreferencesRepository` / `AiHandler`. If unconfigured, prompts user to configure them in Settings.
- **Playlist Options UI**: Add an "AI 智能排序" (AI Reorder) button with an `AutoAwesome` icon in `PlaylistDetailScreen`'s options menu/bottom sheet.
- **Execution & UX**:
  - Show a progress/loading dialog while the AI is analyzing ("AI 正在分析并排序曲目...").
  - Handle errors gracefully with clear user feedback (e.g. invalid API key, timeout, parsing failure).
  - Automatically apply the new track sequence and refresh the playlist view upon completion.

## 3. Architecture & Data Flow

### 3.1 AI Reorder Service (`data/ai/AiPlaylistReorderService.kt`)
- Input: `List<Song>` (extracts `id` and `title` / filename).
- Prompt: Informs the AI to act as a sequential playlist and course organizer, detecting chapter/lesson numbering, clean title formatting, and chronological or lesson order, returning a strict JSON mapping:
  ```json
  {
    "song_id_1": { "rank": 1 },
    "song_id_2": { "rank": 2 }
  }
  ```
- Calls `AiHandler.generateContent` with `AiSystemPromptType.COURSE_REORDER` and cleans JSON with `AiResponseCleaner`.
- Returns ordered song IDs sorted by `rank`.

### 3.2 Folder Playlist Ordering Persistence
- Currently, standard playlists store their order in `PlaylistPreferencesRepository.reorderSongsInPlaylist(playlistId, songIds)`.
- For folder playlists (`folder_playlist:...`), `PlaylistPreferencesRepository` or `UserPreferencesRepository` will store custom song order mapping (`getFolderSongOrder(folderPlaylistId)` / `setFolderSongOrder(folderPlaylistId, songIds)`), ensuring folder playlists persist custom or AI-sorted orders smoothly just like user playlists.

### 3.3 ViewModel (`PlaylistViewModel.kt`)
- `reorderPlaylistWithAi(playlistId: String)`
- Manages `aiReorderState: AiReorderState` (`Idle`, `Loading`, `Success`, `Error(message)`).
- Coordinates fetching songs, calling `AiPlaylistReorderService`, persisting new song order, and updating `_uiState.currentPlaylistSongs`.

### 3.4 UI (`PlaylistDetailScreen.kt`)
- Enable `MoreVert` / Playlist Options for all playlists (both standard and folder playlists).
- Add `PlaylistActionItem` for "AI Reorder" in `showPlaylistOptionsSheet`.
- Display modal progress dialog when `isAiReordering` is active.
- Display Snackbar or alert dialog on completion or failure.
