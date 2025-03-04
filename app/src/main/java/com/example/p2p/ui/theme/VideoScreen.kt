package com.example.p2papp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.p2papp.wifi.WifiDirectManager
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import android.media.Image as AndroidImage

@Composable
fun VideoScreen(wifiManager: WifiDirectManager) {
    var isStreaming by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(true) }
    val frameBuffer = remember { mutableStateListOf<Bitmap>() }
    val context = LocalContext.current
    val cameraExecutor: java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor()

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "VIDEO_FRAME" -> {
                        val frameData = intent.getStringExtra("frame")!!
                        val bytes = android.util.Base64.decode(frameData, android.util.Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (frameBuffer.size < 5) frameBuffer.add(bitmap)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        context.registerReceiver(receiver, IntentFilter("VIDEO_FRAME"))
    }

    DisposableEffect(Unit) {
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(isStreaming, useFrontCamera) {
        if (isStreaming) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(cameraExecutor) { image ->
                            val bitmap = image.toBitmap()
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
                            val data = outputStream.toByteArray()
                            val targetIp = wifiManager.getGroupOwnerAddress() ?: return@setAnalyzer
                            wifiManager.sendChunkedData(targetIp, 6666, data)
                            image.close()
                            delay(66)
                        }
                    }
                val cameraSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(context as MainActivity, cameraSelector, preview, imageAnalysis)
            }, ContextCompat.getMainExecutor(context))
        }
    }

    LaunchedEffect(frameBuffer.size) {
        if (frameBuffer.isNotEmpty()) {
            delay(66)
            frameBuffer.removeAt(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isStreaming) {
            AndroidView(factory = { PreviewView(it) }, modifier = Modifier.weight(1f)) { view ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().also {
                            it.setAnalyzer(cameraExecutor) { image ->
                                val bitmap = image.toBitmap()
                                val outputStream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
                                val data = outputStream.toByteArray()
                                val targetIp = wifiManager.getGroupOwnerAddress() ?: return@setAnalyzer
                                wifiManager.sendChunkedData(targetIp, 6666, data)
                                image.close()
                                delay(66)
                            }
                        }
                    val cameraSelector = if (useFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(context as MainActivity, cameraSelector, preview, imageAnalysis)
                }, ContextCompat.getMainExecutor(context))
            }
        }
        frameBuffer.firstOrNull()?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "Received Video", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { isStreaming = true }) { Text("Start Video") }
            Button(onClick = { isStreaming = false }) { Text("Stop Video") }
            Button(onClick = { useFrontCamera = !useFrontCamera }) { Text("Switch Camera") }
        }
    }
}

fun AndroidImage.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = android.media.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 60, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}