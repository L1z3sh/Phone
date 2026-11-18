package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AfkMacro
import com.example.model.InstanceProfileMode
import com.example.model.InstanceStatus
import com.example.model.ResolutionPreset
import com.example.model.UserAgentMode
import com.example.model.VirtualInstance
import com.example.ui.theme.AmberBatch
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldAfk
import com.example.ui.theme.TextSecondary

fun formatDurationHms(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hrs = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return String.format("%02d:%02d:%02d", hrs, mins, secs)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

@Composable
fun StatusPill(status: InstanceStatus, isActiveRuntime: Boolean) {
    val (color, label) = when {
        status == InstanceStatus.AFK_BACKGROUND -> AmberBatch to "AFK BG"
        isActiveRuntime || status == InstanceStatus.RUNNING -> EmeraldAfk to "LIVE"
        else -> TextSecondary to "STANDBY"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TelemetryStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstanceEditorDialog(
    initial: VirtualInstance?,
    macros: List<AfkMacro>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        profileMode: InstanceProfileMode,
        resolutionPreset: ResolutionPreset,
        userAgentMode: UserAgentMode,
        targetUrl: String,
        assignedMacroId: String,
        keepAwake: Boolean,
        adShieldEnabled: Boolean,
        httpsOnly: Boolean
    ) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "Cloud Node 01") }
    var targetUrl by remember(initial) {
        mutableStateOf(initial?.targetUrl ?: "https://example.org")
    }
    var profileMode by remember(initial) {
        mutableStateOf(initial?.profileEnum() ?: InstanceProfileMode.GAME_AFK)
    }
    var resolutionPreset by remember(initial) {
        mutableStateOf(initial?.resolutionEnum() ?: ResolutionPreset.FHD_1080)
    }
    var userAgentMode by remember(initial) {
        mutableStateOf(initial?.userAgentEnum() ?: UserAgentMode.ANDROID_MOBILE)
    }
    var assignedMacroId by remember(initial) { mutableStateOf(initial?.assignedMacroId ?: "") }
    var keepAwake by remember(initial) { mutableStateOf(initial?.keepAwake ?: true) }
    var adShield by remember(initial) { mutableStateOf(initial?.adShieldEnabled ?: true) }
    var httpsOnly by remember(initial) { mutableStateOf(initial?.httpsOnly ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Create Virtual Instance" else "Configure Instance",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 50) name = it },
                    label = { Text("Instance Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instance_name_input")
                )

                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { if (it.length <= 500) targetUrl = it },
                    label = { Text("Target App / Game / Web URL") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instance_url_input")
                )

                Text("Profile Mode", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InstanceProfileMode.entries.forEach { mode ->
                        FilterChip(
                            selected = profileMode == mode,
                            onClick = { profileMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }

                Text("Virtual Screen Resolution", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResolutionPreset.entries.forEach { res ->
                        FilterChip(
                            selected = resolutionPreset == res,
                            onClick = { resolutionPreset = res },
                            label = { Text(res.label) }
                        )
                    }
                }

                Text("Browser Environment", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UserAgentMode.entries.forEach { ua ->
                        FilterChip(
                            selected = userAgentMode == ua,
                            onClick = { userAgentMode = ua },
                            label = { Text(ua.label) }
                        )
                    }
                }

                Text("Assigned AFK Macro", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = assignedMacroId.isEmpty(),
                        onClick = { assignedMacroId = "" },
                        label = { Text("None") }
                    )
                    macros.forEach { macro ->
                        FilterChip(
                            selected = assignedMacroId == macro.id,
                            onClick = { assignedMacroId = macro.id },
                            leadingIcon = if (assignedMacroId == macro.id) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text(macro.name) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { keepAwake = !keepAwake }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AFK Background Keep-Awake", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Keep CPU & macro loop alive via Foreground Service",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = keepAwake, onCheckedChange = { keepAwake = it })
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { adShield = !adShield }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ad & Tracker Shield", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Block telemetry & ad requests inside instance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = adShield, onCheckedChange = { adShield = it })
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { httpsOnly = !httpsOnly }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Strict HTTPS Upgrade", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Enforce encrypted TLS connections",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = httpsOnly, onCheckedChange = { httpsOnly = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && targetUrl.isNotBlank()) {
                        onSave(
                            name.trim(),
                            profileMode,
                            resolutionPreset,
                            userAgentMode,
                            targetUrl.trim(),
                            assignedMacroId,
                            keepAwake,
                            adShield,
                            httpsOnly
                        )
                    }
                },
                enabled = name.isNotBlank() && targetUrl.isNotBlank(),
                modifier = Modifier.testTag("save_instance_button")
            ) {
                Text("Save Instance")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
