package com.twoamcam.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo

enum class FocusMode(val label: String) {
    PORTRAIT("PORTRAIT"),
    GROUP("GROUP")
}

data class FocusCalibration(
    // Portrait: target ~1.0m (diopter D = 1 / 1.0 = 1.0)
    val portraitDistanceMeters: Float = 1.0f,
    // Group: target ~2.5m - 3.0m (diopter D = 1 / 2.5 = 0.4)
    val groupDistanceMeters: Float = 2.5f
)

sealed class FocusResult {
    data class Success(val mode: FocusMode, val diopterApplied: Float) : FocusResult()
    data class FallbackAutofocus(val reason: String) : FocusResult()
    data class Error(val throwable: Throwable) : FocusResult()
}

/**
 * Quản lý khóa tiêu cự (Zone Focus) cho Portrait và Group modes trên Android.
 * Sử dụng Camera2 Interop để điều khiển LENS_FOCUS_DISTANCE theo Diopter (1/meters).
 * Tự động kiểm tra LENS_INFO_MINIMUM_FOCUS_DISTANCE và fallback an toàn nếu không hỗ trợ.
 */
class FocusZoneManager(
    private val calibration: FocusCalibration = FocusCalibration()
) {
    companion object {
        private const val TAG = "FocusZoneManager"
    }

    var currentMode: FocusMode = FocusMode.PORTRAIT
        private set

    var isManualFocusSupported: Boolean = false
        private set

    private var minimumFocusDistance: Float = 0f

    /**
     * Khởi tạo và đọc đặc tính ống kính từ CameraCharacteristics
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun inspectCameraCapabilities(cameraInfo: CameraInfo) {
        val camera2Info = Camera2CameraInfo.from(cameraInfo)
        minimumFocusDistance = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f

        // Nếu minimumFocusDistance > 0 thì phần cứng hỗ trợ manual focus
        isManualFocusSupported = minimumFocusDistance > 0f
        Log.d(TAG, "MinimumFocusDistance: $minimumFocusDistance, isManualSupported: $isManualFocusSupported")
    }

    /**
     * Áp dụng khoảng lấy nét cố định tương ứng với mode được chọn
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun applyFocusMode(
        mode: FocusMode,
        cameraControl: CameraControl
    ): FocusResult {
        this.currentMode = mode

        if (!isManualFocusSupported) {
            Log.w(TAG, "Manual focus không được hỗ trợ bởi Camera HAL, fallback về Continuous AF")
            try {
                val camera2Control = Camera2CameraControl.from(cameraControl)
                val options = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    .build()
                camera2Control.addCaptureRequestOptions(options)
                return FocusResult.FallbackAutofocus("Thiết bị không hỗ trợ manual focus diopter, sử dụng AF")
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi thiết lập fallback AF", e)
                return FocusResult.Error(e)
            }
        }

        return try {
            val camera2Control = Camera2CameraControl.from(cameraControl)

            // Diopter D = 1 / distanceInMeters
            val targetDiopter = when (mode) {
                FocusMode.PORTRAIT -> 1.0f / calibration.portraitDistanceMeters
                FocusMode.GROUP -> 1.0f / calibration.groupDistanceMeters
            }

            // Giới hạn trong khoảng [0f, minimumFocusDistance] (0 là vô cực)
            val clampedDiopter = targetDiopter.coerceIn(0f, minimumFocusDistance)

            // Tắt Continuous AF và set khoảng cách lấy nét cố định
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
                )
                .setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    clampedDiopter
                )
                .build()

            camera2Control.addCaptureRequestOptions(options)
            Log.i(TAG, "Đã khóa tiêu cự mode: $mode với diopter: $clampedDiopter (max: $minimumFocusDistance)")
            FocusResult.Success(mode, clampedDiopter)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gán LENS_FOCUS_DISTANCE", e)
            FocusResult.Error(e)
        }
    }
}
