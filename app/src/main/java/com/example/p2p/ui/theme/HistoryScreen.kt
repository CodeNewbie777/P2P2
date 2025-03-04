package com.example.p2papp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen() {
    val history = remember { mutableStateListOf<String>() }
    val context = LocalContext.current

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "FILE_SENT" -> history.add("Sent: ${intent.getStringExtra("file")}")
                    "FILE_RECEIVED" -> history.add("Received: ${intent.getStringExtra("file")}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction("FILE_SENT")
            addAction("FILE_RECEIVED")
        })
    }

    DisposableEffect(Unit) {
        onDispose { context.unregisterReceiver(receiver) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("File Transfer History", style = MaterialTheme.typography.h5, color = MaterialTheme.colors.primary) // Updated
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(history) { entry -> Text(entry, style = MaterialTheme.typography.body1) }
        }
    }
}