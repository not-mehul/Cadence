package app.cadence.feature.healthconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cadence.core.ui.components.GroupCard
import app.cadence.core.ui.components.SectionHeader
import app.cadence.core.ui.theme.CadenceTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HealthConnectMessage {
    data object NotPermitted : HealthConnectMessage

    data class Imported(
        val days: Int,
    ) : HealthConnectMessage

    data object Failed : HealthConnectMessage
}

data class HealthConnectUiState(
    val availability: HealthConnectAvailability,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: HealthConnectMessage? = null,
)

@HiltViewModel
class HealthConnectViewModel
    @Inject
    constructor(
        private val sync: HealthConnectSync,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(HealthConnectUiState(sync.availability()))
        val state: StateFlow<HealthConnectUiState> = mutableState.asStateFlow()

        /** Permissions can change in Health Connect's own settings at any time. */
        fun refresh() {
            if (state.value.availability != HealthConnectAvailability.AVAILABLE) return
            viewModelScope.launch {
                val connected = runCatching { sync.isConnected() }.getOrDefault(false)
                mutableState.update { it.copy(connected = connected) }
            }
        }

        fun onPermissionsResult(granted: Set<String>) {
            if (!granted.containsAll(HealthConnectSync.PERMISSIONS)) {
                mutableState.update { it.copy(message = HealthConnectMessage.NotPermitted) }
                return
            }
            mutableState.update { it.copy(connected = true) }
            work { sync.mirror() }
        }

        fun disconnect() =
            work {
                sync.disconnect()
                mutableState.update { it.copy(connected = false) }
            }

        fun importFromOtherApps() =
            work {
                val days = sync.importFromOtherApps()
                mutableState.update { it.copy(message = HealthConnectMessage.Imported(days)) }
            }

        fun dismissMessage() = mutableState.update { it.copy(message = null) }

        private fun work(block: suspend () -> Unit) {
            mutableState.update { it.copy(busy = true) }
            viewModelScope.launch {
                try {
                    block()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Health Connect can refuse (rate limits, a revoked permission, an update in progress).
                    mutableState.update { it.copy(message = HealthConnectMessage.Failed) }
                } finally {
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }
    }

/** Rows for Settings; nothing at all on phones without Health Connect. */
@Composable
fun HealthConnectSettingsRows(viewModel: HealthConnectViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            viewModel.onPermissionsResult(it)
        }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    HealthConnectRows(
        state = state,
        onConnectChange = { on -> if (on) launcher.launch(HealthConnectSync.PERMISSIONS) else viewModel.disconnect() },
        onImport = viewModel::importFromOtherApps,
        onDismissMessage = viewModel::dismissMessage,
    )
}

@Composable
internal fun HealthConnectRows(
    state: HealthConnectUiState,
    onConnectChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    if (state.availability == HealthConnectAvailability.UNAVAILABLE) return
    val gutter = CadenceTheme.spacing.screenGutter
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column(Modifier.padding(top = CadenceTheme.spacing.sm)) {
        SectionHeader(
            stringResource(R.string.hc_title),
            Modifier.padding(horizontal = gutter + CadenceTheme.spacing.xs),
        )
        GroupCard(Modifier.padding(horizontal = gutter)) {
            if (state.availability == HealthConnectAvailability.NEEDS_UPDATE) {
                Text(
                    stringResource(R.string.hc_needs_update),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = gutter, vertical = CadenceTheme.spacing.sm),
                )
                return@GroupCard
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.hc_share)) },
                supportingContent = { Text(stringResource(R.string.hc_share_detail)) },
                trailingContent = { Switch(checked = state.connected, onCheckedChange = null) },
                colors = itemColors,
                modifier =
                    Modifier.toggleable(
                        value = state.connected,
                        enabled = !state.busy,
                        role = Role.Switch,
                        onValueChange = onConnectChange,
                    ),
            )
            if (state.connected) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.hc_import)) },
                    supportingContent = { Text(stringResource(R.string.hc_import_detail)) },
                    colors = itemColors,
                    modifier =
                        Modifier
                            .toggleable(value = false, enabled = !state.busy, role = Role.Button, onValueChange = {
                                onImport()
                            }),
                )
            }
        }
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissMessage,
            text = {
                Text(
                    when (message) {
                        HealthConnectMessage.NotPermitted -> {
                            stringResource(R.string.hc_not_permitted)
                        }

                        HealthConnectMessage.Failed -> {
                            stringResource(R.string.hc_failed)
                        }

                        is HealthConnectMessage.Imported -> {
                            if (message.days == 0) {
                                stringResource(R.string.hc_imported_none)
                            } else {
                                pluralStringResource(R.plurals.hc_imported, message.days, message.days)
                            }
                        }
                    },
                )
            },
            confirmButton = { TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.hc_ok)) } },
        )
    }
}

/**
 * Health Connect opens this to show why Cadence asks for access (required for its
 * permissions, and on Android 14+ from the system's permission usage screen).
 */
class HealthPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CadenceTheme {
                Surface(Modifier.fillMaxSize()) {
                    HealthPermissionsRationale(onClose = ::finish)
                }
            }
        }
    }
}

@Composable
internal fun HealthPermissionsRationale(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(CadenceTheme.spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
    ) {
        Text(
            stringResource(R.string.hc_rationale_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        listOf(R.string.hc_rationale_what, R.string.hc_rationale_others, R.string.hc_rationale_off).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.hc_ok)) }
    }
}
