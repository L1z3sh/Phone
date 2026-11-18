package com.example.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.AetherRepository
import com.example.model.AfkMacro
import com.example.model.InstanceStatus
import com.example.model.MacroActionType
import com.example.model.VirtualInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ActiveInstanceRuntime(
    val instanceId: String,
    val instanceName: String,
    val isBackgroundAfk: Boolean,
    val sessionSeconds: Long = 0L,
    val cyclesCompleted: Long = 0L,
    val activeMacroName: String? = null,
    val currentStepLabel: String = "Standby"
)

data class MacroLogEntry(
    val id: Long,
    val timestamp: String,
    val instanceName: String,
    val message: String,
    val isError: Boolean = false
)

class AfkMacroRunner(
    context: Context,
    private val repository: AetherRepository,
    private val webViewManager: AetherWebViewManager
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val instanceJobs = ConcurrentHashMap<String, Job>()
    private val _activeRuntimes = MutableStateFlow<Map<String, ActiveInstanceRuntime>>(emptyMap())
    val activeRuntimes: StateFlow<Map<String, ActiveInstanceRuntime>> = _activeRuntimes.asStateFlow()

    private val _logs = MutableStateFlow<List<MacroLogEntry>>(emptyList())
    val logs: StateFlow<List<MacroLogEntry>> = _logs.asStateFlow()

    private var logSequence = 0L
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun startInstance(
        userId: String,
        instance: VirtualInstance,
        macro: AfkMacro?,
        backgroundAfk: Boolean
    ) {
        stopInstance(userId, instance.id, persistStopped = false)

        val status = if (backgroundAfk) InstanceStatus.AFK_BACKGROUND else InstanceStatus.RUNNING
        scope.launch {
            repository.updateInstanceRuntimeState(userId, instance.id, status)
        }

        _activeRuntimes.update {
            it + (instance.id to ActiveInstanceRuntime(
                instanceId = instance.id,
                instanceName = instance.name,
                isBackgroundAfk = backgroundAfk,
                sessionSeconds = 0L,
                cyclesCompleted = 0L,
                activeMacroName = macro?.name,
                currentStepLabel = if (macro != null) "Initializing ${macro.name}" else "Live Sandbox Active"
            ))
        }

        appendLog(instance.name, "Started (${status.label})${macro?.let { " + macro '${it.name}'" } ?: ""}")
        syncForegroundService()

        val job = scope.launch {
            var localSeconds = 0L
            var localCycles = 0L
            val baseTotalSeconds = instance.totalRunSeconds
            val baseCycles = instance.cycleCount

            // Launch heartbeat timer for unlimited uptime tracking
            val tickerJob = launch {
                while (isActive) {
                    delay(1000L)
                    localSeconds++
                    _activeRuntimes.update { map ->
                        val cur = map[instance.id] ?: return@update map
                        map + (instance.id to cur.copy(sessionSeconds = localSeconds))
                    }
                    // Periodic Firestore checkpoint every 20 seconds
                    if (localSeconds % 20L == 0L) {
                        repository.updateInstanceRuntimeState(
                            userId = userId,
                            instanceId = instance.id,
                            status = status,
                            totalRunSeconds = baseTotalSeconds + localSeconds,
                            cycleCount = baseCycles + localCycles
                        )
                    }
                    syncForegroundService()
                }
            }

            try {
                if (macro != null && macro.steps.isNotEmpty()) {
                    val steps = macro.decodedSteps()
                    val maxIterations = if (macro.loopForever) Long.MAX_VALUE else macro.maxLoops.coerceAtLeast(1L)
                    var loopIndex = 0L

                    while (isActive && loopIndex < maxIterations) {
                        loopIndex++
                        for (step in steps) {
                            if (!isActive) break
                            updateStepLabel(instance.id, "Loop #$loopIndex: ${step.label}")
                            executeMacroStep(instance, step.type, step.arg)
                        }
                        localCycles++
                        _activeRuntimes.update { map ->
                            val cur = map[instance.id] ?: return@update map
                            map + (instance.id to cur.copy(cyclesCompleted = localCycles))
                        }
                        repository.incrementMacroRunCount(userId, macro.id)
                        appendLog(instance.name, "Completed macro '${macro.name}' cycle #$localCycles")

                        updateStepLabel(instance.id, "Cooldown ${macro.intervalMs}ms")
                        delay(macro.intervalMs.coerceAtLeast(200L))
                    }
                    appendLog(instance.name, "Macro '${macro.name}' finished ($localCycles loops)")
                } else {
                    // Continuous AFK keep-alive loop without macro steps
                    while (isActive) {
                        updateStepLabel(instance.id, "AFK Session Active (Unlimited)")
                        delay(15_000L)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                tickerJob.cancel()
                repository.updateInstanceRuntimeState(
                    userId = userId,
                    instanceId = instance.id,
                    status = InstanceStatus.STOPPED,
                    totalRunSeconds = baseTotalSeconds + localSeconds,
                    cycleCount = baseCycles + localCycles
                )
            }
        }

        instanceJobs[instance.id] = job
    }

    private suspend fun executeMacroStep(
        instance: VirtualInstance,
        type: MacroActionType,
        arg: String
    ) {
        when (type) {
            MacroActionType.TAP -> {
                val parts = arg.split(",")
                val x = parts.getOrNull(0)?.trim()?.toFloatOrNull() ?: 0.5f
                val y = parts.getOrNull(1)?.trim()?.toFloatOrNull() ?: 0.5f
                webViewManager.dispatchVirtualTap(instance.id, x, y)
                delay(180L)
            }
            MacroActionType.WAIT -> {
                val ms = arg.trim().toLongOrNull()?.coerceIn(100L, 600_000L) ?: 1000L
                delay(ms)
            }
            MacroActionType.SCROLL -> {
                val dy = arg.trim().toIntOrNull() ?: 320
                webViewManager.dispatchVirtualScroll(instance.id, dy)
                delay(250L)
            }
            MacroActionType.RELOAD -> {
                webViewManager.reload(instance.id)
                delay(800L)
            }
            MacroActionType.NAV -> {
                val target = arg.trim().ifBlank { instance.targetUrl }
                webViewManager.navigate(instance.id, target)
                delay(800L)
            }
            MacroActionType.JS -> {
                if (arg.isNotBlank()) {
                    webViewManager.evaluateScript(instance.id, arg)
                }
                delay(200L)
            }
            MacroActionType.PING -> {
                val target = arg.trim().ifBlank { instance.targetUrl }
                try {
                    val req = Request.Builder().url(target).get().build()
                    httpClient.newCall(req).execute().use { resp ->
                        appendLog(instance.name, "HTTP Ping ${resp.code} -> $target")
                    }
                } catch (e: Exception) {
                    appendLog(instance.name, "HTTP Ping failed: ${e.message}", isError = true)
                }
            }
        }
    }

    fun batchRunMacro(
        userId: String,
        instances: List<VirtualInstance>,
        macro: AfkMacro?,
        backgroundAfk: Boolean = true
    ) {
        if (instances.isEmpty()) return
        appendLog("Batch Engine", "Launching ${instances.size} instances${macro?.let { " with '${it.name}'" } ?: ""}")
        instances.forEach { inst ->
            startInstance(userId, inst, macro, backgroundAfk)
        }
    }

    fun stopInstance(userId: String, instanceId: String, persistStopped: Boolean = true) {
        val removedJob = instanceJobs.remove(instanceId)
        removedJob?.cancel()
        val prev = _activeRuntimes.value[instanceId]
        _activeRuntimes.update { it - instanceId }
        if (prev != null) {
            appendLog(prev.instanceName, "Stopped after ${prev.sessionSeconds}s (${prev.cyclesCompleted} cycles)")
        }
        if (persistStopped) {
            scope.launch {
                repository.updateInstanceRuntimeState(userId, instanceId, InstanceStatus.STOPPED)
            }
        }
        syncForegroundService()
    }

    fun stopAll(userId: String) {
        val activeIds = instanceJobs.keys.toList()
        activeIds.forEach { id ->
            stopInstance(userId, id, persistStopped = true)
        }
        AetherAfkService.stop(appContext)
    }

    private fun updateStepLabel(instanceId: String, label: String) {
        _activeRuntimes.update { map ->
            val cur = map[instanceId] ?: return@update map
            map + (instanceId to cur.copy(currentStepLabel = label))
        }
    }

    private fun appendLog(instanceName: String, message: String, isError: Boolean = false) {
        val entry = MacroLogEntry(
            id = ++logSequence,
            timestamp = timeFormatter.format(Date()),
            instanceName = instanceName,
            message = message,
            isError = isError
        )
        _logs.update { current ->
            (listOf(entry) + current).take(80)
        }
    }

    private fun syncForegroundService() {
        val active = _activeRuntimes.value.values.toList()
        if (active.isEmpty()) {
            AetherAfkService.stop(appContext)
            return
        }
        val totalCycles = active.sumOf { it.cyclesCompleted }
        val maxSeconds = active.maxOfOrNull { it.sessionSeconds } ?: 0L
        val intent = Intent(appContext, AetherAfkService::class.java).apply {
            action = AetherAfkService.ACTION_START_OR_UPDATE
            putExtra(AetherAfkService.EXTRA_ACTIVE_COUNT, active.size)
            putExtra(AetherAfkService.EXTRA_CYCLES, totalCycles)
            putExtra(AetherAfkService.EXTRA_UPTIME_SEC, maxSeconds)
        }
        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (_: Exception) {
            // Foreground service start restricted by OS background policy when app is backgrounded
        }
    }
}
