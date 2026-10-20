package com.healthconnect.export.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.healthconnect.export.R
import com.healthconnect.export.data.ExportFormat
import com.healthconnect.export.data.HealthDataType
import com.healthconnect.export.ui.components.DataSourceCard
import com.healthconnect.export.ui.components.DataTypeCard
import com.healthconnect.export.ui.components.DateRangeCard
import com.healthconnect.export.ui.components.DriveStatusCard
import com.healthconnect.export.ui.components.ExportSummaryCard
import com.healthconnect.export.ui.components.ExportedFilesCard
import com.healthconnect.export.ui.components.JsonViewerDialog
import com.healthconnect.export.ui.components.MaterialCard
import com.healthconnect.export.ui.components.MaterialSectionHeader
import com.healthconnect.export.ui.components.ReleaseNotesDialog
import com.healthconnect.export.ui.components.ScheduleCard
import com.healthconnect.export.ui.components.WebhookCard
import com.healthconnect.export.viewmodel.ExportViewModel
import com.healthconnect.export.viewmodel.UpdateCheckState
import java.io.File

private enum class ExportDestination(
    val route: String,
    val titleRes: Int,
) {
    EXPORT("export", R.string.nav_export),
    HISTORY("history", R.string.nav_history),
    INTEGRATIONS("integrations", R.string.nav_integrations),
    SCHEDULE("schedule", R.string.nav_schedule),
    SETTINGS("settings", R.string.nav_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    onSignInClick: () -> Unit,
    onRequestHealthPermissions: (Set<String>) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedRoute by rememberSaveable { mutableStateOf(ExportDestination.EXPORT.route) }
    var selectedJsonFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var showReleaseNotes by rememberSaveable { mutableStateOf(false) }
    // Hoisted so the expanded History list stays expanded when switching tabs
    var showAllHistoryFiles by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(viewModel.pendingPermissions) {
        viewModel.pendingPermissions?.let(onRequestHealthPermissions)
    }

    // Close the release-notes dialog if the update state changes (e.g. after a re-check)
    LaunchedEffect(uiState.updateCheckState) {
        if (uiState.updateCheckState !is UpdateCheckState.Available) {
            showReleaseNotes = false
        }
    }

    selectedJsonFilePath?.let { path ->
        JsonViewerDialog(
            file = File(path),
            onDismiss = { selectedJsonFilePath = null },
        )
    }

    if (showReleaseNotes) {
        val available = uiState.updateCheckState as? UpdateCheckState.Available
        if (available != null) {
            ReleaseNotesDialog(
                version = available.latestVersion,
                releaseNotes =
                    available.releaseNotes
                        ?: stringResource(R.string.release_notes_unavailable),
                onDownload = {
                    uriHandler.openUri(available.downloadUrl)
                    viewModel.resetUpdateCheck()
                    showReleaseNotes = false
                },
                onDismiss = { showReleaseNotes = false },
            )
        }
    }

    val destination =
        ExportDestination.entries.firstOrNull { it.route == selectedRoute }
            ?: ExportDestination.EXPORT

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(destination.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                actions = {
                    UpdateCheckAction(
                        state = uiState.updateCheckState,
                        onCheck = viewModel::checkForUpdates,
                        onShowReleaseNotes = { showReleaseNotes = true },
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                ExportDestination.entries.forEach { item ->
                    val selected = item.route == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedRoute = item.route },
                        icon = {
                            Icon(
                                imageVector = destinationIcon(item),
                                contentDescription = stringResource(item.titleRes),
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(item.titleRes),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
            }
        },
    ) { paddingValues ->
        when (destination) {
            ExportDestination.EXPORT ->
                ExportHomeContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.padding(paddingValues),
                )
            ExportDestination.HISTORY ->
                HistoryContent(
                    files = uiState.exportedFiles,
                    summary = uiState.exportSummary,
                    showAll = showAllHistoryFiles,
                    onShowAllChange = { showAllHistoryFiles = it },
                    onFileClick = { selectedJsonFilePath = it.absolutePath },
                    onShareFile = viewModel::shareExportFile,
                    onDeleteFile = viewModel::deleteExportFile,
                    modifier = Modifier.padding(paddingValues),
                )
            ExportDestination.INTEGRATIONS ->
                IntegrationsContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onSignInClick = onSignInClick,
                    modifier = Modifier.padding(paddingValues),
                )
            ExportDestination.SCHEDULE ->
                ScheduleContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.padding(paddingValues),
                )
            ExportDestination.SETTINGS ->
                SettingsContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowReleaseNotes = { showReleaseNotes = true },
                    modifier = Modifier.padding(paddingValues),
                )
        }
    }
}

private fun destinationIcon(destination: ExportDestination) =
    when (destination) {
        ExportDestination.EXPORT -> Icons.Default.FileUpload
        ExportDestination.HISTORY -> Icons.Default.History
        ExportDestination.INTEGRATIONS -> Icons.Default.Cloud
        ExportDestination.SCHEDULE -> Icons.Default.Schedule
        ExportDestination.SETTINGS -> Icons.Default.Settings
    }

/**
 * App bar action for the GitHub update check. Shows a small spinner while
 * checking and a notification dot when a new version is available.
 */
@Composable
private fun UpdateCheckAction(
    state: UpdateCheckState,
    onCheck: () -> Unit,
    onShowReleaseNotes: () -> Unit,
) {
    when (state) {
        is UpdateCheckState.Checking -> {
            val checkingDescription = stringResource(R.string.checking_for_updates)
            IconButton(onClick = onCheck, enabled = false) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .semantics { contentDescription = checkingDescription },
                    strokeWidth = 2.dp,
                )
            }
        }
        else -> {
            val hasUpdate = state is UpdateCheckState.Available
            BadgedBox(
                badge = {
                    if (hasUpdate) {
                        Badge()
                    }
                },
            ) {
                IconButton(
                    onClick = {
                        if (hasUpdate) {
                            onShowReleaseNotes()
                        } else {
                            onCheck()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription =
                            stringResource(
                                if (hasUpdate) {
                                    R.string.update_available_badge
                                } else {
                                    R.string.check_for_updates
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportHomeContent(
    uiState: com.healthconnect.export.viewmodel.ExportUiState,
    viewModel: ExportViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DateRangeCard(
                startDate = uiState.startDate,
                endDate = uiState.endDate,
                onPresetChange = viewModel::setDateRange,
                onDateRangeChange = viewModel::setDateRange,
                onStartDateChange = { viewModel.setDateRange(it, uiState.endDate) },
                onEndDateChange = { viewModel.setDateRange(uiState.startDate, it) },
            )
        }
        item {
            DataTypeCard(
                selectedTypes = uiState.selectedTypes,
                onTypeToggle = { type ->
                    val newTypes =
                        if (type in uiState.selectedTypes) {
                            uiState.selectedTypes - type
                        } else {
                            uiState.selectedTypes + type
                        }
                    viewModel.selectTypes(newTypes)
                },
                onSelectAll = { viewModel.selectTypes(HealthDataType.entries.toSet()) },
                onDeselectAll = { viewModel.selectTypes(emptySet()) },
            )
        }
        item {
            DataSourceCard(
                availableSources = uiState.availableSources,
                selectedSourcePackage = uiState.selectedSourcePackage,
                sourcesLoading = uiState.sourcesLoading,
                onSourceSelected = viewModel::setSourcePackage,
                onRefresh = viewModel::fetchAvailableSources,
            )
        }
        item {
            ExportActionButton(
                isLoading = uiState.isLoading,
                exportProgress = uiState.exportProgress,
                progressPhase = uiState.progressPhase,
                progressDate = uiState.progressDate,
                progressCurrent = uiState.progressCurrent,
                progressTotal = uiState.progressTotal,
                selectedTypesCount = uiState.selectedTypes.size,
                onExport = viewModel::exportNow,
                onCancel = viewModel::cancelExportNow,
            )
        }
        item {
            AnimatedVisibility(
                visible = uiState.exportSummary != null,
                enter = expandVertically(tween(400)) + fadeIn(tween(400)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(300)),
            ) {
                uiState.exportSummary?.let { summary ->
                    ExportSummaryCard(summary = summary, onDismiss = viewModel::dismissSummary)
                }
            }
        }
        item { Spacer(modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun HistoryContent(
    files: List<File>,
    summary: com.healthconnect.export.data.ExportSummary?,
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    onFileClick: (File) -> Unit,
    onShareFile: (File) -> Unit = {},
    onDeleteFile: (File) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (files.isEmpty()) {
            item { EmptyHistoryCard() }
        } else {
            item {
                ExportedFilesCard(
                    files = files,
                    showAll = showAll,
                    onShowAllChange = onShowAllChange,
                    onFileClick = onFileClick,
                    onShareFile = onShareFile,
                    onDeleteFile = onDeleteFile,
                )
            }
        }
        if (summary != null) {
            item { ExportSummaryCard(summary = summary, onDismiss = {}) }
        }
        item { Spacer(modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun EmptyHistoryCard() {
    MaterialCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.history_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun IntegrationsContent(
    uiState: com.healthconnect.export.viewmodel.ExportUiState,
    viewModel: ExportViewModel,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DriveStatusCard(
                status = uiState.driveStatus,
                onSync = viewModel::syncToDrive,
                onSignInClick = onSignInClick,
                onSignOutClick = viewModel::signOut,
            )
        }
        item {
            MaterialCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.auto_sync_drive),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.Switch(
                        checked = uiState.autoSyncDrive,
                        onCheckedChange = viewModel::setAutoSyncDrive,
                    )
                }
            }
        }
        item {
            WebhookCard(
                webhookUrl = uiState.webhookUrl,
                webhookUrlError = uiState.webhookUrlError,
                webhookAuthToken = uiState.webhookAuthToken,
                autoSendWebhook = uiState.autoSendWebhook,
                isTestingWebhook = uiState.isTestingWebhook,
                onUrlChange = viewModel::setWebhookUrl,
                onTokenChange = viewModel::setWebhookAuthToken,
                onToggle = viewModel::setAutoSendWebhook,
                onTestClick = viewModel::testWebhook,
                onCancelTestClick = viewModel::cancelTestWebhook,
            )
        }
        item { Spacer(modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun ScheduleContent(
    uiState: com.healthconnect.export.viewmodel.ExportUiState,
    viewModel: ExportViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScheduleCard(
                frequency = uiState.frequency,
                scheduleStatus = uiState.scheduleStatus,
                onFrequencyChange = viewModel::setFrequency,
                onSchedule = viewModel::scheduleExport,
                onCancel = viewModel::cancelSchedule,
                autoSendWebhookEvery2Hours = uiState.autoSendWebhookEvery2Hours,
                webhookUrl = uiState.webhookUrl,
                onAutoSendEvery2HoursChange = viewModel::setAutoSendWebhookEvery2Hours,
                scheduleHour = uiState.scheduleHour,
                onScheduleHourChange = viewModel::setScheduleHour,
                lastAttemptAt = uiState.lastDeliveryAttemptAt,
                lastAttemptResult = uiState.lastDeliveryAttemptResult,
                lastSuccessAt = uiState.lastDeliverySuccessAt,
                batteryOptimizationIgnored = uiState.batteryOptimizationIgnored,
                onRequestBatteryExemption = viewModel::requestBatteryOptimizationExemption,
                deliveryLogLines =
                    if (uiState.lastDeliveryAttemptAt != null) {
                        viewModel.deliveryLog()
                    } else {
                        emptyList()
                    },
                onRefreshDeliveryStatus = {
                    viewModel.refreshDeliveryStatus()
                    viewModel.refreshBatteryOptimizationStatus()
                },
            )
        }
        item { Spacer(modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun SettingsContent(
    uiState: com.healthconnect.export.viewmodel.ExportUiState,
    viewModel: ExportViewModel,
    onShowReleaseNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MaterialCard {
                MaterialSectionHeader(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.appearance),
                )
                Spacer(modifier = Modifier.size(8.dp))
                SettingDropdown(
                    label = stringResource(R.string.theme_label),
                    selectedLabel =
                        when (uiState.isDarkTheme) {
                            null -> stringResource(R.string.theme_system)
                            false -> stringResource(R.string.theme_light)
                            true -> stringResource(R.string.theme_dark)
                        },
                    options =
                        listOf(
                            stringResource(R.string.theme_system) to { viewModel.setDarkTheme(null) },
                            stringResource(R.string.theme_light) to { viewModel.setDarkTheme(false) },
                            stringResource(R.string.theme_dark) to { viewModel.setDarkTheme(true) },
                        ),
                    icon = Icons.Default.Settings,
                )
            }
        }
        item {
            MaterialCard {
                MaterialSectionHeader(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.locale_switch),
                )
                Spacer(modifier = Modifier.size(8.dp))
                SettingDropdown(
                    label = stringResource(R.string.language_label),
                    selectedLabel =
                        when (uiState.locale) {
                            "en" -> stringResource(R.string.locale_en)
                            "ru" -> stringResource(R.string.locale_ru)
                            "es" -> stringResource(R.string.locale_es)
                            else -> stringResource(R.string.locale_system)
                        },
                    options =
                        listOf(
                            stringResource(R.string.locale_system) to { viewModel.setLocale(null) },
                            stringResource(R.string.locale_en) to { viewModel.setLocale("en") },
                            stringResource(R.string.locale_ru) to { viewModel.setLocale("ru") },
                            stringResource(R.string.locale_es) to { viewModel.setLocale("es") },
                        ),
                    icon = Icons.Default.Language,
                )
            }
        }
        item {
            MaterialCard {
                MaterialSectionHeader(
                    icon = Icons.Default.Save,
                    title = stringResource(R.string.export_format_title),
                )
                Spacer(modifier = Modifier.size(8.dp))
                SettingDropdown(
                    label = stringResource(R.string.export_format_label),
                    selectedLabel =
                        when (uiState.exportFormat) {
                            ExportFormat.JSON -> stringResource(R.string.export_format_json)
                            ExportFormat.CSV -> stringResource(R.string.export_format_csv)
                        },
                    options =
                        listOf(
                            stringResource(R.string.export_format_json) to {
                                viewModel.setExportFormat(ExportFormat.JSON)
                            },
                            stringResource(R.string.export_format_csv) to {
                                viewModel.setExportFormat(ExportFormat.CSV)
                            },
                        ),
                    icon = Icons.Default.Save,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.export_format_csv_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            RetentionCard(
                retentionDays = uiState.retentionDays,
                onRetentionDaysChange = viewModel::setRetentionDays,
            )
        }
        item {
            MaterialCard {
                MaterialSectionHeader(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.health_connect_access),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.health_connect_access_limit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            AboutCard(
                viewModel = viewModel,
                onShowReleaseNotes = onShowReleaseNotes,
            )
        }
        item { Spacer(modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun RetentionCard(
    retentionDays: Int?,
    onRetentionDaysChange: (Int?) -> Unit,
) {
    MaterialCard {
        MaterialSectionHeader(
            icon = Icons.Default.Delete,
            title = stringResource(R.string.retention_title),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.retention_enable),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Switch(
                checked = retentionDays != null,
                onCheckedChange = { enabled ->
                    onRetentionDaysChange(if (enabled) 30 else null)
                },
            )
        }
        if (retentionDays != null) {
            Spacer(modifier = Modifier.size(8.dp))
            SettingDropdown(
                label = stringResource(R.string.retention_days_label),
                selectedLabel = stringResource(R.string.retention_days_format, retentionDays),
                options =
                    listOf(
                        stringResource(R.string.retention_days_format, 7) to { onRetentionDaysChange(7) },
                        stringResource(R.string.retention_days_format, 14) to { onRetentionDaysChange(14) },
                        stringResource(R.string.retention_days_format, 30) to { onRetentionDaysChange(30) },
                        stringResource(R.string.retention_days_format, 90) to { onRetentionDaysChange(90) },
                    ),
                icon = Icons.Default.Delete,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.retention_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, () -> Unit>>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(
            onClick = { expanded = true },
            modifier =
                Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true,
                    ).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (optionLabel, onSelect) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelect()
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutCard(
    viewModel: ExportViewModel,
    onShowReleaseNotes: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val repositoryUrl = stringResource(R.string.github_url)
    val uiState by viewModel.uiState.collectAsState()
    val updateState = uiState.updateCheckState

    MaterialCard {
        MaterialSectionHeader(
            icon = Icons.Default.Description,
            title = stringResource(R.string.about),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.version_format, com.healthconnect.export.BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.app_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(12.dp))
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )

        // ── Update check (GitHub releases) ──
        when (val state = updateState) {
            is UpdateCheckState.Idle -> {
                TextButton(
                    onClick = viewModel::checkForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.check_for_updates),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                    )
                }
            }
            is UpdateCheckState.Checking -> {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.checking_for_updates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is UpdateCheckState.UpToDate -> {
                TextButton(
                    onClick = viewModel::checkForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.up_to_date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
            is UpdateCheckState.Available -> {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.update_available, state.latestVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            uriHandler.openUri(state.downloadUrl)
                            viewModel.resetUpdateCheck()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.update_download))
                    }
                }
                TextButton(
                    onClick = onShowReleaseNotes,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.whats_new),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
            is UpdateCheckState.Error -> {
                TextButton(
                    onClick = viewModel::checkForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.retry),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )

        // ── GitHub repository link ──
        TextButton(
            onClick = { uriHandler.openUri(repositoryUrl) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.github_repository),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExportActionButton(
    isLoading: Boolean,
    exportProgress: String,
    progressPhase: String,
    progressDate: String,
    progressCurrent: Int,
    progressTotal: Int,
    selectedTypesCount: Int,
    onExport: () -> Unit,
    onCancel: () -> Unit,
) {
    Button(
        onClick = if (isLoading) onCancel else onExport,
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(tween(300)),
        enabled = isLoading || selectedTypesCount > 0,
    ) {
        if (!isLoading) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.export_data_types, selectedTypesCount))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(exportProgress)
                }
                if (progressPhase == "read" && progressTotal > 0) {
                    Spacer(modifier = Modifier.size(4.dp))
                    LinearProgressIndicator(
                        progress = { progressCurrent.toFloat() / progressTotal.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "$progressDate  $progressCurrent/$progressTotal",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = stringResource(R.string.cancel_export),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                )
            }
        }
    }
}
