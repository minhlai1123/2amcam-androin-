package com.twoamcam.core.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

data class ProcessedFilmResult(
    val jpegBytes: ByteArray,
    val executionTimeMs: Long,
    val width: Int,
    val height: Int,
    val isFallback: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProcessedFilmResult
        return jpegBytes.contentEquals(other.jpegBytes)
    }

    override fun hashCode(): Int = jpegBytes.contentHashCode()
}

/**
 * Điều phối toàn bộ quy trình Offline Image Processing trên Android.
 * Pipeline: Decode -> Fix Orientation -> Color/LUT -> Vignette -> Grain -> Encode JPEG.
 * Đảm bảo:
 * - Chạy trên background coroutine (Dispatchers.Default)
 * - Serial execution qua Mutex để bảo vệ RAM khỏi shutter spam
 * - Recycle bitmap lập tức để chống OOM
 * - Graceful fallback về ảnh gốc nếu gặp lỗi bất ngờ
 */
class ImageProcessingPipeline(
    var configuration: FilmConfiguration = FilmConfiguration.DEFAULT,
    private val lutProcessor: LutProcessor = LutProcessor(),
    private val vignetteProcessor: VignetteProcessor = VignetteProcessor(),
    private val grainProcessor: FilmGrainProcessor = FilmGrainProcessor()
) {
    companion object {
        private const val TAG = "ImageProcessingPipeline"
        
        private fun logE(msg: String, tr: Throwable? = null) {
            try { android.util.Log.e(TAG, msg, tr) } catch (_: Throwable) { System.err.println("[$TAG] $msg") }
        }
        private fun logI(msg: String) {
            try { android.util.Log.i(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
        }
        private fun logW(msg: String, tr: Throwable? = null) {
            try { android.util.Log.w(TAG, msg, tr) } catch (_: Throwable) { println("[$TAG] $msg") }
        }
    }

    // Mutex ngăn chặn việc nhiều luồng full-res chạy đồng thời gây cạn kiệt RAM
    private val processingMutex = Mutex()

    /**
     * Xử lý mảng byte JPEG thô thành ảnh JPEG chất film 2AM Flash
     */
    suspend fun processImage(rawBytes: ByteArray): ProcessedFilmResult = withContext(Dispatchers.Default) {
        if (rawBytes.isEmpty()) {
            logE("Dữ liệu ảnh thô rỗng")
            return@withContext ProcessedFilmResult(rawBytes, 0L, 0, 0, isFallback = true)
        }

        processingMutex.withLock {
            val startTime = System.currentTimeMillis()
            var decodedBitmap: Bitmap? = null
            var orientedBitmap: Bitmap? = null
            var processedBitmap: Bitmap? = null

            try {
                // 1. Đọc Exif Orientation từ luồng byte
                val orientation = readExifOrientation(rawBytes)

                // 2. Decode JPEG thành Bitmap an toàn RAM (giới hạn tối đa 2560px để chống OOM trên máy yếu)
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)
                var sampleSize = 1
                if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                    while ((boundsOptions.outWidth / sampleSize) > 2560 || (boundsOptions.outHeight / sampleSize) > 2560) {
                        sampleSize *= 2
                    }
                }

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                decodedBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                    ?: throw IllegalStateException("Không thể decode JPEG stream")

                // 3. Xoay ảnh theo Exif Orientation nếu cảm biến chụp góc lệch
                orientedBitmap = fixOrientation(decodedBitmap, orientation)
                if (orientedBitmap !== decodedBitmap) {
                    decodedBitmap.recycle()
                    decodedBitmap = null
                }

                val width = orientedBitmap.width
                val height = orientedBitmap.height

                // 4. Tone & Film Color Transformation (Crushed blacks, flash pop, warm/green tint)
                processedBitmap = lutProcessor.applyFilmColor(orientedBitmap, configuration)
                orientedBitmap.recycle()
                orientedBitmap = null

                // 5. Optical Vignette (Tối nhẹ 4 góc gom sáng vào trung tâm)
                vignetteProcessor.applyVignette(processedBitmap, configuration)

                // 6. Organic Film Grain (Hạt noise mịn ngẫu nhiên theo seed)
                val seed = System.currentTimeMillis()
                grainProcessor.applyFilmGrain(processedBitmap, configuration.grainIntensity, seed)

                // 7. Nén thành JPEG chất lượng cao
                val outputStream = ByteArrayOutputStream()
                val compressed = processedBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    configuration.jpegQuality,
                    outputStream
                )
                if (!compressed) {
                    throw IllegalStateException("Lỗi khi nén ảnh sang định dạng JPEG")
                }

                val finalBytes = outputStream.toByteArray()
                val duration = System.currentTimeMillis() - startTime

                logI("Hoàn tất xử lý film trong ${duration}ms (${width}x${height}, ${finalBytes.size / 1024} KB)")

                ProcessedFilmResult(
                    jpegBytes = finalBytes,
                    executionTimeMs = duration,
                    width = width,
                    height = height,
                    isFallback = false
                )
            } catch (t: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                logE("Lỗi trong Image Pipeline, kích hoạt fallback an toàn về ảnh gốc: ${t.message}", t)

                ProcessedFilmResult(
                    jpegBytes = rawBytes,
                    executionTimeMs = duration,
                    width = 0,
                    height = 0,
                    isFallback = true
                )
            } finally {
                // Dọn dẹp triệt để các Bitmap trong RAM
                decodedBitmap?.recycle()
                orientedBitmap?.recycle()
                processedBitmap?.recycle()
            }
        }
    }

    private fun readExifOrientation(bytes: ByteArray): Int {
        return try {
            val inputStream = ByteArrayInputStream(bytes)
            val exif = ExifInterface(inputStream)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            logW("Không thể đọc Exif orientation, sử dụng mặc định", e)
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun fixOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
