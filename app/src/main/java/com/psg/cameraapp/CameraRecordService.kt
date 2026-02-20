package com.psg.cameraapp

import android.Manifest
import android.app.*
import android.content.Intent
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ActivityCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraRecordService : Service() {

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null

    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler

    private lateinit var cameraId: String

    private var chipCount = 0
    private val maxChips = 4
    private val chipDuration = 30_000L

    override fun onCreate() {
        super.onCreate()

        cameraThread = HandlerThread("CameraThread")
        cameraThread.start()
        cameraHandler = Handler(cameraThread.looper)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        cameraId = intent?.getStringExtra("CAMERA_ID") ?: return START_NOT_STICKY

        startForeground(1, createNotification())

        startRecordingChip()

        return START_STICKY
    }

    private fun startRecordingChip() {

        if (chipCount >= maxChips) {
            stopSelf()
            return
        }

        chipCount++

        setupMediaRecorder()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        cameraManager.openCamera(cameraId, stateCallback, cameraHandler)
    }

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
        }
    }

    private fun createSession() {

        val recorderSurface = mediaRecorder!!.surface

        val requestBuilder =
            cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(recorderSurface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            }

        cameraDevice!!.createCaptureSession(
            listOf(recorderSurface),
            object : CameraCaptureSession.StateCallback() {

                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session

                    session.setRepeatingRequest(
                        requestBuilder.build(),
                        null,
                        cameraHandler
                    )

                    mediaRecorder?.start()

                    Handler(Looper.getMainLooper()).postDelayed({
                        stopRecordingChip()
                    }, chipDuration)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            cameraHandler
        )
    }

    private fun stopRecordingChip() {

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (_: Exception) {}

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}

        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null

        cameraDevice?.close()

        startRecordingChip()
    }

    private fun setupMediaRecorder() {

        val file = createVideoFile()

        val (width, height) = getSupportedVideoSize()

        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(file.absolutePath)
            setVideoEncodingBitRate(10_000_000)
            setVideoFrameRate(30)
            setVideoSize(width, height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }
    }

    private fun getSupportedVideoSize(): Pair<Int, Int> {

        val characteristics =
            cameraManager.getCameraCharacteristics(cameraId)

        val map =
            characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )

        val size =
            map!!.getOutputSizes(MediaRecorder::class.java)[0]

        return Pair(size.width, size.height)
    }

    private fun createVideoFile(): File {

        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())

        return File(
            getExternalFilesDir(null),
            "chip_${chipCount}_$timeStamp.mp4"
        )
    }

    private fun createNotification(): Notification {

        val channelId = "camera_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Camera Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording Video")
            .setContentText("Recording...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onDestroy() {
        cameraThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}