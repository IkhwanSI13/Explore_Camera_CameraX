package com.ikhwankoto.cameraandcamerax

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ikhwankoto.cameraandcamerax.cameraX.CameraXActivity
import com.ikhwankoto.cameraandcamerax.cameraIntent.SimpleCameraActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_camera_intent.setOnClickListener {
            startActivity(Intent(this, SimpleCameraActivity::class.java))
        }

        btn_camera_x.setOnClickListener {
            startActivity(Intent(this, CameraXActivity::class.java))
        }
    }

}
