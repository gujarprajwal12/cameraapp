package com.psg.cameraapp

import android.Manifest
import android.app.*
import android.content.Intent
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@UnstableApi
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
    private val chipDuration = 30_000L   // 30 seconds per chip
    private var lastRecordedFile: File? = null


    // watermark add code
    private val watermarkText: String
        get() = "©PSG ${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())}"



    private val wmAnchorX = 0f
    private val wmAnchorY = -0.9f
    private val wmAlpha = 0.75f



    override fun onCreate() {
        super.onCreate()

        cameraThread = HandlerThread("CameraThread")
        cameraThread.start()
        cameraHandler = Handler(cameraThread.looper)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cameraId = intent?.getStringExtra("CAMERA_ID") ?: return START_NOT_STICKY

        startForeground(NOTIF_ID, createNotification())
        startRecordingChip()

        return START_STICKY
    }

    override fun onDestroy() {
        cameraThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null


    private fun startRecordingChip() {
        if (chipCount >= maxChips) {
            stopSelf()
            return
        }

        chipCount++

        setupMediaRecorder()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        cameraManager.openCamera(cameraId, stateCallback, cameraHandler)
    }

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCaptureSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
        }
    }

    private fun createCaptureSession() {
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

                    Handler(Looper.getMainLooper()).postDelayed(
                        { stopRecordingChip() },
                        chipDuration
                    )
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    startRecordingChip()
                }
            },
            cameraHandler
        )
    }


    private fun stopRecordingChip() {

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (_: Exception) {}
        captureSession = null


        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null

        cameraDevice?.close()
        cameraDevice = null


        val rawFile = lastRecordedFile
        if (rawFile == null || !rawFile.exists()) {
            startRecordingChip()
            return
        }

        val watermarkedPath = rawFile.absolutePath.replace(".mp4", "_wm.mp4")

        WatermarkHelper.applyTextWatermark(
            context      = this,
            inputPath    = rawFile.absolutePath,
            outputPath   = watermarkedPath,
            watermarkText = watermarkText,
            anchorX      = wmAnchorX,
            anchorY      = wmAnchorY,
            alpha        = wmAlpha,
            onSuccess    = {
                rawFile.delete()
                startRecordingChip()
            },
            onError      = { e ->
                e.printStackTrace()
                startRecordingChip()
            }
        )
    }


    private fun setupMediaRecorder() {
        val file = createVideoFile()
        lastRecordedFile = file           // watermark ad

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
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val size = map!!.getOutputSizes(MediaRecorder::class.java)[0]
        return Pair(size.width, size.height)
    }

    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording Video")
            .setContentText("Recording chip $chipCount / $maxChips…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1
    }
}