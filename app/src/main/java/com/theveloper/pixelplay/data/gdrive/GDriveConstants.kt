package com.theveloper.pixelplay.data.gdrive

import com.theveloper.pixelplay.BuildConfig

object GDriveConstants {
    // Configured via local.properties (GDRIVE_WEB_CLIENT_ID) or fallback
    val WEB_CLIENT_ID: String = BuildConfig.GDRIVE_WEB_CLIENT_ID.ifEmpty {
        "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    }

    fun isConfigured(): Boolean {
        return WEB_CLIENT_ID.isNotBlank() && !WEB_CLIENT_ID.startsWith("YOUR_WEB_CLIENT_ID")
    }

    const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"

    val AUDIO_MIME_TYPES = setOf(
        "audio/mpeg", "audio/mp3", "audio/flac", "audio/wav", "audio/x-wav",
        "audio/mp4", "audio/x-m4a", "audio/aac", "audio/ogg",
        "audio/opus", "audio/x-aiff", "audio/alac", "audio/aiff",
        "audio/x-flac", "audio/vnd.wave", "audio/midi", "audio/x-midi",
        "audio/sp-midi", "audio/x-mid"
    )
}
