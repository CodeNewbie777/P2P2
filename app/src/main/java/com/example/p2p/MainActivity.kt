package com.example.p2papp

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.p2papp.ui.theme.P2PAppTheme
import com.example.p2papp.wifi.WifiDirectManager

class MainActivity : ComponentActivity() {
    private lateinit var wifiManager: WifiDirectManager
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.all { it.value }) initializeApp() else finish()
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val filePath = getPathFromUri(it)
            filePath?.let { path ->
                val targetIp = wifiManager.getGroupOwnerAddress() ?: return@let
                wifiManager.sendFile(targetIp, 9999, path,
                    onProgress = { progress -> sendBroadcast(Intent("FILE_PROGRESS").putExtra("progress", progress)) },
                    onError = { error -> sendBroadcast(Intent("FILE_ERROR").putExtra("error", error)) }
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = WifiDirectManager(this)
        checkPermissions()
    }

    private fun checkPermissions() {
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            initializeApp()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun initializeApp() {
        startService(Intent(this, P2PService::class.java))
        setContent {
            P2PAppTheme(darkTheme = true) {
                val navController = rememberNavController()
                Scaffold(topBar = { TopAppBar(title = { Text("P2P App") }) }) { padding ->
                    NavHost(navController = navController, startDestination = "home", modifier = Modifier.padding(padding)) {
                        composable("home") { HomeScreen(navController, wifiManager) }
                        composable("chat") { ChatScreen(wifiManager) }
                        composable("video") { VideoScreen(wifiManager) }
                        composable("history") { HistoryScreen() }
                    }
                }
            }
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            it.moveToFirst()
            val index = it.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA)
            if (index >= 0) it.getString(index) else null
        }
    }

    fun launchFilePicker() = filePickerLauncher.launch("*/*")

    override fun onDestroy() {
        stopService(Intent(this, P2PService::class.java))
        wifiManager.stop()
        super.onDestroy()
    }
