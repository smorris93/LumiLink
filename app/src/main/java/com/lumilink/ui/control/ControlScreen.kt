package com.lumilink.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumilink.model.CameraStatus
import com.lumilink.ui.appContainer

/**
 * Control screen (MVP2): remote shutter, autofocus and video record, with a live camera-status
 * strip. Live view and exposure numbers are placeholders until MVP3 wires up the MJPEG stream.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen() {
    val container = appContainer()
    val viewModel: ControlViewModel = viewModel(factory = ControlViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Poll only while this screen is on-screen; stop when the user switches tabs.
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control") },
                actions = { ConnectedChip(ready = state.recordModeReady); Box(Modifier.size(12.dp)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LiveViewPlaceholder()
            ExposureGrid()
            StatusStrip(state.status)
            Box(Modifier.weight(1f))
            ControlsRow(
                enabled = state.recordModeReady && !state.busy,
                recording = state.videoRecording,
                onAutofocus = viewModel::autofocus,
                onCapture = viewModel::capture,
                onToggleVideo = viewModel::toggleVideoRecord,
            )
            Text(
                text = "Live view, exposure read-out & standalone autofocus arrive in MVP3 " +
                    "(the camera needs the live stream running for remote AF). The shutter " +
                    "autofocuses on its own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConnectedChip(ready: Boolean) {
    val color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = if (ready) "record mode" else "arming…",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** Dark 3:2 placeholder standing in for the future MJPEG live-view feed. */
@Composable
private fun LiveViewPlaceholder() {
    Surface(
        color = Color(0xFF0B0D0F),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 2f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(52.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
            )
            Text(
                text = "live view · MJPEG :49152",
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            )
        }
    }
}

/** Four-up ISO / Aperture / Shutter / EV grid. Values are placeholders until live view lands. */
@Composable
private fun ExposureGrid() {
    val cells = listOf("ISO" to "—", "Aperture" to "—", "Shutter" to "—", "EV" to "—")
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            cells.forEach { (label, value) ->
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/** Real camera status from getstate: battery, mode and SD-card. Shows "—" for anything unknown. */
@Composable
private fun StatusStrip(status: CameraStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusItem("BATTERY", status.battery ?: "—")
        StatusItem("MODE", status.mode ?: "—")
        StatusItem("SD", status.sdCard ?: "—")
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

/** AF (left) · Shutter (center, large) · REC (right), matching the wireframe. */
@Composable
private fun ControlsRow(
    enabled: Boolean,
    recording: Boolean,
    onAutofocus: () -> Unit,
    onCapture: () -> Unit,
    onToggleVideo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingButton(
            label = "AF",
            enabled = enabled,
            onClick = onAutofocus,
            icon = Icons.Default.CenterFocusStrong,
            contentDescription = "Autofocus",
        )
        ShutterButton(enabled = enabled, onClick = onCapture)
        RingButton(
            label = if (recording) "STOP" else "REC",
            enabled = enabled,
            tint = MaterialTheme.colorScheme.error,
            onClick = onToggleVideo,
            icon = if (recording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
            contentDescription = "Record video",
        )
    }
}

@Composable
private fun RingButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val alpha = if (enabled) 1f else 0.4f
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = alpha), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint.copy(alpha = alpha))
        }
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
    }
}

/** The big amber capture button. */
@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(3.dp, accent.copy(alpha = alpha), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(accent.copy(alpha = alpha)))
    }
}
