package nethical.locklock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import nethical.locklock.screens.LockScreen
import nethical.locklock.services.AppLockerInfo
import nethical.locklock.services.INTENT_ACTION_APP_UNLOCKED
import nethical.locklock.ui.theme.LockLockTheme
import nethical.locklock.utils.IntruderCameraCapture


class AppLockActivity : ComponentActivity() {

    // Only true once the correct passcode has actually been verified (or a pin
    // change was completed). Everything below relies on this flag to decide
    // whether it's safe to let this screen disappear.
    private var isUnlocked = false
    private var intruderCapture: IntruderCameraCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isChangePin = intent.getBooleanExtra("is_change_pin",false)
        val lockedPackage = intent.getStringExtra("locked_package") ?: "Unknown App"

        setContent {
            LockLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Hidden front-camera session: only for the real "someone is
                    // trying to open a locked app" screen, never for the owner's
                    // own "change my passcode" flow, and only if they've turned
                    // the feature on and granted camera permission in Settings.
                    if (!isChangePin) {
                        IntruderCaptureHost(onReady = { capture -> intruderCapture = capture })
                    }

                    LockScreen(lockedPackage,isChangePin, onUnlocked = {
                        // Unlock logic
                        isUnlocked = true
                        val intent = Intent(INTENT_ACTION_APP_UNLOCKED).apply {
                            putExtra("packageName", lockedPackage)
                        }
                        sendBroadcast(intent)
                        Toast.makeText(this, "$lockedPackage Unlocked", Toast.LENGTH_SHORT).show()
                        finishAffinity() // Closes this activity and any parent activities in the task
                    },
                        onPinChanged = {
                            isUnlocked = true
                            finish()
                            Toast.makeText(this, "Passcode Change Success", Toast.LENGTH_SHORT).show()
                        },
                        onWrongPasscode = {
                            intruderCapture?.captureIntruderPhoto()
                        })
                }
            }
        }
    }

    // Called when the user deliberately leaves (Home button, Recents/app switcher,
    // swipe gesture) — fires BEFORE the screen actually disappears, so it's the
    // right place to shield the locked app.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isUnlocked) {
            moveTaskToBack(true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isUnlocked) {
            // SECURITY FIX: previously this called finish() unconditionally.
            // That meant leaving this screen for ANY reason (Home, Recents,
            // notification shade, an incoming call, the screen turning off...)
            // closed the lock screen WITHOUT the passcode ever being checked,
            // exposing the locked app underneath the moment you came back to it.
            //
            // Now: if the passcode hasn't been verified, we just push the whole
            // task to the background instead of destroying this Activity. The
            // lock screen stays alive (still showing "Enter Passcode", nothing
            // unlocked) and will be exactly what the user sees again if this
            // task ever resurfaces.
            moveTaskToBack(true)
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        intruderCapture?.unbind()
        super.onDestroy()
    }
}

/**
 * Binds a headless (invisible) front-camera session for the lifetime of this
 * composable, and hands the ready [IntruderCameraCapture] back via [onReady]
 * once CameraX has finished initializing. Renders a real — just visually
 * hidden — 1x1dp [PreviewView], since CameraX needs an actual surface to
 * bind to on most devices; nothing is ever shown to whoever is looking at
 * the screen.
 */
@Composable
private fun IntruderCaptureHost(onReady: (IntruderCameraCapture) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (hasCameraPermission && AppLockerInfo.isIntruderCaptureOn) {
        val capture = remember { IntruderCameraCapture(context) }
        AndroidView(
            modifier = Modifier.size(1.dp).alpha(0f),
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    capture.bind(lifecycleOwner, previewView)
                    onReady(capture)
                }
            }
        )
    }
}
