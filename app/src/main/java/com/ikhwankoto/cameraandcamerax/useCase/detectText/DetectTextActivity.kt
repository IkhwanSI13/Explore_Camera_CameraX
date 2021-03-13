package com.ikhwankoto.cameraandcamerax.useCase.detectText

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.ikhwankoto.cameraandcamerax.R
import com.ikhwankoto.cameraandcamerax.analyzer.TextAnalyzer
import com.ikhwankoto.cameraandcamerax.util.Language
import com.ikhwankoto.cameraandcamerax.util.ScopedExecutor
import kotlinx.android.synthetic.main.activity_detect_text.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class DetectTextActivity : AppCompatActivity() {

    companion object {
        // We only need to analyze the part of the image that has text, so we set crop percentages
        // to avoid analyze the entire image from the live camera feed.
        const val DESIRED_WIDTH_CROP_PERCENT = 8
        const val DESIRED_HEIGHT_CROP_PERCENT = 74

        // Permission
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private const val TAG = "Ikhwan"
    }

    private var displayId: Int = -1
    private lateinit var viewModel: DetectTextModel

    /** CAMERA X */
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null

    /** CAMERA X | Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var scopedExecutor: ScopedExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detect_text)

        viewModel = ViewModelProvider(this).get(DetectTextModel::class.java)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        scopedExecutor = ScopedExecutor(cameraExecutor)

        // Request camera permissions
        if (allPermissionsGranted()) {
            // Wait for the views to be properly laid out
            viewfinder.post {
                // Keep track of the display in which this view is attached
                displayId = viewfinder.display.displayId

                // Set up the camera and its use cases
                setUpCamera()
            }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Get available language list and set up the target language spinner
        // with default selections.
        setUpTranslationSpinner()

        // UI - Text result
        viewModel.sourceText.observe(this, Observer {
            srcText.text = it
        })

        // UI - Source Language
        viewModel.sourceLang.observe(this, Observer { srcLang.text = it.displayName })

        // UI - Translated Text
        viewModel.translatedText.observe(this, Observer { resultOrError ->
            resultOrError?.let {
                if (it.error != null) {
                    translatedText.error = resultOrError.error?.localizedMessage
                } else {
                    translatedText.text = resultOrError.result
                }
            }
        })

        // UI - Model Download Status
        viewModel.modelDownloading.observe(this, Observer { isDownloading ->
            progressBar.visibility = if (isDownloading) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            progressText.visibility = progressBar.visibility
        })

        // UI - Extra layer above camera preview, for show a box and guide text
        overlay.apply {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceChanged(
                    holder: SurfaceHolder?,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                }

                override fun surfaceDestroyed(holder: SurfaceHolder?) {
                }

                override fun surfaceCreated(holder: SurfaceHolder?) {
                    holder?.let {
                        drawOverlay(
                            it,
                            DESIRED_HEIGHT_CROP_PERCENT,
                            DESIRED_WIDTH_CROP_PERCENT
                        )
                    }
                }

            })
        }
    }

    private fun setUpTranslationSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item, viewModel.availableLanguages
        )

        targetLangSelector.adapter = adapter
        targetLangSelector.setSelection(adapter.getPosition(Language("en")))
        targetLangSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                viewModel.targetLang.value = adapter.getItem(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun drawOverlay(
        holder: SurfaceHolder,
        heightCropPercent: Int,
        widthCropPercent: Int
    ) {
        val canvas = holder.lockCanvas()
        val bgPaint = Paint().apply {
            alpha = 140
        }
        canvas.drawPaint(bgPaint)
        val rectPaint = Paint()
        rectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        rectPaint.style = Paint.Style.FILL
        rectPaint.color = Color.WHITE
        val outlinePaint = Paint()
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.color = Color.WHITE
        outlinePaint.strokeWidth = 4f
        val surfaceWidth = holder.surfaceFrame.width()
        val surfaceHeight = holder.surfaceFrame.height()

        val cornerRadius = 25f
        // Set rect centered in frame
        val rectTop = surfaceHeight * heightCropPercent / 2 / 100f
        val rectLeft = surfaceWidth * widthCropPercent / 2 / 100f
        val rectRight = surfaceWidth * (1 - widthCropPercent / 2 / 100f)
        val rectBottom = surfaceHeight * (1 - heightCropPercent / 2 / 100f)
        val rect = RectF(rectLeft, rectTop, rectRight, rectBottom)
        canvas.drawRoundRect(
            rect, cornerRadius, cornerRadius, rectPaint
        )
        canvas.drawRoundRect(
            rect, cornerRadius, cornerRadius, outlinePaint
        )
        val textPaint = Paint()
        textPaint.color = Color.WHITE
        textPaint.textSize = 50F

        val overlayText = getString(R.string.overlay_help)
        val textBounds = Rect()
        textPaint.getTextBounds(overlayText, 0, overlayText.length, textBounds)
        val textX = (surfaceWidth - textBounds.width()) / 2f
        val textY = rectBottom + textBounds.height() + 15f // put text below rect and 15f padding
        canvas.drawText(getString(R.string.overlay_help), textX, textY, textPaint)
        holder.unlockCanvasAndPost(canvas)
    }

    override fun onDestroy() {
        // Shut down our background executor
        cameraExecutor.shutdown()
        scopedExecutor.shutdown()

        super.onDestroy()
    }

    /** CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewfinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = viewfinder.display.rotation

        val preview = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        // Build the image analysis use case and instantiate our analyzer
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                /** TextAnalyzer will trigger viewModel.sourceText for the result */
                it.setAnalyzer(
                    cameraExecutor, TextAnalyzer(
                        this,
                        lifecycle,
                        viewModel.sourceText,
                        viewModel.imageCropPercentages
                    )
                )
            }

        viewModel.imageCropPercentages.observe(this,
            Observer { drawOverlay(overlay.holder, it.first, it.second) })

        // Select back camera since text detection does not work with front camera
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            preview.setSurfaceProvider(viewfinder.createSurfaceProvider())
        } catch (exc: IllegalStateException) {
            Log.e(TAG, "Use case binding failed. This must be running on main thread.", exc)
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by comparing absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = ln(max(width, height).toDouble() / min(width, height))
        if (abs(previewRatio - ln(RATIO_4_3_VALUE))
            <= abs(previewRatio - ln(RATIO_16_9_VALUE))
        ) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewfinder.post {
                    // Keep track of the display in which this view is attached
                    displayId = viewfinder.display.displayId

                    // Set up the camera and its use cases
                    setUpCamera()
                }
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            this, it
        ) == PackageManager.PERMISSION_GRANTED
    }

}