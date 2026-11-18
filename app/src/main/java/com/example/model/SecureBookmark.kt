package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class BookmarkCategory(val label: String) {
    GAMING("Web Games"),
    CLOUD("Cloud Portals"),
    TOOLS("Utilities"),
    GENERAL("General");

    companion object {
        fun fromRaw(raw: String): BookmarkCategory =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

@Keep
@IgnoreExtraProperties
data class SecureBookmark(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val url: String = "",
    val category: String = BookmarkCategory.GENERAL.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): BookmarkCategory = BookmarkCategory.fromRaw(category)
}

enum class DownloadStatus(val label: String) {
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    COMPLETED("Completed"),
    FAILED("Failed");

    companion object {
        fun fromRaw(raw: String): DownloadStatus =
            entries.firstOrNull { it.name == raw } ?: QUEUED
    }
}

@Keep
@IgnoreExtraProperties
data class DownloadRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fileName: String = "",
    val url: String = "",
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val status: String = DownloadStatus.QUEUED.name,
    val localUri: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): DownloadStatus = DownloadStatus.fromRaw(status)
}
