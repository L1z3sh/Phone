package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AetherRepository
import com.example.model.AfkMacro
import com.example.model.BookmarkCategory
import com.example.model.DownloadRecord
import com.example.model.InstanceProfileMode
import com.example.model.NetworkTelemetry
import com.example.model.ResolutionPreset
import com.example.model.SecureBookmark
import com.example.model.UserAgentMode
import com.example.model.VirtualInstance
import com.example.runtime.ActiveInstanceRuntime
import com.example.runtime.MacroLogEntry
import com.example.runtime.RuntimeCoordinator
import com.example.runtime.TapPulseEvent
import com.example.runtime.ViewportRuntimeInfo
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class AetherViewModel(
    private val repository: AetherRepository,
    val currentUserId: String,
    val runtime: RuntimeCoordinator
) : ViewModel() {

    val instancesState: StateFlow<UiState<List<VirtualInstance>>> =
        observeUiState(repository.observeInstances(currentUserId), "instances")

    val macrosState: StateFlow<UiState<List<AfkMacro>>> =
        observeUiState(repository.observeMacros(currentUserId), "macros")

    val bookmarksState: StateFlow<UiState<List<SecureBookmark>>> =
        observeUiState(repository.observeBookmarks(currentUserId), "bookmarks")

    val downloadsState: StateFlow<UiState<List<DownloadRecord>>> =
        observeUiState(repository.observeDownloads(currentUserId), "downloads")

    val activeRuntimes: StateFlow<Map<String, ActiveInstanceRuntime>> =
        runtime.macroRunner.activeRuntimes

    val macroLogs: StateFlow<List<MacroLogEntry>> =
        runtime.macroRunner.logs

    val networkTelemetry: StateFlow<NetworkTelemetry> =
        runtime.networkMonitor.telemetry

    val viewportInfo: StateFlow<Map<String, ViewportRuntimeInfo>> =
        runtime.webViewManager.viewportInfo

    val tapPulses: SharedFlow<TapPulseEvent> =
        runtime.webViewManager.tapPulses

    val downloadProgress: StateFlow<Map<String, Int>> =
        runtime.downloadEngine.activeProgress

    private val _selectedInstanceId = MutableStateFlow<String?>(null)
    val selectedInstanceId: StateFlow<String?> = _selectedInstanceId.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private fun <T> observeUiState(flow: Flow<T>, label: String): StateFlow<UiState<T>> =
        flow
            .map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    fun selectViewportInstance(instanceId: String) {
        _selectedInstanceId.value = instanceId
    }

    fun createInstance(
        name: String,
        profileMode: InstanceProfileMode,
        resolutionPreset: ResolutionPreset,
        userAgentMode: UserAgentMode,
        targetUrl: String,
        assignedMacroId: String,
        keepAwake: Boolean,
        adShieldEnabled: Boolean,
        httpsOnly: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.createInstance(
                userId = currentUserId,
                name = name,
                profileMode = profileMode,
                resolutionPreset = resolutionPreset,
                userAgentMode = userAgentMode,
                targetUrl = targetUrl,
                assignedMacroId = assignedMacroId,
                keepAwake = keepAwake,
                adShieldEnabled = adShieldEnabled,
                httpsOnly = httpsOnly
            )
            res.onSuccess { newId ->
                _selectedInstanceId.value = newId
                _events.tryEmit("Virtual instance '$name' created")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Failed to create instance")
            }
        }
    }

    fun updateInstanceConfig(
        instanceId: String,
        name: String,
        profileMode: InstanceProfileMode,
        resolutionPreset: ResolutionPreset,
        userAgentMode: UserAgentMode,
        targetUrl: String,
        assignedMacroId: String,
        keepAwake: Boolean,
        adShieldEnabled: Boolean,
        httpsOnly: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.updateInstanceConfig(
                userId = currentUserId,
                instanceId = instanceId,
                name = name,
                profileMode = profileMode,
                resolutionPreset = resolutionPreset,
                userAgentMode = userAgentMode,
                targetUrl = targetUrl,
                assignedMacroId = assignedMacroId,
                keepAwake = keepAwake,
                adShieldEnabled = adShieldEnabled,
                httpsOnly = httpsOnly
            )
            res.onSuccess {
                _events.tryEmit("Updated '$name'")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Update failed")
            }
        }
    }

    fun cloneInstance(source: VirtualInstance) {
        createInstance(
            name = "${source.name.take(40)} Clone",
            profileMode = source.profileEnum(),
            resolutionPreset = source.resolutionEnum(),
            userAgentMode = source.userAgentEnum(),
            targetUrl = source.targetUrl,
            assignedMacroId = source.assignedMacroId,
            keepAwake = source.keepAwake,
            adShieldEnabled = source.adShieldEnabled,
            httpsOnly = source.httpsOnly
        )
    }

    fun deleteInstance(instanceId: String) {
        runtime.macroRunner.stopInstance(currentUserId, instanceId, persistStopped = false)
        runtime.webViewManager.destroyInstanceWebView(instanceId)
        if (_selectedInstanceId.value == instanceId) {
            _selectedInstanceId.value = null
        }
        viewModelScope.launch {
            repository.deleteInstance(currentUserId, instanceId)
                .onSuccess { _events.tryEmit("Instance removed") }
                .onFailure { _events.tryEmit(it.message ?: "Delete failed") }
        }
    }

    fun startInstance(
        instance: VirtualInstance,
        macros: List<AfkMacro>,
        backgroundAfk: Boolean
    ) {
        runtime.webViewManager.getOrCreateWebView(instance)
        val assignedMacro = macros.firstOrNull { it.id == instance.assignedMacroId }
        runtime.macroRunner.startInstance(currentUserId, instance, assignedMacro, backgroundAfk)
        _selectedInstanceId.value = instance.id
        _events.tryEmit(
            if (backgroundAfk) "AFK background mode active for ${instance.name}"
            else "Launched ${instance.name}"
        )
    }

    fun stopInstance(instanceId: String) {
        runtime.macroRunner.stopInstance(currentUserId, instanceId, persistStopped = true)
        _events.tryEmit("Instance placed in standby")
    }

    fun batchStartAll(instances: List<VirtualInstance>, macros: List<AfkMacro>) {
        if (instances.isEmpty()) {
            _events.tryEmit("Create at least one virtual instance first")
            return
        }
        instances.forEach { inst ->
            runtime.webViewManager.getOrCreateWebView(inst)
            val macro = macros.firstOrNull { it.id == inst.assignedMacroId }
            runtime.macroRunner.startInstance(currentUserId, inst, macro, backgroundAfk = true)
        }
        _events.tryEmit("Batch started ${instances.size} AFK instances")
    }

    fun batchRunMacro(
        instances: List<VirtualInstance>,
        macro: AfkMacro?,
        backgroundAfk: Boolean = true
    ) {
        if (instances.isEmpty()) {
            _events.tryEmit("Select at least one target instance")
            return
        }
        instances.forEach { runtime.webViewManager.getOrCreateWebView(it) }
        runtime.macroRunner.batchRunMacro(currentUserId, instances, macro, backgroundAfk)
        _events.tryEmit("Batch macro running on ${instances.size} instances")
    }

    fun stopAllInstances() {
        runtime.macroRunner.stopAll(currentUserId)
        _events.tryEmit("All active AFK instances stopped")
    }

    fun navigateInstance(instanceId: String, url: String) {
        runtime.webViewManager.navigate(instanceId, url)
        viewModelScope.launch {
            repository.updateInstanceUrl(currentUserId, instanceId, url)
        }
    }

    fun createMacro(
        name: String,
        description: String,
        loopForever: Boolean,
        maxLoops: Long,
        intervalMs: Long,
        steps: List<String>
    ) {
        viewModelScope.launch {
            repository.createMacro(
                userId = currentUserId,
                name = name,
                description = description,
                loopForever = loopForever,
                maxLoops = maxLoops,
                intervalMs = intervalMs,
                steps = steps
            ).onSuccess {
                _events.tryEmit("Macro '$name' saved")
            }.onFailure {
                _events.tryEmit(it.message ?: "Failed to save macro")
            }
        }
    }

    fun updateMacro(
        macroId: String,
        name: String,
        description: String,
        loopForever: Boolean,
        maxLoops: Long,
        intervalMs: Long,
        steps: List<String>
    ) {
        viewModelScope.launch {
            repository.updateMacro(
                userId = currentUserId,
                macroId = macroId,
                name = name,
                description = description,
                loopForever = loopForever,
                maxLoops = maxLoops,
                intervalMs = intervalMs,
                steps = steps
            ).onSuccess {
                _events.tryEmit("Macro '$name' updated")
            }.onFailure {
                _events.tryEmit(it.message ?: "Failed to update macro")
            }
        }
    }

    fun deleteMacro(macroId: String) {
        viewModelScope.launch {
            repository.deleteMacro(currentUserId, macroId)
                .onSuccess { _events.tryEmit("Macro deleted") }
                .onFailure { _events.tryEmit(it.message ?: "Delete failed") }
        }
    }

    fun addBookmark(title: String, url: String, category: BookmarkCategory) {
        viewModelScope.launch {
            repository.addBookmark(currentUserId, title, url, category)
                .onSuccess { _events.tryEmit("Bookmark saved") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to save bookmark") }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            repository.deleteBookmark(currentUserId, bookmarkId)
        }
    }

    fun startDirectDownload(url: String, fileName: String) {
        val normalized = runtime.webViewManager.normalizeUrl(url, httpsOnly = true)
        runtime.downloadEngine.startDownload(
            userId = currentUserId,
            url = normalized,
            suggestedName = fileName
        )
        _events.tryEmit("Download queued")
    }

    fun openDownload(record: DownloadRecord) {
        val opened = runtime.downloadEngine.openDownloadedFile(record.localUri, record.mimeType)
        if (!opened) {
            _events.tryEmit("No compatible viewer installed for ${record.fileName}")
        }
    }

    fun deleteDownloadRecord(downloadId: String) {
        viewModelScope.launch {
            repository.deleteDownloadRecord(currentUserId, downloadId)
        }
    }

    fun refreshNetworkTelemetry() {
        runtime.networkMonitor.refreshSnapshot()
    }

    override fun onCleared() {
        runtime.shutdown()
        super.onCleared()
    }

    private companion object {
        const val TAG = "AetherVM"
    }
}
