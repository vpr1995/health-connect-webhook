package com.hcwebhook.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.hcwebhook.app.HealthConnectManager
import com.hcwebhook.app.PreferencesManager
import com.hcwebhook.app.AuthSessionManager
import com.hcwebhook.app.SyncManager
import com.hcwebhook.app.SyncResult
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSyncCard(onSyncCompleted: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { PreferencesManager(context) }
    
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmSheet by remember { mutableStateOf(false) }
    var fromDateMillis by remember { mutableStateOf<Long?>(null) }
    var toDateMillis by remember { mutableStateOf<Long?>(null) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    val authState by AuthSessionManager.authUiState.collectAsState()
    
    val webhookConfigs = preferencesManager.getWebhookConfigs()
    val isDateRangeValid = fromDateMillis == null || toDateMillis == null || fromDateMillis!! <= toDateMillis!!
    val syncDateRange = remember(fromDateMillis, toDateMillis) {
        buildSyncDateRange(fromDateMillis, toDateMillis)
    }

    if (showFromDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = fromDateMillis)
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromDateMillis = datePickerState.selectedDateMillis
                    showFromDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showToDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = toDateMillis)
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toDateMillis = datePickerState.selectedDateMillis
                    showToDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Confirmation Bottom Sheet ──────────────────────────────────────────────
    if (showConfirmSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConfirmSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Sync Now?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "This will immediately send your health data to all configured webhooks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Range: ${formatDateLabel(fromDateMillis, "Default")} → ${formatDateLabel(toDateMillis, "Now")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        showConfirmSheet = false
                        if (isSyncing) return@Button

                        scope.launch {
                            isSyncing = true
                            syncMessage = null

                            try {
                                val availability = HealthConnectClient.getSdkStatus(context)
                                if (availability != HealthConnectClient.SDK_AVAILABLE) {
                                    syncMessage = when (availability) {
                                        HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect is not installed"
                                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect needs update"
                                        else -> "Health Connect is not available"
                                    }
                                    isSyncing = false
                                    return@launch
                                }

                                val healthConnectManager = HealthConnectManager(context)
                                if (!healthConnectManager.hasPermissions()) {
                                    syncMessage = "Permissions required for sync."
                                    isSyncing = false
                                    return@launch
                                }

                                val syncManager = SyncManager(context)
                                val result = syncManager.performSync(
                                    fromTime = syncDateRange.first,
                                    toTime = syncDateRange.second
                                )

                                when {
                                    result.isSuccess -> {
                                        val syncResult = result.getOrThrow()
                                        syncMessage = when (syncResult) {
                                            is SyncResult.NoData -> "No new data to sync"
                                            is SyncResult.Success -> {
                                                val parts = syncResult.syncCounts.map { (type, count) ->
                                                    "$count ${type.displayName.lowercase()}"
                                                }
                                                if (parts.isEmpty()) "Sync completed successfully"
                                                else "Synced ${parts.joinToString(", ")}"
                                            }
                                        }
                                        onSyncCompleted()
                                    }
                                    result.isFailure -> {
                                        syncMessage = "Sync failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                                    }
                                }
                            } catch (e: Exception) {
                                syncMessage = "Sync failed: ${e.message}"
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    enabled = isDateRangeValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sync Now")
                }
                OutlinedButton(
                    onClick = { showConfirmSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    // ── Card UI ───────────────────────────────────────────────────────────────
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Manual Sync", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Trigger a manual sync to send current health data to webhooks",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Optional date range",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showFromDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("From: ${formatDateLabel(fromDateMillis, "Default")}")
                }
                OutlinedButton(
                    onClick = { showToDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("To: ${formatDateLabel(toDateMillis, "Now")}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val todayMillis = LocalDate.now().toEpochDay() * MILLIS_PER_DAY
                        toDateMillis = todayMillis
                        fromDateMillis = todayMillis - (6 * MILLIS_PER_DAY)
                    }
                ) {
                    Text("Last 7 days")
                }
                TextButton(
                    onClick = {
                        fromDateMillis = null
                        toDateMillis = null
                    }
                ) {
                    Text("Clear")
                }
            }
            if (!isDateRangeValid) {
                Text(
                    "From date must be before To date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!authState.isSignedIn) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Login is required before syncing data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showConfirmSheet = true },
                enabled = !isSyncing && webhookConfigs.isNotEmpty() && authState.isSignedIn && isDateRangeValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Text("Sync Now")
                }
            }

            syncMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.contains("failed", ignoreCase = true))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

internal const val MILLIS_PER_DAY = 86_400_000L
internal val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

internal fun formatDateLabel(dateMillis: Long?, fallback: String): String {
    if (dateMillis == null) return fallback
    val localDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return DATE_FORMATTER.format(localDate)
}

internal fun buildSyncDateRange(fromDateMillis: Long?, toDateMillis: Long?): Pair<Instant?, Instant?> {
    val zone = ZoneId.systemDefault()
    val from = fromDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant()
    }
    val to = toDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    }
    return from to to
}
