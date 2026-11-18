package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AfkMacro
import com.example.model.MacroActionType
import com.example.model.MacroStep
import com.example.model.VirtualInstance
import com.example.runtime.MacroLogEntry
import com.example.ui.theme.AmberBatch
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldAfk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.UiState
import kotlin.math.roundToInt

@Composable
fun MacrosScreen(
    macrosState: UiState<List<AfkMacro>>,
    instances: List<VirtualInstance>,
    logs: List<MacroLogEntry>,
    onCreateMacro: (
        name: String,
        description: String,
        loopForever: Boolean,
        maxLoops: Long,
        intervalMs: Long,
        steps: List<String>
    ) -> Unit,
    onUpdateMacro: (
        macroId: String,
        name: String,
        description: String,
        loopForever: Boolean,
        maxLoops: Long,
        intervalMs: Long,
        steps: List<String>
    ) -> Unit,
    onDeleteMacro: (String) -> Unit,
    onBatchRunMacro: (List<VirtualInstance>, AfkMacro) -> Unit
) {
    var showBuilder by remember { mutableStateOf(false) }
    var editingMacro by remember { mutableStateOf<AfkMacro?>(null) }
    var batchTargetMacro by remember { mutableStateOf<AfkMacro?>(null) }

    val macros = (macrosState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Studio Card
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
                            .height(124.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_macro_automation_art_1795001908017),
                            contentDescription = "Automated Macro Pipeline Artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, ObsidianSurface.copy(alpha = 0.92f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text("Batch Macro Studio", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = "Automate taps, scrolls, scripts & HTTP pings across multiple nodes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                editingMacro = null
                                showBuilder = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulse,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_macro_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Macro")
                        }

                        OutlinedButton(
                            onClick = {
                                onCreateMacro(
                                    "AFK Auto-Collector",
                                    "Taps primary button, scrolls feed, and waits 4 seconds",
                                    true,
                                    1000L,
                                    4000L,
                                    listOf(
                                        MacroStep(MacroActionType.TAP, "0.50,0.72", "Tap Action").encode(),
                                        MacroStep(MacroActionType.WAIT, "1500", "Wait 1.5s").encode(),
                                        MacroStep(MacroActionType.SCROLL, "280", "Scroll View").encode()
                                    )
                                )
                            },
                            modifier = Modifier.testTag("starter_macro_button")
                        ) {
                            Text("+ Preset Loop")
                        }
                    }
                }
            }
        }

        when (macrosState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
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
                            text = macrosState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                if (macros.isEmpty()) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("No Custom Macros Yet", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Build a step sequence with the Visual Tap Pad or add the Preset Loop to start batch automating instances.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(macros, key = { it.id }) { macro ->
                        MacroCard(
                            macro = macro,
                            onBatchRun = { batchTargetMacro = macro },
                            onEdit = {
                                editingMacro = macro
                                showBuilder = true
                            },
                            onDelete = { onDeleteMacro(macro.id) }
                        )
                    }
                }
            }
        }

        // Live Batch Execution Console
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = ObsidianBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = EmeraldAfk,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Live Batch Execution Log", style = MaterialTheme.typography.titleMedium)
                    }

                    if (logs.isEmpty()) {
                        Text(
                            text = "No batch runs yet. Launch any macro to stream live execution telemetry.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        logs.take(12).forEach { log ->
                            Text(
                                text = "[${log.timestamp}] ${log.instanceName}: ${log.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.isError) RoseAlert else EmeraldAfk.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBuilder) {
        MacroBuilderDialog(
            initial = editingMacro,
            onDismiss = { showBuilder = false },
            onSave = { name, desc, loopForever, maxLoops, intervalMs, steps ->
                val cur = editingMacro
                if (cur == null) {
                    onCreateMacro(name, desc, loopForever, maxLoops, intervalMs, steps)
                } else {
                    onUpdateMacro(cur.id, name, desc, loopForever, maxLoops, intervalMs, steps)
                }
                showBuilder = false
            }
        )
    }

    batchTargetMacro?.let { macro ->
        BatchRunTargetDialog(
            macro = macro,
            instances = instances,
            onDismiss = { batchTargetMacro = null },
            onLaunch = { selected ->
                onBatchRunMacro(selected, macro)
                batchTargetMacro = null
            }
        )
    }
}

@Composable
private fun MacroCard(
    macro: AfkMacro,
    onBatchRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val decoded = remember(macro.steps) { macro.decodedSteps() }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
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
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesomeMotion,
                        contentDescription = null,
                        tint = AmberBatch,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(macro.name, style = MaterialTheme.typography.titleLarge)
                        if (macro.description.isNotBlank()) {
                            Text(
                                text = macro.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    if (macro.loopForever) {
                        Icon(
                            Icons.Rounded.AllInclusive,
                            contentDescription = "Unlimited Loops",
                            tint = EmeraldAfk,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (macro.loopForever) "INF" else "${macro.maxLoops}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Steps preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                decoded.take(5).forEachIndexed { idx, step ->
                    Text(
                        text = "${idx + 1}. [${step.type.name}] ${step.label} (${step.arg})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (decoded.size > 5) {
                    Text(
                        text = "+ ${decoded.size - 5} more steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPulse
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cooldown: ${macro.intervalMs}ms • Runs: ${macro.runCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = onBatchRun,
                        modifier = Modifier.testTag("batch_run_${macro.id}")
                    ) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Batch Run")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${macro.name}")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${macro.name}", tint = RoseAlert)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacroBuilderDialog(
    initial: AfkMacro?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        loopForever: Boolean,
        maxLoops: Long,
        intervalMs: Long,
        steps: List<String>
    ) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "AFK Batch Script") }
    var desc by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var loopForever by remember(initial) { mutableStateOf(initial?.loopForever ?: true) }
    var maxLoopsStr by remember(initial) { mutableStateOf((initial?.maxLoops ?: 100L).toString()) }
    var intervalSec by remember(initial) {
        mutableFloatStateOf(((initial?.intervalMs ?: 3000L) / 1000f).coerceIn(0.5f, 30f))
    }

    val steps = remember(initial) {
        mutableStateListOf<MacroStep>().apply {
            if (initial != null && initial.steps.isNotEmpty()) {
                addAll(initial.decodedSteps())
            } else {
                add(MacroStep(MacroActionType.TAP, "0.50,0.65", "Tap Target"))
                add(MacroStep(MacroActionType.WAIT, "2000", "Wait 2s"))
            }
        }
    }

    var newActionType by remember { mutableStateOf(MacroActionType.TAP) }
    var newActionArg by remember { mutableStateOf("0.50,0.65") }
    var newActionLabel by remember { mutableStateOf("Tap Point") }
    var tapX by remember { mutableFloatStateOf(0.5f) }
    var tapY by remember { mutableFloatStateOf(0.65f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Build Custom Macro" else "Edit Macro") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 50) name = it },
                    label = { Text("Macro Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { if (it.length <= 200) desc = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Unlimited Loop (∞ AFK)", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = loopForever, onCheckedChange = { loopForever = it })
                }

                if (!loopForever) {
                    OutlinedTextField(
                        value = maxLoopsStr,
                        onValueChange = { maxLoopsStr = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("Max Loop Count") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = "Loop Cooldown: ${(intervalSec * 1000).roundToInt()} ms",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = intervalSec,
                    onValueChange = { intervalSec = it },
                    valueRange = 0.5f..30f
                )

                Text("Add Step (${steps.size}/30)", style = MaterialTheme.typography.titleMedium)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MacroActionType.entries.forEach { actionType ->
                        FilterChip(
                            selected = newActionType == actionType,
                            onClick = {
                                newActionType = actionType
                                newActionArg = when (actionType) {
                                    MacroActionType.TAP -> String.format("%.2f,%.2f", tapX, tapY)
                                    MacroActionType.WAIT -> "2000"
                                    MacroActionType.SCROLL -> "300"
                                    MacroActionType.RELOAD -> "0"
                                    MacroActionType.NAV -> "https://example.org"
                                    MacroActionType.JS -> "window.scrollBy(0, 150);"
                                    MacroActionType.PING -> "https://example.org"
                                }
                                newActionLabel = actionType.label
                            },
                            label = { Text(actionType.label) }
                        )
                    }
                }

                // Interactive Visual Tap Coordinate Pad when TAP is selected
                if (newActionType == MacroActionType.TAP) {
                    Text(
                        text = "Visual Screen Coordinate Pad (Tap to set X,Y)",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPulse
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(ObsidianBg)
                            .border(1.dp, CyanPulse.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val nx = (offset.x / size.width).coerceIn(0.05f, 0.95f)
                                    val ny = (offset.y / size.height).coerceIn(0.05f, 0.95f)
                                    tapX = nx
                                    tapY = ny
                                    newActionArg = String.format("%.2f,%.2f", nx, ny)
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Subtle grid
                            for (i in 1..3) {
                                val x = size.width * (i / 4f)
                                val y = size.height * (i / 4f)
                                drawLine(
                                    Color.White.copy(alpha = 0.08f),
                                    Offset(x, 0f),
                                    Offset(x, size.height)
                                )
                                drawLine(
                                    Color.White.copy(alpha = 0.08f),
                                    Offset(0f, y),
                                    Offset(size.width, y)
                                )
                            }
                            val target = Offset(size.width * tapX, size.height * tapY)
                            drawCircle(CyanPulse.copy(alpha = 0.3f), 18.dp.toPx(), target)
                            drawCircle(CyanPulse, 6.dp.toPx(), target)
                        }
                    }
                }

                OutlinedTextField(
                    value = newActionArg,
                    onValueChange = { newActionArg = it },
                    label = { Text(newActionType.hint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newActionLabel,
                        onValueChange = { newActionLabel = it },
                        label = { Text("Step Label") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            if (steps.size < 30) {
                                steps.add(MacroStep(newActionType, newActionArg, newActionLabel))
                            }
                        },
                        enabled = steps.size < 30
                    ) {
                        Text("+ Add")
                    }
                }

                // Current Steps list
                steps.forEachIndexed { idx, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${idx + 1}. ${step.type.name}: ${step.arg}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { steps.removeAt(idx) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Remove Step",
                                tint = RoseAlert,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val loops = maxLoopsStr.toLongOrNull()?.coerceIn(1L, 100_000L) ?: 100L
                    val intervalMs = (intervalSec * 1000L).toLong().coerceIn(200L, 3_600_000L)
                    onSave(
                        name.trim(),
                        desc.trim(),
                        loopForever,
                        loops,
                        intervalMs,
                        steps.map { it.encode() }
                    )
                },
                enabled = name.isNotBlank() && steps.isNotEmpty()
            ) {
                Text("Save Macro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BatchRunTargetDialog(
    macro: AfkMacro,
    instances: List<VirtualInstance>,
    onDismiss: () -> Unit,
    onLaunch: (List<VirtualInstance>) -> Unit
) {
    val selectedIds = remember(instances) {
        mutableStateListOf<String>().apply {
            addAll(instances.map { it.id })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Run '${macro.name}'") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (instances.isEmpty()) {
                    Text("No virtual instances available. Create an instance in the Cluster tab first.")
                } else {
                    Text(
                        text = "Select virtual instances to execute this macro concurrently in AFK background mode:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    instances.forEach { inst ->
                        val checked = selectedIds.contains(inst.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    if (checked) selectedIds.remove(inst.id)
                                    else selectedIds.add(inst.id)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (!selectedIds.contains(inst.id)) selectedIds.add(inst.id)
                                    } else {
                                        selectedIds.remove(inst.id)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(inst.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = inst.targetUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targets = instances.filter { selectedIds.contains(it.id) }
                    onLaunch(targets)
                },
                enabled = selectedIds.isNotEmpty()
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Launch Batch (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
