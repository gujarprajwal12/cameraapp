package com.psg.cameraapp


import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraRecordService : LifecycleService() {

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var recording: Recording? = null
    private lateinit var executor: ExecutorService
    private var cameraId: String = "0"

    override fun onCreate() {
        super.onCreate()
        executor = Executors.newSingleThreadExecutor()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)

        cameraId = intent?.getStringExtra("CAMERA_ID") ?: "0"
        startCamera()
        return START_STICKY
    }

    private fun createNotification(): Notification {

        val channelId = "camera_service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Camera Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording Video")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val selector = if (cameraId == "1")
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this,
                selector,
                videoCapture
            )

            startRecording()

        }, ContextCompat.getMainExecutor(this)) // ✅ MAIN THREAD
    }

    private fun startRecording() {

        val file = File(getExternalFilesDir(null), "full_video.mp4")
        val outputOptions = FileOutputOptions.Builder(file).build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(executor) { event ->

                if (event is VideoRecordEvent.Finalize) {
                    // Recording finished
                }
            }
//
//        Handler(Looper.getMainLooper()).postDelayed({
//            recording?.stop()
//            stopSelf()
//        }, 120000) // 2 Minutes
//

        Handler(Looper.getMainLooper()).postDelayed({
            recording?.stop()
            stopSelf()
        }, 60000) // 1 Minutes
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}