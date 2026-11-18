package com.example.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.AetherRepository
import com.example.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class FileDownloadEngine(
    context: Context,
    private val repository: AetherRepository
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _activeProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val activeProgress: StateFlow<Map<String, Int>> = _activeProgress.asStateFlow()

    private fun downloadsDir(): File {
        val dir = File(appContext.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startDownload(
        userId: String,
        url: String,
        suggestedName: String,
        mimeType: String = "application/octet-stream"
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        val safeName = sanitizeFileName(suggestedName, cleanUrl)

        scope.launch {
            val createRes = repository.createDownloadRecord(
                userId = userId,
                fileName = safeName,
                url = cleanUrl,
                mimeType = mimeType
            )
            val recordId = createRes.getOrNull() ?: return@launch

            try {
                repository.updateDownloadRecord(
                    userId = userId,
                    downloadId = recordId,
                    status = DownloadStatus.DOWNLOADING,
                    sizeBytes = 0L,
                    localUri = ""
                )
                _activeProgress.update { it + (recordId to 0) }

                val request = Request.Builder().url(cleanUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty body")
                    val totalBytes = body.contentLength()
                    val destFile = uniqueFile(downloadsDir(), safeName)

                    var copied = 0L
                    var lastReportedPct = -1
                    body.byteStream().use { input ->
                        FileOutputStream(destFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (totalBytes > 0) {
                                    val pct = ((copied * 100) / totalBytes).toInt().coerceIn(0, 100)
                                    if (pct != lastReportedPct) {
                                        lastReportedPct = pct
                                        _activeProgress.update { it + (recordId to pct) }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    val contentUri = FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        destFile
                    )

                    _activeProgress.update { it - recordId }
                    repository.updateDownloadRecord(
                        userId = userId,
                        downloadId = recordId,
                        status = DownloadStatus.COMPLETED,
                        sizeBytes = copied,
                        localUri = contentUri.toString()
                    )
                }
            } catch (_: Exception) {
                _activeProgress.update { it - recordId }
                repository.updateDownloadRecord(
                    userId = userId,
                    downloadId = recordId,
                    status = DownloadStatus.FAILED,
                    sizeBytes = 0L,
                    localUri = ""
                )
            }
        }
    }

    fun openDownloadedFile(localUriString: String, mimeType: String): Boolean {
        if (localUriString.isBlank()) return false
        return try {
            val uri = Uri.parse(localUriString)
            val resolvedMime = mimeType.ifBlank {
                val ext = MimeTypeMap.getFileExtensionFromUrl(localUriString)
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitizeFileName(rawName: String, url: String): String {
        val candidate = rawName.ifBlank {
            Uri.parse(url).lastPathSegment ?: "aether_download_${System.currentTimeMillis()}.bin"
        }
        return candidate.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(120)
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        if (!file.exists()) return file
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (file.exists() && index < 999) {
            file = File(dir, "${base}_$index$ext")
            index++
        }
        return file
    }
}
