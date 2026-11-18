package com.example.ui.screens

import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.AfkMacro
import com.example.model.BookmarkCategory
import com.example.model.VirtualInstance
import com.example.runtime.ActiveInstanceRuntime
import com.example.runtime.AetherWebViewManager
import com.example.runtime.TapPulseEvent
import com.example.runtime.ViewportRuntimeInfo
import com.example.ui.components.formatDurationHms
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldAfk
import com.example.ui.theme.ObsidianBg
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ViewportScreen(
    instances: List<VirtualInstance>,
    macros: List<AfkMacro>,
    selectedInstanceId: String?,
    activeRuntimes: Map<String, ActiveInstanceRuntime>,
    viewportInfoMap: Map<String, ViewportRuntimeInfo>,
    tapPulses: SharedFlow<TapPulseEvent>,
    webViewManager: AetherWebViewManager,
    onSelectInstance: (String) -> Unit,
    onNavigateUrl: (String, String) -> Unit,
    onStartInstance: (VirtualInstance, Boolean) -> Unit,
    onStopInstance: (String) -> Unit,
    onSaveBookmark: (title: String, url: String, category: BookmarkCategory) -> Unit,
    onGoToCluster: () -> Unit,
    onNotify: (String) -> Unit
) {
    val activeInstance = instances.firstOrNull { it.id == selectedInstanceId } ?: instances.firstOrNull()

    if (activeInstance == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = CyanPulse,
                    modifier = Modifier.size(56.dp)
                )
                Text("No Virtual Screen Active", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Create or select a virtual instance from the Cluster tab to stream its interactive sandbox viewport.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onGoToCluster) {
                    Text("Go to Cluster")
                }
            }
        }
        return
    }

    val runtimeInfo = activeRuntimes[activeInstance.id]
    val webInfo = viewportInfoMap[activeInstance.id] ?: ViewportRuntimeInfo(
        currentUrl = activeInstance.targetUrl
    )

    var urlBarText by remember(activeInstance.id) {
        mutableStateOf(webInfo.currentUrl.ifBlank { activeInstance.targetUrl })
    }
    LaunchedEffect(webInfo.currentUrl) {
        if (webInfo.currentUrl.isNotBlank()) {
            urlBarText = webInfo.currentUrl
        }
    }

    var lastTapPulse by remember(activeInstance.id) { mutableStateOf<TapPulseEvent?>(null) }
    val pulseRadius = remember { Animatable(0f) }

    LaunchedEffect(activeInstance.id) {
        tapPulses.collectLatest { pulse ->
            if (pulse.instanceId == activeInstance.id) {
                lastTapPulse = pulse
                pulseRadius.snapTo(0f)
                pulseRadius.animateTo(1f, animationSpec = tween(420))
                lastTapPulse = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Multi-Instance Quick Switcher Strip
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(instances, key = { it.id }) { inst ->
                val isSelected = inst.id == activeInstance.id
                val isRunning = activeRuntimes.containsKey(inst.id)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectInstance(inst.id) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) EmeraldAfk else MaterialTheme.colorScheme.outline)
                        )
                    },
                    label = {
                        Text(
                            text = inst.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        // Virtual Browser & Address Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { webViewManager.goBack(activeInstance.id) },
                enabled = webInfo.canGoBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }

            OutlinedTextField(
                value = urlBarText,
                onValueChange = { urlBarText = it },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = if (webInfo.isSecureHttps) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = if (webInfo.isSecureHttps) "HTTPS Encrypted" else "Unencrypted HTTP",
                        tint = if (webInfo.isSecureHttps) EmeraldAfk else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { webViewManager.reload(activeInstance.id) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reload Page", modifier = Modifier.size(18.dp))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = { onNavigateUrl(activeInstance.id, urlBarText) }
                ),
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .weight(1f)
                    .testTag("viewport_url_input")
            )

            IconButton(
                onClick = {
                    val title = webInfo.pageTitle.ifBlank { activeInstance.name }.take(60)
                    onSaveBookmark(title, urlBarText, BookmarkCategory.GENERAL)
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Save Bookmark", tint = CyanPulse)
            }
        }

        if (webInfo.isLoading) {
            LinearProgressIndicator(
                progress = { (webInfo.progress.coerceIn(0, 100) / 100f) },
                color = CyanPulse,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        // Virtual Phone Bezel + Live WebView Sandbox
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianBg)
                    .border(
                        width = 2.dp,
                        color = if (runtimeInfo != null) EmeraldAfk.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                key(activeInstance.id) {
                    AndroidView(
                        factory = {
                            val wv = webViewManager.getOrCreateWebView(activeInstance)
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            wv
                        },
                        update = {
                            webViewManager.updateInstanceConfig(activeInstance)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Macro Tap Pulse Visualizer Overlay
                val pulse = lastTapPulse
                if (pulse != null) {
                    val progress = pulseRadius.value
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width * pulse.xPct
                        val cy = size.height * pulse.yPct
                        val maxR = 46.dp.toPx()
                        drawCircle(
                            color = CyanPulse.copy(alpha = (1f - progress) * 0.85f),
                            radius = maxR * progress,
                            center = Offset(cx, cy),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = EmeraldAfk.copy(alpha = (1f - progress)),
                            radius = 8.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                    }
                }

                // Live Telemetry HUD chip
                Surface(
                    color = ObsidianBg.copy(alpha = 0.82f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (runtimeInfo != null) EmeraldAfk else CyanPulse)
                        )
                        Text(
                            text = if (runtimeInfo != null) {
                                "AFK ${formatDurationHms(runtimeInfo.sessionSeconds)} • ${runtimeInfo.cyclesCompleted} cyc"
                            } else {
                                activeInstance.resolutionEnum().name
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Virtual Phone Control Dock
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = { webViewManager.dispatchVirtualTap(activeInstance.id, 0.5f, 0.5f) },
                    modifier = Modifier.testTag("viewport_quick_tap_button")
                ) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = "Test Virtual Tap")
                }

                FilledTonalIconButton(
                    onClick = {
                        webViewManager.clearSandboxStorage(activeInstance.id)
                        onNotify("Cleared cookies & storage for ${activeInstance.name}")
                    }
                ) {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = "Clear Sandbox Data")
                }

                val isRunning = runtimeInfo != null
                if (isRunning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable {
                                onStartInstance(activeInstance, true)
                                onGoToCluster()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AllInclusive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Keep AFK in Background",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    FilledTonalIconButton(onClick = { onStopInstance(activeInstance.id) }) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Stop Instance")
                    }
                } else {
                    val linkedMacro = macros.firstOrNull { it.id == activeInstance.assignedMacroId }
                    Button(
                        onClick = { onStartInstance(activeInstance, true) },
                        modifier = Modifier.testTag("viewport_start_afk_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(linkedMacro?.let { "Run '${it.name}'" } ?: "Start AFK Loop")
                    }
                }
            }
        }
    }
}
