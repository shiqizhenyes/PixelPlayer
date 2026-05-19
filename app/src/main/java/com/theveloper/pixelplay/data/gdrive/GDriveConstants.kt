package com.theveloper.pixelplay.data.gdrive

object GDriveConstants {
    // TODO: Replace with your Google Cloud Console OAuth2 Web Client ID
    const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    // Principle of least privilege: request `drive.file` (only files the
    // user explicitly opens via the Picker or files the app itself created)
    // instead of `drive.readonly` (read access to the entire Drive). The
    // music-folder use case is satisfied by a user-picked folder under
    // drive.file. NOTE: the actual scope granted is decided by the OAuth
    // configuration on the Web Client (above); this constant documents the
    // intent and is what the in-app authorization flow should request.
    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    @Deprecated(
        "Use SCOPE_DRIVE_FILE — drive.readonly grants access to the user's entire Drive.",
        replaceWith = ReplaceWith("SCOPE_DRIVE_FILE")
    )
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
