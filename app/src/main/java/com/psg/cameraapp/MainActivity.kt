package com.psg.cameraapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.psg.cameraapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }



    permissionLauncher.launch(
    arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
    )
    )

    binding.btnBackCamera.setOnClickListener {
        startService("0")
    }

    binding.btnFrontCamera.setOnClickListener {
        startService("1")
    }
}

private fun startService(cameraId: String) {
    val intent = Intent(this, CameraRecordService::class.java)
    intent.putExtra("CAMERA_ID", cameraId)
    ContextCompat.startForegroundService(this, intent)
}
}

