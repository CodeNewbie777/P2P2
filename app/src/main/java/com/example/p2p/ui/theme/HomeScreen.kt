package com.example.p2papp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.p2papp.wifi.WifiDirectManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(navController: NavController, wifiManager: WifiDirectManager) {
    val pttManager = remember { PTTManager(wifiManager) }
    var isPTTActive by remember { mutableStateOf(false) }
    val peers by remember { mutableStateOf(wifiManager.getPeers()) }
    var statusMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var fileProgress by remember { mutableStateOf(0f) }
    var showReconnectDialog by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "FILE_PROGRESS" -> fileProgress = intent.getFloatExtra("progress", 0f)
                    "FILE_ERROR" -> fileError = intent.getStringExtra("error")
                    "CONNECTION_LOST" -> showReconnectDialog = true
                    "PTT_AUDIO" -> pttManager.playAudio(intent.getStringExtra("audio")!!)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction("FILE_PROGRESS")
            addAction("FILE_ERROR")
            addAction("CONNECTION_LOST")
            addAction("PTT_AUDIO")
        })
        wifiManager.startDiscovery(
            onSuccess = { statusMessage = "Discovery started" },
            onFailure = { statusMessage = "Discovery failed: $it" }
        )
    }

    DisposableEffect(Unit) {
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(fileError) {
        fileError?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            fileProgress = 0f
            fileError = null
        }
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar(statusMessage)
                delay(2000)
                statusMessage = ""
            }
        }
    }

    if (showReconnectDialog && peers.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showReconnectDialog = false },
            title = { Text("Connection Lost") },
            text = { Text("Reconnect to ${peers.first().deviceName}?") },
            confirmButton = {
                TextButton(onClick = {
                    wifiManager.attemptReconnect(peers.first())
                    showReconnectDialog = false
                }) { Text("Yes") }
            },
            dismissButton = { TextButton(onClick = { showReconnectDialog = false }) { Text("No") } }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Connected Peers", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
            Card(modifier = Modifier.fillMaxWidth().height(120.dp), elevation = 4.dp) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(peers) { peer ->
                        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(peer.deviceName, style = MaterialTheme.typography.body1)
                            Text(peer.deviceAddress, style = MaterialTheme.typography.body2, color = Color.Gray)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("chat") }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Icon(Icons.Default.Chat, contentDescription = "Chat", modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Chat")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("video") }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Icon(Icons.Default.Videocam, contentDescription = "Video", modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Video")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { (context as MainActivity).launchFilePicker() }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Icon(Icons.Default.UploadFile, contentDescription = "File", modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send File")
            }
            AnimatedVisibility(visible = fileProgress > 0f) {
                Column {
                    LinearProgressIndicator(
                        progress = animateFloatAsState(targetValue = fileProgress).value,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colors.secondary
                    )
                    Text("Progress: ${(fileProgress * 100).toInt()}%", style = MaterialTheme.typography.caption)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                if (isPTTActive) pttManager.stopPTT() else pttManager.startPTT()
                isPTTActive = !isPTTActive
            }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Icon(if (isPTTActive) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "PTT", modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPTTActive) "Release PTT" else "Push to Talk")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("history") }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("File History")
            }
        }
    }
}