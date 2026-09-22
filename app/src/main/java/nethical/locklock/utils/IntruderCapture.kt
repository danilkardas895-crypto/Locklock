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

/**
 * Where intruder photos live.
 *
 * SECURITY/PRIVACY: this is [Context.getFilesDir], the app's private internal
 * storage — deliberately NOT MediaStore and NOT the public Pictures folder.
 * That means:
 *  - the photos never show up in the Gallery/Photos app, or in any other app
 *    that browses shared media, so whoever is trying to get in has no normal
 *    way to even notice they were photographed, let alone delete the evidence,
 *  - on modern Android (scoped storage) this folder is sandboxed per-app, so
 *    no other app could read it even if it tried.
 * Only this app can read it back (see [listIntruderPhotos], used by the
 * in-app "Intruder Photos" viewer in Settings).
 */
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

/**
 * A headless (no visible preview) CameraX session on the front camera, used
 * to silently snap a photo the instant a wrong passcode is entered.
 *
 * Usage: bind once while the lock screen is up (with a real, if invisible,
 * [PreviewView] surface — CameraX needs one to initialize properly on most
 * devices), then call [captureIntruderPhoto] on every failed attempt, and
 * [unbind] when the screen goes away.
 */
class IntruderCameraCapture(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
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
                // No front camera, camera already in use by something else,
                // device/OEM quirk, etc. A missed intruder photo must never
                // crash or block the lock screen itself — fail silently.
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
                    // Saved silently into the app's private storage.
                }

                override fun onError(exception: ImageCaptureException) {
                    // Ignore — a camera hiccup should never surface on the lock screen.
                }
            }
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
    }
}
