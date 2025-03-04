package com.example.p2papp.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.util.Log
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import android.util.Base64

class WifiDirectManager(private val context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, android.os.Looper.getMainLooper(), null)
    private val peers = mutableListOf<WifiP2pDevice>()
    private var groupInfo: WifiP2pGroup? = null
    private var connectionInfo: WifiP2pInfo? = null
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> manager?.requestPeers(channel, peerListListener)
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    connectionInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                    manager?.requestGroupInfo(channel) { group ->
                        groupInfo = group
                        if (connectionInfo?.groupFormed != true) context.sendBroadcast(Intent("CONNECTION_LOST"))
                    }
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) Log.d("WifiDirect", "Wi-Fi Direct enabled")
                }
            }
        }
    }

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        peers.clear()
        peers.addAll(peerList.deviceList)
        Log.d("WifiDirect", "Peers updated: ${peers.map { it.deviceName }}")
    }

    fun getPeers(): List<WifiP2pDevice> = peers
    fun isGroupOwner(): Boolean = connectionInfo?.isGroupOwner == true
    fun getGroupOwnerAddress(): String? = connectionInfo?.groupOwnerAddress?.hostAddress

    fun startDiscovery(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        context.registerReceiver(receiver, intentFilter)
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onSuccess()
            override fun onFailure(reason: Int) = onFailure("Discovery failed: $reason")
        })
    }

    fun connectToPeer(device: WifiP2pDevice, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onSuccess()
            override fun onFailure(reason: Int) = onFailure("Connection failed: $reason")
        })
    }

    fun attemptReconnect(peer: WifiP2pDevice) = connectToPeer(peer, { Log.d("WifiDirect", "Reconnected") }, { Log.d("WifiDirect", "Reconnect failed: $it") })

    fun startServer(port: Int, onDataReceived: (String) -> Unit) {
        Thread {
            var sock: Socket? = null
            try {
                serverSocket = ServerSocket(port)
                sock = serverSocket?.accept()
                val reader = BufferedReader(InputStreamReader(sock?.getInputStream()))
                while (true) {
                    val data = reader.readLine() ?: break
                    onDataReceived(data)
                }
            } catch (e: Exception) {
                Log.e("WifiDirect", "Server failed on port $port: ${e.message}")
            } finally {
                sock?.close()
                serverSocket?.close()
            }
        }.start()
    }

    fun sendData(targetAddress: String, port: Int, data: String) {
        Thread {
            var sock: Socket? = null
            try {
                sock = Socket(targetAddress, port)
                val writer = PrintWriter(sock.getOutputStream(), true)
                writer.println(data)
            } catch (e: Exception) {
                Log.e("WifiDirect", "Send data failed: ${e.message}")
            } finally {
                sock?.close()
            }
        }.start()
    }

    fun broadcastData(port: Int, data: String) {
        Thread {
            peers.forEach { peer ->
                var sock: Socket? = null
                try {
                    sock = Socket(peer.deviceAddress, port)
                    val writer = PrintWriter(sock.getOutputStream(), true)
                    writer.println(data)
                } catch (e: Exception) {
                    Log.e("WifiDirect", "Failed to send to ${peer.deviceName}: ${e.message}")
                } finally {
                    sock?.close()
                }
            }
        }.start()
    }

    fun sendChunkedData(targetAddress: String, port: Int, data: ByteArray, chunkSize: Int = 1024) {
        Thread {
            var sock: Socket? = null
            try {
                sock = Socket(targetAddress, port)
                val output = sock.getOutputStream()
                var sequence = 0
                for (i in 0 until data.size step chunkSize) {
                    val chunk = data.copyOfRange(i, minOf(i + chunkSize, data.size))
                    val packet = "SEQ:$sequence:${Base64.encodeToString(chunk, Base64.DEFAULT)}".toByteArray()
                    output.write(packet)
                    sequence++
                }
            } catch (e: Exception) {
                Log.e("WifiDirect", "Send chunked data failed: ${e.message}")
            } finally {
                sock?.close()
            }
        }.start()
    }

    fun startVideoServer(port: Int, onFrameReceived: (ByteArray) -> Unit) {
        Thread {
            var sock: Socket? = null
            try {
                serverSocket = ServerSocket(port)
                sock = serverSocket?.accept()
                val input = sock?.getInputStream()
                val buffer = ByteArray(65536)
                val frameParts = mutableMapOf<Int, String>()
                while (true) {
                    val bytesRead = input?.read(buffer) ?: break
                    val packet = String(buffer, 0, bytesRead)
                    val parts = packet.split(":", limit = 3)
                    if (parts.size == 3 && parts[0] == "SEQ") {
                        val seq = parts[1].toInt()
                        frameParts[seq] = parts[2]
                        if (frameParts.size >= 5) {
                            val fullFrame = frameParts.toSortedMap().values.joinToString("").let { Base64.decode(it, Base64.DEFAULT) }
                            onFrameReceived(fullFrame)
                            frameParts.clear()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WifiDirect", "Video server failed: ${e.message}")
            } finally {
                sock?.close()
                serverSocket?.close()
            }
        }.start()
    }

    fun sendFile(targetAddress: String, port: Int, filePath: String, onProgress: (Float) -> Unit, onError: (String) -> Unit) {
        Thread {
            val file = File(filePath)
            var attempts = 0
            val maxAttempts = 3
            while (attempts < maxAttempts) {
                var sock: Socket? = null
                try {
                    sock = Socket(targetAddress, port)
                    val output = DataOutputStream(sock.getOutputStream())
                    output.writeLong(file.length())
                    FileInputStream(file).use { input ->
                        val totalSize = file.length()
                        var bytesSent = 0L
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesSent += bytesRead
                            onProgress(bytesSent.toFloat() / totalSize)
                        }
                    }
                    context.sendBroadcast(Intent("FILE_SENT").putExtra("file", filePath))
                    break
                } catch (e: Exception) {
                    attempts++
                    onError("Attempt $attempts failed: ${e.message}")
                    if (attempts < maxAttempts) {
                        Thread.sleep(1000)
                        peers.firstOrNull { it.deviceAddress == targetAddress }?.let { attemptReconnect(it) }
                    } else {
                        onError("File transfer failed after $maxAttempts attempts")
                    }
                } finally {
                    sock?.close()
                }
            }
        }.start()
    }

    fun receiveFile(port: Int, savePath: String, onProgress: (Float) -> Unit) {
        Thread {
            var sock: Socket? = null
            try {
                serverSocket = ServerSocket(port)
                sock = serverSocket?.accept()
                val input = DataInputStream(sock?.getInputStream())
                val fileSize = input.readLong()
                FileOutputStream(savePath).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        onProgress(totalBytes.toFloat() / fileSize)
                    }
                }
                context.sendBroadcast(Intent("FILE_RECEIVED").putExtra("file", savePath))
            } catch (e: Exception) {
                Log.e("WifiDirect", "Receive file failed: ${e.message}")
            } finally {
                sock?.close()
                serverSocket?.close()
            }
        }.start()
    }

    fun stop() {
        context.unregisterReceiver(receiver)
        socket?.close()
        serverSocket?.close()
        manager?.cancelConnect(channel, null)
    }
}