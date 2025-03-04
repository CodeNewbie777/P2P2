package com.example.p2p.ui.theme

package com.example.p2papp

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.example.p2papp.wifi.WifiDirectManager
import java.io.File
import java.io.FileOutputStream

class PTTManager(private val wifiManager: WifiDirectManager) {
    private var recorder: AudioRecord? = null
    private var isRecording = false
    private val player = MediaPlayer()

    fun startPTT() {
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        recorder?.startRecording()
        isRecording = true

        Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = recorder?.read(buffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    val targetIp = wifiManager.getGroupOwnerAddress() ?: continue
                    wifiManager.sendData(targetIp, 7777, Base64.encodeToString(buffer.copyOf(read), Base64.DEFAULT))
                }
            }
        }.start()
    }

    fun stopPTT() {
        isRecording = false
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    fun playAudio(audioData: String) {
        try {
            val bytes = Base64.decode(audioData, Base64.DEFAULT)
            val tempFile = File.createTempFile("ptt", ".pcm", wifiManager.context.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }
            player.reset()
            player.setDataSource(tempFile.path)
            player.prepare()
            player.start()
            player.setOnCompletionListener { tempFile.delete() }
        } catch (e: Exception) {
            Log.e("PTTManager", "Audio playback failed: ${e.message}")
        }
    }
}