package com.twoamcam.core.camera

import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

sealed class CaptureResult {
    data class Success(val jpegBytes: ByteArray, val width: Int, val height: Int) : CaptureResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            return jpegBytes.contentEquals(other.jpegBytes)
        }
        override fun hashCode(): Int = jpegBytes.contentHashCode()
    }
    data class Error(val message: String, val cause: Throwable? = null) : CaptureResult()
}

/**
 * Quản lý tiến trình chụp ảnh thô JPEG từ CameraX ImageCapture.
 * Đảm bảo:
 * - Chống spam nút chụp bằng AtomicBoolean
 * - Trích xuất ByteBuffer an toàn
 * - Gọi image.close() ngay lập tức để giải phóng buffer cho Camera HAL
 */
class CaptureManager(
    private val captureExecutor: Executor
) {
    companion object {
        private const val TAG = "CaptureManager"
    }

    private val isCapturing = AtomicBoolean(false)

    fun isBusy(): Boolean = isCapturing.get()

    /**
     * Thực hiện chụp ảnh JPEG thô
     */
    fun capturePhoto(
        imageCapture: ImageCapture?,
        onResult: (CaptureResult) -> Unit
    ) {
        if (imageCapture == null) {
            onResult(CaptureResult.Error("ImageCapture use-case chưa được khởi tạo"))
            return
        }

        if (!isCapturing.compareAndSet(false, true)) {
            Log.w(TAG, "Đang xử lý lượt chụp trước, bỏ qua spam shutter")
            onResult(CaptureResult.Error("Đang trong quá trình chụp ảnh, vui lòng chờ"))
            return
        }

        imageCapture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer: ByteBuffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        val width = image.width
                        val height = image.height

                        Log.i(TAG, "Chụp JPEG thành công: ${bytes.size} bytes (${width}x${height})")
                        onResult(CaptureResult.Success(jpegBytes = bytes, width = width, height = height))
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi khi trích xuất dữ liệu JPEG từ ImageProxy", e)
                        onResult(CaptureResult.Error("Lỗi đọc dữ liệu ảnh", e))
                    } finally {
                        // BẮT BUỘC đóng image để giải phóng buffer phần cứng
                        image.close()
                        isCapturing.set(false)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing.set(false)
                    Log.e(TAG, "Lỗi từ CameraX phần cứng: ${exception.imageCaptureError}", exception)
                    onResult(CaptureResult.Error("Chụp ảnh thất bại: ${exception.localizedMessage}", exception))
                }
            }
        )
    }
}
