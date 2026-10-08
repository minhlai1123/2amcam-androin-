package com.twoamcam.core.camera

import android.util.Log
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture

sealed class FlashStatus {
    object Available : FlashStatus()
    data class Unavailable(val reason: String) : FlashStatus()
}

/**
 * Quản lý kiểm tra và cấu hình đèn Flash phần cứng trên Android với CameraX.
 * Đảm bảo kích hoạt hardware flash an toàn, không crash khi thiết bị không hỗ trợ.
 */
class FlashSafetyManager {
    companion object {
        private const val TAG = "FlashSafetyManager"
    }

    var isFlashAvailable: Boolean = false
        private set

    /**
     * Kiểm tra khả năng hỗ trợ Flash từ CameraInfo
     */
    fun checkFlashAvailability(cameraInfo: CameraInfo?): FlashStatus {
        if (cameraInfo == null) {
            isFlashAvailable = false
            return FlashStatus.Unavailable("CameraInfo không khả dụng")
        }

        val hasFlash = cameraInfo.hasFlashUnit()
        isFlashAvailable = hasFlash

        return if (hasFlash) {
            Log.d(TAG, "Hardware flash sẵn sàng trên thiết bị")
            FlashStatus.Available
        } else {
            Log.w(TAG, "Thiết bị không trang bị đèn Flash phần cứng")
            FlashStatus.Unavailable("Thiết bị không có đèn Flash phần cứng")
        }
    }

    /**
     * Cấu hình flashMode cho ImageCapture trước khi chụp
     * @return true nếu flash được bật (.ON), false nếu phải tắt (.OFF)
     */
    fun configureFlashForCapture(imageCapture: ImageCapture?): Boolean {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture là null, không thể cấu hình flash")
            return false
        }

        return if (isFlashAvailable) {
            imageCapture.flashMode = ImageCapture.FLASH_MODE_ON
            Log.i(TAG, "Đã kích hoạt ImageCapture.FLASH_MODE_ON")
            true
        } else {
            imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
            Log.w(TAG, "Fallback: ImageCapture.FLASH_MODE_OFF do phần cứng không có flash")
            false
        }
    }
}
