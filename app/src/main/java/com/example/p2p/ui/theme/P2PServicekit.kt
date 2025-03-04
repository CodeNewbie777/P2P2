package com.example.p2papp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.example.p2papp.wifi.WifiDirectManager

class P2PService : Service() {
    private lateinit var wifiManager: WifiDirectManager

    override fun onCreate() {
        super.onCreate()
        try {
            wifiManager = WifiDirectManager(this)
            startServers()
        } catch (e: Exception) {
            stopSelf()
            Log.e("P2PService", "Failed to initialize: ${e.message}")
        }
    }

    private fun startServers() {
        wifiManager.startServer(8888) { message ->
            sendBroadcast(Intent("CHAT_MESSAGE").putExtra("message", message))
        }
        wifiManager.receiveFile(9999, "/storage/emulated/0/Download/received_${System.currentTimeMillis()}.file") { progress ->
            sendBroadcast(Intent("FILE_PROGRESS").putExtra("progress", progress))
        }
        if (!wifiManager.isGroupOwner()) {
            wifiManager.startVideoServer(6666) { frame ->
                sendBroadcast(Intent("VIDEO_FRAME").putExtra("frame", Base64.encodeToString(frame, Base64.DEFAULT)))
            }
        }
        wifiManager.startServer(7777) { audio ->
            sendBroadcast(Intent("PTT_AUDIO").putExtra("audio", audio))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        wifiManager.stop()
        super.onDestroy()
    }
}