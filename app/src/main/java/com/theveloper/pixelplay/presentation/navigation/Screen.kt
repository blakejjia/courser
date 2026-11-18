package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.runtime.Immutable


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object TodaysLearn : Screen("todays_learn")
    object DailyGoalSettings : Screen("daily_goal_settings")
    object Search : Screen("search")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object Accounts : Screen("settings_accounts")
    object SettingsCategory : Screen("settings_category/{categoryId}") {
        fun createRoute(categoryId: String) = "settings_category/$categoryId"
    }
    object PaletteStyle : Screen("palette_style_settings")
    object Experimental : Screen("experimental_settings")
    object NavBarCrRad : Screen("nav_bar_corner_radius")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/${android.net.Uri.encode(playlistId)}"
        @Deprecated("Use createRoute(playlistId: String) instead", ReplaceWith("createRoute(playlistId.toString())"))
        fun createRoute(playlistId: Long) = createRoute(playlistId.toString())
    }
    object PlaylistEdit : Screen("playlist_edit/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_edit/${android.net.Uri.encode(playlistId)}"
        @Deprecated("Use createRoute(playlistId: String) instead", ReplaceWith("createRoute(playlistId.toString())"))
        fun createRoute(playlistId: Long) = createRoute(playlistId.toString())
    }

    object  DailyMixScreen : Screen("daily_mix")
    object RecentlyPlayed : Screen("recently_played")
    object Stats : Screen("stats")
    object GenreDetail : Screen("genre_detail/{genreId}") { // New screen
        fun createRoute(genreId: String) = "genre_detail/$genreId"
    }

    object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
    }

    object About : Screen("about")
    object OpenSourceLicenses : Screen("open_source_licenses")
    object EasterEgg : Screen("easter_egg")

    object ArtistSettings : Screen("artist_settings")
    object DelimiterConfig : Screen("delimiter_config")
    object WordDelimiterConfig : Screen("word_delimiter_config")
    object Equalizer : Screen("equalizer")
    object DeviceCapabilities : Screen("device_capabilities")
    object NavidromeDashboard : Screen("navidrome_dashboard")
    object JellyfinDashboard : Screen("jellyfin_dashboard")
    object GDriveDashboard : Screen("course_folder_selection?tab=gdrive")
    object CourseFolderSelection : Screen("course_folder_selection?tab={tab}") {
        fun createRoute(tab: String = "local") = "course_folder_selection?tab=$tab"
    }

}
