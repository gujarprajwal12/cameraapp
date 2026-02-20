package com.psg.cameraapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.psg.cameraapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                getCameraIds()
            } else {
                Toast.makeText(this, "Permissions required!", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()

        binding.btncaptre.setOnClickListener {

            val cameraId = binding.edtcameraid.text.toString().trim()

            if (cameraId.isEmpty()) {
                Toast.makeText(this, "Enter camera ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

            if (!manager.cameraIdList.contains(cameraId)) {
                Toast.makeText(this, "Invalid Camera ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startCameraService(cameraId)
        }
    }

    private fun requestPermissions() {

        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CAMERA)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun getCameraIds() {

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val result = StringBuilder()

        for (id in manager.cameraIdList) {

            val characteristics =
                manager.getCameraCharacteristics(id)

            val facing = when (
                characteristics.get(CameraCharacteristics.LENS_FACING)
            ) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                else -> "Other"
            }

            result.append("Camera ID: $id\nFacing: $facing\n\n")
        }

        binding.txtcameraid.text = result.toString()
    }

    private fun startCameraService(cameraId: String) {
        val intent = Intent(this, CameraRecordService::class.java)
        intent.putExtra("CAMERA_ID", cameraId)
        ContextCompat.startForegroundService(this, intent)

        Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show()
    }
}

