fun unbind() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            // Never let releasing the camera crash the unlock flow itself.
        }
        cameraProvider = null
        imageCapture = null
}
