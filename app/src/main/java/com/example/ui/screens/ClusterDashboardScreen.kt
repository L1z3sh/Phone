package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.AfkMacro
import com.example.model.InstanceProfileMode
import com.example.model.NetworkTelemetry
import com.example.model.ResolutionPreset
import com.example.model.UserAgentMode
import com.example.model.VirtualInstance
import com.example.runtime.ActiveInstanceRuntime
import com.example.ui.components.InstanceEditorDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryStatTile
import com.example.ui.components.formatDurationHms
import com.example.ui.theme.AmberBatch
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldAfk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.UiState

@Composable
fun ClusterDashboardScreen(
    instancesState: UiState<List<VirtualInstance>>,
    macros: List<AfkMacro>,
    activeRuntimes: Map<String, ActiveInstanceRuntime>,
    networkTelemetry: NetworkTelemetry,
    onOpenViewport: (VirtualInstance) -> Unit,
    onCreateInstance: (
        name: String,
        profileMode: InstanceProfileMode,
        resolutionPreset: ResolutionPreset,
        userAgentMode: UserAgentMode,
        targetUrl: String,
        assignedMacroId: String,
        keepAwake: Boolean,
        adShieldEnabled: Boolean,
        httpsOnly: Boolean
    ) -> Unit,
    onUpdateInstance: (
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
    ) -> Unit,
    onStartInstance: (VirtualInstance, Boolean) -> Unit,
    onStopInstance: (String) -> Unit,
    onCloneInstance: (VirtualInstance) -> Unit,
    onDeleteInstance: (String) -> Unit,
    onBatchStartAll: (List<VirtualInstance>) -> Unit,
    onStopAll: () -> Unit
) {
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }
    var editingInstance by remember { mutableStateOf<VirtualInstance?>(null) }

    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val instances = (instancesState as? UiState.Success)?.data ?: emptyList()
    val totalActiveCount = activeRuntimes.size
    val totalCycles = instances.sumOf { it.cycleCount } +
        activeRuntimes.values.sumOf { it.cyclesCompleted }
    val totalUptimeSec = instances.sumOf { it.totalRunSeconds } +
        activeRuntimes.values.sumOf { it.sessionSeconds }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Cluster Header Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_cloud_cluster_hero_1795001908127),
                            contentDescription = "Virtual Node Cluster Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            ObsidianSurface.copy(alpha = 0.92f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Virtual Phone Cluster",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Unlimited AFK background runtime & multi-node batch engine",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Telemetry grid
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatTile(
                                icon = Icons.Rounded.PhoneAndroid,
                                label = "ACTIVE NODES",
                                value = "$totalActiveCount / ${instances.size}",
                                accent = EmeraldAfk,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatTile(
                                icon = Icons.Rounded.AllInclusive,
                                label = "AFK UPTIME",
                                value = formatDurationHms(totalUptimeSec),
                                accent = CyanPulse,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatTile(
                                icon = Icons.Rounded.AutoAwesomeMotion,
                                label = "MACRO CYCLES",
                                value = totalCycles.toString(),
                                accent = AmberBatch,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatTile(
                                icon = Icons.Rounded.Wifi,
                                label = networkTelemetry.transportLabel.uppercase(),
                                value = if (networkTelemetry.isConnected) "${networkTelemetry.linkDownMbps} Mbps" else "Offline",
                                accent = if (networkTelemetry.isConnected) EmeraldAfk else RoseAlert,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Batch Command Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    editingInstance = null
                                    showEditor = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanPulse,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("new_instance_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Node")
                            }

                            FilledTonalButton(
                                onClick = { onBatchStartAll(instances) },
                                enabled = instances.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("batch_start_all_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start All AFK")
                            }

                            if (totalActiveCount > 0) {
                                IconButton(
                                    onClick = onStopAll,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .testTag("stop_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = "Stop All Instances",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notification permission banner on Android 13+ for smooth AFK background service status
        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable AFK Background Status",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Allow notifications so the foreground service stays visible while grinding in the background.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        TextButton(
                            onClick = {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        ) {
                            Text("Allow")
                        }
                    }
                }
            }
        }

        // State-driven list
        when (instancesState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyanPulse)
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = instancesState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                if (instances.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "No Virtual Nodes Provisioned Yet",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "Spin up an isolated virtual sandbox for AFK web grinding, multi-instance automation, or shielded browsing & downloads.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            onCreateInstance(
                                                "AFK Grind Node #1",
                                                InstanceProfileMode.GAME_AFK,
                                                ResolutionPreset.FHD_1080,
                                                UserAgentMode.ANDROID_MOBILE,
                                                "https://example.org",
                                                "",
                                                true,
                                                true,
                                                true
                                            )
                                        },
                                        modifier = Modifier.testTag("quick_afk_template_button")
                                    ) {
                                        Text("Launch AFK Template")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onCreateInstance(
                                                "Secure Wi-Fi Sandbox",
                                                InstanceProfileMode.SECURE_BROWSE,
                                                ResolutionPreset.DESKTOP_16_9,
                                                UserAgentMode.DESKTOP_CHROME,
                                                "https://duckduckgo.com",
                                                "",
                                                false,
                                                true,
                                                true
                                            )
                                        }
                                    ) {
                                        Text("Secure Web Node")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(instances, key = { it.id }) { instance ->
                        val runtime = activeRuntimes[instance.id]
                        val linkedMacro = macros.firstOrNull { it.id == instance.assignedMacroId }
                        VirtualInstanceCard(
                            instance = instance,
                            runtime = runtime,
                            linkedMacroName = linkedMacro?.name,
                            onOpenViewport = { onOpenViewport(instance) },
                            onStartAfkBackground = { onStartInstance(instance, true) },
                            onStop = { onStopInstance(instance.id) },
                            onEdit = {
                                editingInstance = instance
                                showEditor = true
                            },
                            onClone = { onCloneInstance(instance) },
                            onDelete = { onDeleteInstance(instance.id) }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        InstanceEditorDialog(
            initial = editingInstance,
            macros = macros,
            onDismiss = { showEditor = false },
            onSave = { name, profile, res, ua, url, macroId, keepAwake, adShield, httpsOnly ->
                val currentEdit = editingInstance
                if (currentEdit == null) {
                    onCreateInstance(name, profile, res, ua, url, macroId, keepAwake, adShield, httpsOnly)
                } else {
                    onUpdateInstance(
                        currentEdit.id,
                        name,
                        profile,
                        res,
                        ua,
                        url,
                        macroId,
                        keepAwake,
                        adShield,
                        httpsOnly
                    )
                }
                showEditor = false
            }
        )
    }
}

@Composable
private fun VirtualInstanceCard(
    instance: VirtualInstance,
    runtime: ActiveInstanceRuntime?,
    linkedMacroName: String?,
    onOpenViewport: () -> Unit,
    onStartAfkBackground: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit
) {
    val isRunning = runtime != null
    val sessionTime = runtime?.sessionSeconds ?: 0L
    val totalTime = instance.totalRunSeconds + sessionTime
    val cycles = instance.cycleCount + (runtime?.cyclesCompleted ?: 0L)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isRunning) 1.5.dp else 1.dp,
                color = if (isRunning) EmeraldAfk.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.large
            )
            .testTag("instance_card_${instance.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = instance.profileEnum().badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = instance.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${instance.resolutionEnum().label} • ${instance.userAgentEnum().label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(status = instance.statusEnum(), isActiveRuntime = isRunning)
            }

            // Target URL & Shield strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (instance.adShieldEnabled) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Ad Shield Active",
                        tint = CyanPulse,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = instance.targetUrl,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Live runtime step or macro badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = runtime?.currentStepLabel
                        ?: linkedMacroName?.let { "Macro: $it" }
                        ?: "No macro assigned",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isRunning) CyanPulse else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "∞ ${formatDurationHms(totalTime)} • $cycles cyc",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenViewport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_viewport_${instance.id}")
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Screen")
                }

                if (isRunning) {
                    FilledTonalButton(
                        onClick = onStop,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.testTag("stop_instance_${instance.id}")
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onStartAfkBackground,
                        modifier = Modifier.testTag("afk_bg_${instance.id}")
                    ) {
                        Icon(Icons.Rounded.AllInclusive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AFK Run")
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Configure ${instance.name}")
                }
                IconButton(onClick = onClone) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Clone ${instance.name}")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${instance.name}",
                        tint = RoseAlert
                    )
                }
            }
        }
    }
}
