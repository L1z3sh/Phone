package com.example.runtime

import android.content.Context
import com.example.data.AetherRepository

class RuntimeCoordinator(
    context: Context,
    val repository: AetherRepository,
    private val currentUserId: String
) {
    private val appContext = context.applicationContext

    val networkMonitor = NetworkMonitor(appContext)
    val downloadEngine = FileDownloadEngine(appContext, repository)
    val webViewManager = AetherWebViewManager(
        context = appContext,
        networkMonitor = networkMonitor,
        onDownloadRequested = { url, fileName, mimeType ->
            downloadEngine.startDownload(currentUserId, url, fileName, mimeType)
        }
    )
    val macroRunner = AfkMacroRunner(appContext, repository, webViewManager)

    init {
        AetherAfkService.onStopAllRequested = {
            macroRunner.stopAll(currentUserId)
        }
    }

    fun shutdown() {
        AetherAfkService.onStopAllRequested = null
        macroRunner.stopAll(currentUserId)
        webViewManager.destroyAll()
        networkMonitor.release()
    }
}
