package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.signOutUser
import com.example.model.InstanceProfileMode
import com.example.model.ResolutionPreset
import com.example.model.UserAgentMode
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldAfk
import com.example.ui.viewmodel.AetherViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.collectLatest

const val ROUTE_CLUSTER = "cluster"
const val ROUTE_VIEWPORT = "viewport"
const val ROUTE_MACROS = "macros"
const val ROUTE_NETWORK = "network"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    NavDestination(ROUTE_CLUSTER, R.string.nav_cluster, Icons.Rounded.Devices, "nav_cluster"),
    NavDestination(ROUTE_VIEWPORT, R.string.nav_viewport, Icons.Rounded.PhoneAndroid, "nav_viewport"),
    NavDestination(ROUTE_MACROS, R.string.nav_macros, Icons.Rounded.AutoAwesomeMotion, "nav_macros"),
    NavDestination(ROUTE_NETWORK, R.string.nav_network, Icons.Rounded.Wifi, "nav_network")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceShell(
    user: FirebaseUser,
    viewModel: AetherViewModel,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val instancesState by viewModel.instancesState.collectAsStateWithLifecycle()
    val macrosState by viewModel.macrosState.collectAsStateWithLifecycle()
    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val downloadsState by viewModel.downloadsState.collectAsStateWithLifecycle()
    val activeRuntimes by viewModel.activeRuntimes.collectAsStateWithLifecycle()
    val macroLogs by viewModel.macroLogs.collectAsStateWithLifecycle()
    val telemetry by viewModel.networkTelemetry.collectAsStateWithLifecycle()
    val viewportInfoMap by viewModel.viewportInfo.collectAsStateWithLifecycle()
    val selectedInstanceId by viewModel.selectedInstanceId.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    val instances = (instancesState as? UiState.Success)?.data ?: emptyList()
    val macros = (macrosState as? UiState.Success)?.data ?: emptyList()

    var showAccountSheet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_CLUSTER

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (activeRuntimes.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(EmeraldAfk.copy(alpha = 0.16f))
                                        .border(1.dp, EmeraldAfk.copy(alpha = 0.5f), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.AllInclusive,
                                        contentDescription = null,
                                        tint = EmeraldAfk,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${activeRuntimes.size} AFK",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldAfk
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAccountSheet = true },
                            modifier = Modifier.testTag("account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = "Account & AFK Power Settings",
                                tint = CyanPulse
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 960.dp)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_CLUSTER
                        ) {
                            composable(ROUTE_CLUSTER) {
                                ClusterDashboardScreen(
                                    instancesState = instancesState,
                                    macros = macros,
                                    activeRuntimes = activeRuntimes,
                                    networkTelemetry = telemetry,
                                    onOpenViewport = { inst ->
                                        viewModel.selectViewportInstance(inst.id)
                                        navigateTo(ROUTE_VIEWPORT)
                                    },
                                    onCreateInstance = viewModel::createInstance,
                                    onUpdateInstance = viewModel::updateInstanceConfig,
                                    onStartInstance = { inst, bg ->
                                        viewModel.startInstance(inst, macros, bg)
                                    },
                                    onStopInstance = viewModel::stopInstance,
                                    onCloneInstance = viewModel::cloneInstance,
                                    onDeleteInstance = viewModel::deleteInstance,
                                    onBatchStartAll = { list ->
                                        viewModel.batchStartAll(list, macros)
                                    },
                                    onStopAll = viewModel::stopAllInstances
                                )
                            }

                            composable(ROUTE_VIEWPORT) {
                                ViewportScreen(
                                    instances = instances,
                                    macros = macros,
                                    selectedInstanceId = selectedInstanceId,
                                    activeRuntimes = activeRuntimes,
                                    viewportInfoMap = viewportInfoMap,
                                    tapPulses = viewModel.tapPulses,
                                    webViewManager = viewModel.runtime.webViewManager,
                                    onSelectInstance = viewModel::selectViewportInstance,
                                    onNavigateUrl = viewModel::navigateInstance,
                                    onStartInstance = { inst, bg ->
                                        viewModel.startInstance(inst, macros, bg)
                                    },
                                    onStopInstance = viewModel::stopInstance,
                                    onSaveBookmark = viewModel::addBookmark,
                                    onGoToCluster = { navigateTo(ROUTE_CLUSTER) },
                                    onNotify = { /* feedback via snackbar optional */ }
                                )
                            }

                            composable(ROUTE_MACROS) {
                                MacrosScreen(
                                    macrosState = macrosState,
                                    instances = instances,
                                    logs = macroLogs,
                                    onCreateMacro = viewModel::createMacro,
                                    onUpdateMacro = viewModel::updateMacro,
                                    onDeleteMacro = viewModel::deleteMacro,
                                    onBatchRunMacro = { targets, macro ->
                                        viewModel.batchRunMacro(targets, macro, backgroundAfk = true)
                                    }
                                )
                            }

                            composable(ROUTE_NETWORK) {
                                NetworkAndFilesScreen(
                                    telemetry = telemetry,
                                    bookmarksState = bookmarksState,
                                    downloadsState = downloadsState,
                                    downloadProgress = downloadProgress,
                                    onRefreshTelemetry = viewModel::refreshNetworkTelemetry,
                                    onOpenUrlInSandbox = { url ->
                                        val existing = instances.firstOrNull()
                                        if (existing != null) {
                                            viewModel.selectViewportInstance(existing.id)
                                            viewModel.navigateInstance(existing.id, url)
                                        } else {
                                            viewModel.createInstance(
                                                name = "Secure Web Sandbox",
                                                profileMode = InstanceProfileMode.SECURE_BROWSE,
                                                resolutionPreset = ResolutionPreset.FHD_1080,
                                                userAgentMode = UserAgentMode.ANDROID_MOBILE,
                                                targetUrl = url,
                                                assignedMacroId = "",
                                                keepAwake = false,
                                                adShieldEnabled = true,
                                                httpsOnly = true
                                            )
                                        }
                                        navigateTo(ROUTE_VIEWPORT)
                                    },
                                    onAddBookmark = viewModel::addBookmark,
                                    onDeleteBookmark = viewModel::deleteBookmark,
                                    onStartDownload = viewModel::startDirectDownload,
                                    onOpenDownload = viewModel::openDownload,
                                    onDeleteDownload = viewModel::deleteDownloadRecord
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAccountSheet) {
        AlertDialog(
            onDismissRequest = { showAccountSheet = false },
            title = { Text("Workspace & AFK Runtime") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = user.displayName ?: "Cloud Operator",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = user.email ?: user.uid,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Tip: To prevent Android from pausing background AFK loops during long overnight sessions, set AetherPhone battery usage to Unrestricted in system settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Battery Optimization Settings")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccountSheet = false
                        viewModel.stopAllInstances()
                        signOutUser(credentialManager, scope, onSignOutComplete = onSignedOut)
                    },
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountSheet = false }) {
                    Text("Close")
                }
            }
        )
    }
}
