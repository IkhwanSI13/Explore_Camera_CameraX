package com.ikhwankoto.cameraandcamerax.useCase.cameraIntent

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ikhwankoto.cameraandcamerax.R
import kotlinx.android.synthetic.main.activity_simple_camera.*

class SimpleCameraActivity : AppCompatActivity() {

    var CAMERA_REQUEST = 1888
    var MY_CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_camera)

        btn_camera.setOnClickListener {
            if (checkCameraHardware(this))
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA), MY_CAMERA_PERMISSION_CODE
                    )
                } else {
                    takePictureWithCamera()
                }
            else
                Toast.makeText(
                    this,
                    "Device tidak memiliki kamera", Toast.LENGTH_SHORT
                ).show()

        }
    }

    private fun takePictureWithCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, CAMERA_REQUEST)
    }

    private fun checkCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_CAMERA_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0]
                            == PackageManager.PERMISSION_GRANTED)
                ) {
                    takePictureWithCamera()
                } else {
                    Toast.makeText(
                        this,
                        "Aplikasi membutuhkan ijin untuk menjalankan kamera",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
            else -> {
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {

            if (requestCode == CAMERA_REQUEST) {
                val photo = data!!.extras!!.get("data") as Bitmap?
                photo?.let {
                    iv_image.setImageBitmap(it)
                }
            }
        }
    }

}
