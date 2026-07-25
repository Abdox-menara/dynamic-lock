package com.example.dynamiclock.security

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.dynamiclock.security.Crypto
import java.io.File
import java.util.concurrent.Executors

/**
 * v5: Captures a front-camera photo for intruder selfie mode.
 * Runs completely silently — no UI, no preview shown to the user.
 * Falls back to the rear camera if front is unavailable.
 */
class IntruderCaptureActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val tag = "IntruderCapture"

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check camera permission
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
        // Auto-finish after 5 seconds if no photo captured
        handler.postDelayed({ if (!isFinishing) finish() }, 5000)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCapture()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCapture() {
        val provider = cameraProvider ?: return
        // Try front camera first, fall back to back camera
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        val preview = Preview.Builder().build()
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
        } catch (e: Exception) {
            Log.e(tag, "Camera bind failed", e)
            finish()
            return
        }

        // Take photo after a short delay (let camera warm up)
        handler.postDelayed({ capturePhoto() }, 800)
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: run { finish(); return }
        val photoFile = File(cacheDir, "intruder_temp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(outputOptions, executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Read the photo, encrypt it, and save to private storage
                    val bytes = photoFile.readBytes()
                    // Clean up temp file
                    photoFile.delete()
                    if (bytes.isNotEmpty()) {
                        saveIntruderPhoto(bytes)
                    }
                    runOnUiThread { finish() }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo capture failed", exception)
                    runOnUiThread { finish() }
                }
            })
    }

    private fun saveIntruderPhoto(bytes: ByteArray) {
        val dir = File(filesDir, "intruder_photos").apply { if (!exists()) mkdirs() }
        val file = File(dir, "intruder_${System.currentTimeMillis()}.enc")
        Crypto.writeBytes(this, file, bytes)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        fun capture(context: Context) {
            val intent = Intent(context, IntruderCaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }

        fun listPhotos(context: Context): List<File> {
            val dir = File(context.filesDir, "intruder_photos")
            if (!dir.exists()) return emptyList()
            return dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        }

        fun loadPhoto(context: Context, file: File): Bitmap? {
            val bytes = Crypto.readBytes(context, file) ?: return null
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }

        fun deletePhoto(context: Context, file: File) {
            file.delete()
        }
    }
}
