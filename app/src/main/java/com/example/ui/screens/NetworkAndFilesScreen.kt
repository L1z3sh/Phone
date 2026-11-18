package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BookmarkCategory
import com.example.model.DownloadRecord
import com.example.model.DownloadStatus
import com.example.model.NetworkTelemetry
import com.example.model.SecureBookmark
import com.example.ui.components.TelemetryStatTile
import com.example.ui.components.formatBytes
import com.example.ui.theme.AmberBatch
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldAfk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.UiState

@Composable
fun NetworkAndFilesScreen(
    telemetry: NetworkTelemetry,
    bookmarksState: UiState<List<SecureBookmark>>,
    downloadsState: UiState<List<DownloadRecord>>,
    downloadProgress: Map<String, Int>,
    onRefreshTelemetry: () -> Unit,
    onOpenUrlInSandbox: (String) -> Unit,
    onAddBookmark: (title: String, url: String, category: BookmarkCategory) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onStartDownload: (url: String, fileName: String) -> Unit,
    onOpenDownload: (DownloadRecord) -> Unit,
    onDeleteDownload: (String) -> Unit
) {
    var downloadUrl by remember { mutableStateOf("") }
    var downloadFileName by remember { mutableStateOf("") }
    var showAddBookmark by remember { mutableStateOf(false) }

    val bookmarks = (bookmarksState as? UiState.Success)?.data ?: emptyList()
    val downloads = (downloadsState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Real Wi-Fi & Shielded Network Inspector Card
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
                            painter = painterResource(id = R.drawable.img_shield_wifi_banner_1795001907951),
                            contentDescription = "Encrypted Wi-Fi Shield Artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, ObsidianSurface.copy(alpha = 0.94f))
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wi-Fi & Sandbox Network", style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    text = "${telemetry.wifiSsid} • ${telemetry.localIpAddress}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (telemetry.isConnected) EmeraldAfk else RoseAlert
                                )
                            }
                            IconButton(onClick = onRefreshTelemetry) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Network Telemetry")
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatTile(
                                icon = Icons.Rounded.SignalWifi4Bar,
                                label = "SIGNAL",
                                value = if (telemetry.isConnected) "${telemetry.signalBars}/4 (${telemetry.signalDbm} dBm)" else "Offline",
                                accent = EmeraldAfk,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatTile(
                                icon = Icons.Rounded.Speed,
                                label = "THROUGHPUT",
                                value = "↓${telemetry.linkDownMbps} / ↑${telemetry.linkUpMbps} Mbps",
                                accent = CyanPulse,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatTile(
                                icon = Icons.Rounded.Lock,
                                label = "DNS PRIVACY",
                                value = if (telemetry.privateDnsActive) telemetry.privateDnsServer else "Standard DNS",
                                accent = if (telemetry.privateDnsActive) EmeraldAfk else AmberBatch,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatTile(
                                icon = Icons.Rounded.Shield,
                                label = "SHIELD BLOCKS",
                                value = "${telemetry.blockedTrackersCount} trackers • ${telemetry.httpsUpgradesCount} TLS",
                                accent = CyanPulse,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Secure File Downloader Card
        item {
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
                    Text("Secure File Downloader", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Fetch APKs, assets, or archives directly over TLS into your isolated sandbox storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = downloadUrl,
                        onValueChange = { downloadUrl = it },
                        label = { Text("Direct File URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_url_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = downloadFileName,
                            onValueChange = { downloadFileName = it },
                            label = { Text("File Name (Optional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (downloadUrl.isNotBlank()) {
                                    onStartDownload(downloadUrl, downloadFileName)
                                    downloadUrl = ""
                                    downloadFileName = ""
                                }
                            },
                            enabled = downloadUrl.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulse,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("start_download_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetch")
                        }
                    }
                }
            }
        }

        // Download Queue
        if (downloads.isNotEmpty()) {
            item {
                Text(
                    text = "Downloaded Files (${downloads.size})",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            items(downloads, key = { it.id }) { dl ->
                val pct = downloadProgress[dl.id]
                DownloadRecordCard(
                    record = dl,
                    progressPct = pct,
                    onOpen = { onOpenDownload(dl) },
                    onDelete = { onDeleteDownload(dl.id) }
                )
            }
        }

        // Secure Browser Bookmarks Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Secure Browser Bookmarks", style = MaterialTheme.typography.titleLarge)
                TextButton(
                    onClick = { showAddBookmark = true },
                    modifier = Modifier.testTag("add_bookmark_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Bookmark")
                }
            }
        }

        if (bookmarks.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No bookmarks saved yet. Tap '+ Add Bookmark' or save any URL directly from the Viewport browser bar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(bookmarks, key = { it.id }) { bm ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bm.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${bm.categoryEnum().label} • ${bm.url}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onOpenUrlInSandbox(bm.url) }) {
                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = "Open in Sandbox", tint = EmeraldAfk)
                        }
                        IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Bookmark", tint = RoseAlert)
                        }
                    }
                }
            }
        }
    }

    if (showAddBookmark) {
        AddBookmarkDialog(
            onDismiss = { showAddBookmark = false },
            onSave = { title, url, cat ->
                onAddBookmark(title, url, cat)
                showAddBookmark = false
            }
        )
    }
}

@Composable
private fun DownloadRecordCard(
    record: DownloadRecord,
    progressPct: Int?,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val status = record.statusEnum()
    val statusColor = when (status) {
        DownloadStatus.COMPLETED -> EmeraldAfk
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> CyanPulse
        DownloadStatus.FAILED -> RoseAlert
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${status.label} • ${formatBytes(record.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
                if (status == DownloadStatus.COMPLETED && record.localUri.isNotBlank()) {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Open Downloaded File", tint = CyanPulse)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove Download Record", tint = RoseAlert)
                }
            }
            if (progressPct != null) {
                LinearProgressIndicator(
                    progress = { (progressPct / 100f).coerceIn(0f, 1f) },
                    color = CyanPulse,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddBookmarkDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, url: String, category: BookmarkCategory) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var category by remember { mutableStateOf(BookmarkCategory.GAMING) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Secure Bookmark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 60) title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { if (it.length <= 500) url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BookmarkCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), url.trim(), category) },
                enabled = title.isNotBlank() && url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
