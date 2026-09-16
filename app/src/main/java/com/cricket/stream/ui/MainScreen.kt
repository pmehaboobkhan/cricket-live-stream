package com.cricket.stream.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cricket.stream.streaming.StreamEngine

/**
 * MainComposableScreen displays the cricket streaming app UI with:
 * - Video preview surface (connected to StreamEngine)
 * - Scorecard overlay config panel
 * - Stream health indicators (bitrate, connection status)
 * - Start/Stop controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onBackPressed: () -> Unit = {},
    onStartStreaming: () -> Unit = {},
    onStopStreaming: () -> Unit = {},
    onToggleCamera: () -> Unit = {},
    scorecardUrl: String,
    onUpdateScorecardUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var streamState by remember { mutableStateOf(StreamState.IDLE) }
    var currentBitrate by remember { mutableStateOf(0) }
    var connectionStatus by remember { mutableStateOf("IDLE") }
    var isMuted by remember { mutableStateOf(false) }
    var scorecardUrlInput by remember(scorecardUrl) { mutableStateOf(scorecardUrl) }
    
    // Stream engine reference (initialized in activity onCreate lifecycle)
    val streamEngine = remember(context) { 
        StreamEngine(context).apply { 
            onStreamHealth = { health ->
                currentBitrate = health.currentBitrate
                connectionStatus = if (health.isStreaming) "CONNECTED" else health.connectionStatus
            }
            onError = { msg -> connectionStatus = "ERROR: $msg" }
        }
    }

    // Initialize stream engine on first compose
    LaunchedEffect(streamEngine) {
        streamEngine.init()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cricket Live Stream", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* Settings panel (not in MVP) */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Video preview area (placeholder in MVP — SurfaceView rendered below)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (streamState == StreamState.STREAMING) {
                            Text("● LIVE", color = MaterialTheme.colorScheme.primary)
                            // In production, this is where OpenGlView preview would render
                        } else {
                            Text(
                                "No signal — connect camera\nvia HDMI + USB-C capture card",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stream health indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text("Bitrate: $currentBitrate bps")
                    Text("Status:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(connectionStatus, color = statusColor(connectionStatus))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scorecard overlay configuration
                OutlinedTextField(
                    value = scorecardUrlInput,
                    onValueChange = { 
                        scorecardUrlInput = it
                        onUpdateScorecardUrl(it)
                    },
                    label = { Text("Scorecard URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (streamState == StreamState.IDLE) {
                        Button(
                            onClick = { 
                                streamState = StreamState.STARTING  
                                streamEngine.startStream("rtmps://a.rtmp.youtube.com:443/live2/{STREAM_KEY}")
                                onStartStreaming()
                                streamState = StreamState.STREAMING
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("START STREAM") }
                    } else {
                        Button(
                            onClick = {
                                streamEngine.stopStream()
                                streamState = StreamState.IDLE
                                onStopStreaming()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("STOP STREAM") }
                    }

                    FilledTonalButton(onClick = { onToggleCamera() }) {
                        Icon(Icons.Default.Videocam, contentDescription = if (isMuted) "Unmute" else "Mute")
                    }
                }

                // Stream profile selector (1080p default, 720p low bandwidth option)
                Spacer(modifier = Modifier.height(16.dp))
                
                DropdownMenu(
                    expanded = false, onDismissRequest = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DropdownMenuItem(
                        text = { Text("1080p30 @ 8.5 Mbps (default — high quality)") },
                        onClick = {}
                    )
                    DropdownMenuItem(
                        text = { Text("720p30 @ 4 Mbps (low bandwidth — mobile data friendly)") },
                        onClick = {}
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Footer warning about device temperatures and battery drain
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "⚠️ Always tether to power during streams.\n" +
                        "Keep the phone cool (remove case, shade in sun).\n" +
                        "Test cellular upload speed before live event.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    )
}

enum class StreamState { IDLE, STARTING, STREAMING, STOPPING, ERROR }

@Composable
private fun statusColor(status: String): androidx.compose.ui.graphics.Color {
    return when {
        status.contains("CONNECTED") -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        status.contains("ERROR") || status.contains("FAILED") -> androidx.compose.material3.MaterialTheme.colorScheme.error
        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    }
}
