package com.twoamcam.core.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed class CameraEngineState {
    object Uninitialized : CameraEngineState()
    object Initializing : CameraEngineState()
    object Ready : CameraEngineState()
    data class Error(val message: String, val cause: Throwable? = null) : CameraEngineState()
}

/**
 * Điều phối toàn bộ hoạt động của CameraX, kết nối Preview, ImageCapture,
 * FocusZoneManager, FlashSafetyManager và CaptureManager.
 */
class CameraEngine(
    private val context: Context,
    val focusZoneManager: FocusZoneManager = FocusZoneManager(),
    val flashSafetyManager: FlashSafetyManager = FlashSafetyManager()
) {
    companion object {
        private const val TAG = "CameraEngine"
    }

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val captureManager: CaptureManager = CaptureManager(cameraExecutor)

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null

    var state: CameraEngineState = CameraEngineState.Uninitialized
        private set

    /**
     * Khởi tạo ProcessCameraProvider bất đồng bộ
     */
    fun initialize(onReady: () -> Unit, onError: (Throwable) -> Unit) {
        state = CameraEngineState.Initializing
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                Log.d(TAG, "ProcessCameraProvider đã sẵn sàng")
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Không thể khởi tạo ProcessCameraProvider", e)
                state = CameraEngineState.Error("Lỗi khởi tạo Camera Provider", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Gắn kết Camera UseCases với LifecycleOwner và Viewfinder Surface
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onBound: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val provider = cameraProvider
        if (provider == null) {
            onError(IllegalStateException("CameraProvider chưa được khởi tạo"))
            return
        }

        try {
            // Hủy các use-case trước đó để tránh xung đột
            provider.unbindAll()

            // 1. Cấu hình Preview
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(surfaceProvider)
                }

            // 2. Cấu hình ImageCapture tối ưu chất lượng và đồng bộ đèn Flash phần cứng
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(ImageCapture.FLASH_MODE_ON) // Mặc định ON theo concept direct flash
                .build()

            // 3. Chọn camera sau
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // 4. Bind vào lifecycle
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            camera = boundCamera

            // 5. Kiểm tra thông số ống kính và flash của camera vừa bind
            focusZoneManager.inspectCameraCapabilities(boundCamera.cameraInfo)
            flashSafetyManager.checkFlashAvailability(boundCamera.cameraInfo)

            // 6. Áp dụng flash an toàn
            flashSafetyManager.configureFlashForCapture(imageCapture)

            // 7. Áp dụng khoảng focus mặc định (Portrait)
            focusZoneManager.applyFocusMode(focusZoneManager.currentMode, boundCamera.cameraControl)

            state = CameraEngineState.Ready
            Log.i(TAG, "Camera đã bind thành công vào Lifecycle")
            onBound()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi bind CameraX use cases", e)
            state = CameraEngineState.Error("Không thể kích hoạt Camera", e)
            onError(e)
        }
    }

    /**
     * Chuyển đổi giữa chế độ Portrait và Group
     */
    fun setFocusMode(mode: FocusMode): FocusResult {
        val cam = camera ?: return FocusResult.Error(IllegalStateException("Camera chưa sẵn sàng"))
        return focusZoneManager.applyFocusMode(mode, cam.cameraControl)
    }

    /**
     * Thực hiện chụp ảnh:
     * 1. Kiểm tra camera ready
     * 2. Kiểm tra flash capability
     * 3. Áp dụng focus mode hiện tại
     * 4. Capture JPEG
     */
    fun takePhoto(onResult: (CaptureResult) -> Unit) {
        val cam = camera
        val capture = imageCapture

        if (cam == null || capture == null) {
            onResult(CaptureResult.Error("Camera chưa sẵn sàng để chụp"))
            return
        }

        // 1 & 2: Cấu hình flash trước khi chụp
        flashSafetyManager.configureFlashForCapture(capture)

        // 3: Củng cố lại focus mode để đảm bảo không bị trôi tiêu cự
        focusZoneManager.applyFocusMode(focusZoneManager.currentMode, cam.cameraControl)

        // 4: Chụp JPEG
        captureManager.capturePhoto(capture, onResult)
    }

    /**
     * Dọn dẹp tài nguyên
     */
    fun release() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            state = CameraEngineState.Uninitialized
            Log.d(TAG, "Đã giải phóng CameraEngine")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi giải phóng CameraEngine", e)
        }
    }
}
