package nethical.locklock.utils

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val INTRUDER_DIR_NAME = "intruder_photos"

fun intruderPhotosDir(context: Context): File {
    val dir = File(context.filesDir, INTRUDER_DIR_NAME)
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun listIntruderPhotos(context: Context): List<File> =
    intruderPhotosDir(context)
        .listFiles { f -> f.isFile && f.extension.equals("jpg", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

fun deleteIntruderPhoto(file: File): Boolean = file.delete()

fun deleteAllIntruderPhotos(context: Context) {
    intruderPhotosDir(context).listFiles()?.forEach { it.delete() }
}

class IntruderCameraCapture(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    capture
                )

                cameraProvider = provider
                imageCapture = capture
            } catch (e: Exception) {
                imageCapture = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun captureIntruderPhoto() {
        val capture = imageCapture ?: return
        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(intruderPhotosDir(context), "$fileName.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                }

                override fun onError(exception: ImageCaptureException) {
                }
            }
        )
    }

    fun unbind() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
        }
        cameraProvider = null
        imageCapture = null
    }
}
