package com.ikhwankoto.cameraandcamerax.cameraX

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ikhwankoto.cameraandcamerax.R
import java.io.File

class CameraXActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private val IMMERSIVE_FLAG_TIMEOUT = 500L

    companion object {
        const val KEY_EVENT_ACTION = "key_event_action"
        const val KEY_EVENT_EXTRA = "key_event_extra"

        /** Use external media if it is available, our app's file directory otherwise */
        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
            }
            if (mediaDir != null && mediaDir.exists()) {
                // External Storage so another apps can see the picture
                return mediaDir
            } else {
                // Internal Storage so just this app can use the picture
                // except if the phone is rooted
                return appContext.filesDir
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_x)
        container = findViewById(R.id.fragment_container)

        // todo photo
        // Init fragment if bundle is null
        // Prepare if rotation happen
        if (savedInstanceState == null) {
            val test = CameraFragment()
            test.arguments = intent.extras
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, test, "your_fragment_tag").commit()
        }
    }

    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        container.postDelayed({
            container.systemUiVisibility = FLAGS_FULLSCREEN
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    /** When key down event is triggered, relay it via local broadcast so fragments can handle it */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val intent = Intent(KEY_EVENT_ACTION).apply {
                    putExtra(
                        KEY_EVENT_EXTRA, keyCode
                    )
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

}
