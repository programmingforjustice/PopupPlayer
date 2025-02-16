package com.example.app

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)