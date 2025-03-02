package com.example.p2p.ui.theme

package com.example.p2papp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.p2papp.wifi.WifiDirectManager
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(wifiManager: WifiDirectManager) {
    var message by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<String>()) }
    var isGroupChat by remember { mutableStateOf(false) }
    var selectedPeer by remember { mutableStateOf<WifiP2pDevice?>(null) }
    val peers by remember { mutableStateOf(wifiManager.getPeers()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "CHAT_MESSAGE" -> messages = messages + intent.getStringExtra("message")!!
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        context.registerReceiver(receiver, IntentFilter("CHAT_MESSAGE"))
        wifiManager.startDiscovery(
            onSuccess = { scope.launch { snackbarHostState.showSnackbar("Discovery started") } },
            onFailure = { scope.launch { snackbarHostState.showSnackbar("Error: $it") } }
        )
    }

    DisposableEffect(Unit) {
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(if (isGroupChat) "Group Chat" else "Direct Message", style = MaterialTheme.typography.h5)
            Switch(checked = isGroupChat, onCheckedChange = { isGroupChat = it })
            if (!isGroupChat && peers.isNotEmpty()) {
                DropdownMenu(expanded = true, onDismissRequest = {}) {
                    peers.forEach { peer ->
                        DropdownMenuItem(onClick = {
                            selectedPeer = peer
                            wifiManager.connectToPeer(peer, {}, { scope.launch { snackbarHostState.showSnackbar("Connection failed: $it") } })
                        }) { Text(peer.deviceName) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages) { msg -> Text(msg) }
            }
            OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") })
            Button(onClick = {
                if (isGroupChat) {
                    wifiManager.broadcastData(8888, message)
                } else {
                    val targetIp = selectedPeer?.deviceAddress ?: wifiManager.getGroupOwnerAddress() ?: return@Button
                    wifiManager.sendData(targetIp, 8888, message)
                }
                messages = messages + "Me: $message"
                message = ""
            }) { Text("Send") }
        }
    }
}
