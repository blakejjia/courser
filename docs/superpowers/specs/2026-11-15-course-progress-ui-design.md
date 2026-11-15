# Course Progress UI & State Specification

## 1. Overview
This specification details the UI and state updates for course playback in Courser:
1. Streamline the course item list (inside course playlists) by removing timestamps ("41 minutes ago", "just now").
2. Remove the 3-dots More Options button on each course item; place the playback percentage / completed status at that exact trailing position.
3. Remove the bottom `LinearProgressIndicator` from individual course items, keeping it only for whole courses / playlists (`CourseTile`).
4. Set the completion threshold to 90%: any track played $\ge 90\%$ is treated as completed ("already played").
5. Dim/gray out completed items so users immediately know they have already been listened to.
6. Strictly base completion status on the **latest playback position**: if a user goes back and re-listens to a completed track and stops at 40%, the track dynamically reverts to 40% status and active color instead of remaining locked in the completed state.

---

## 2. Requirements

### 2.1 Course Item UI (`EnhancedSongListItem.kt`)
- **Subtitle**: Under the title, show only `song.displayArtist`. Drop the subtitle progress text and `formatTimeAgo` timestamp.
- **Bottom Progress Bar**: Remove `LinearProgressIndicator` from individual course items.
- **Trailing Action Area**:
  - Remove the 3-dots `MoreVert` `FilledIconButton`.
  - When not in selection mode:
    - **Completed ($\ge 90\%$)**: Show a muted gray checkmark or completion indicator.
    - **In Progress ($1\% - 89\%$)**: Show the integer percentage text (e.g., `40%`) in a clean, legible typography/pill.
    - **Unplayed ($0\%$)**: Show nothing in trailing slot (clean, spacious).
- **Item Dimming for Played Tracks**:
  - When a track is completed ($\ge 90\%$), apply a muted gray tint to its title, artist text, and container background (e.g. `onSurface.copy(alpha = 0.55f)`).
  - When unplayed or in progress ($< 90\%$), render with standard full-contrast colors.
  - Active playing track highlight remains prominent regardless of past completion.

### 2.2 Progress & State Synchronization
- **90% Completion Rule**:
  - A track is considered completed if `(resumePositionMs.toFloat() / duration) >= 0.90f` or `isCompleted && resumePositionMs == 0L`.
  - At $\ge 90\%$, it must not display as "93% played"; it is displayed as completed.
- **Latest-Play Status (Dynamic Reversion)**:
  - If a user re-listens to a previously completed track and has a latest saved position at 40%, `isCompleted` is treated as `false` and the track displays as `40%` with active (non-gray) coloring.
  - In `CourseProgressRepository` / `EngagementDao`, updating resume position updates `is_completed` according to whether the new position meets or does not meet the $90\%$ threshold:
    ```sql
    is_completed = CASE WHEN (:durationMs > 0 AND :positionMs >= :durationMs * 0.90) THEN 1 ELSE 0 END
    ```
  - In `Song.kt`, `isCompleted` and `playbackPercentage` are aligned with the latest resume position.
