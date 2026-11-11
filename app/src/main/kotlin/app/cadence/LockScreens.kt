package app.cadence

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.cadence.core.ui.components.PrimaryButton
import app.cadence.core.ui.expressive.Buddy
import app.cadence.core.ui.expressive.BuddyFace
import app.cadence.core.ui.expressive.SleepyZs
import app.cadence.core.ui.security.DeviceAuth
import app.cadence.core.ui.theme.CadenceTheme

/** Shown instead of everything else while the app is locked. Asks for authentication straight away. */
@Composable
internal fun LockScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val title = stringResource(R.string.lock_prompt_title)
    val currentOnUnlocked by rememberUpdatedState(onUnlock)
    val prompt = { activity?.let { DeviceAuth.authenticate(it, title, null, onSuccess = currentOnUnlocked) } }
    LaunchedEffect(Unit) { prompt() }
    CenteredMessage(
        title = stringResource(R.string.lock_title),
        body = stringResource(R.string.lock_body),
        action = stringResource(R.string.lock_unlock),
        onAction = { prompt() },
        modifier = modifier,
    )
}

/** The database couldn't be opened; explain plainly and offer to start over. */
@Composable
internal fun DataUnavailableScreen(
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    CenteredMessage(
        title = stringResource(R.string.recovery_title),
        body = stringResource(R.string.recovery_body),
        action = stringResource(R.string.recovery_start_over),
        onAction = { confirming = true },
        modifier = modifier,
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.recovery_confirm_title)) },
            text = { Text(stringResource(R.string.recovery_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onStartOver()
                }) { Text(stringResource(R.string.recovery_start_over), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirming = false },
                ) { Text(stringResource(R.string.recovery_cancel)) }
            },
        )
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CadenceTheme.spacing
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A dozing buddy, with z's drifting up from it.
            Box {
                Buddy(
                    body = CadenceTheme.cycleColors.lutealTint,
                    face = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialShapes.Puffy,
                    expression = BuddyFace.SLEEPY,
                    size = 148.dp,
                    modifier = Modifier.padding(top = 32.dp, start = 40.dp, end = 40.dp),
                )
                SleepyZs(CadenceTheme.cycleColors.lutealPhase, Modifier.align(Alignment.TopEnd))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            PrimaryButton(action, onClick = onAction, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth())
        }
    }
}
