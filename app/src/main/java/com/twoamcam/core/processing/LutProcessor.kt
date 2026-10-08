package com.twoamcam.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Xử lý màu sắc, độ tương phản, dìm vùng tối và tone film ngả ấm/xanh lá.
 * Hỗ trợ ma trận màu chuyên dụng cho direct-flash và kiến trúc nạp LUT texture.
 */
class LutProcessor {

    /**
     * Áp dụng biến đổi màu film disposable flash lên Bitmap mục tiêu
     */
    fun applyFilmColor(
        sourceBitmap: Bitmap,
        config: FilmConfiguration
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        // Tạo bitmap đầu ra cùng kích thước và định dạng ARGB_8888
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // 1. Ma trận tương phản (Contrast) và độ sáng (Brightness)
        val c = config.contrast
        val bOffset = (config.brightness - 1.0f) * 255f
        val contrastTranslate = (1.0f - c) * 128f + bOffset

        // 2. Kết hợp với tỷ lệ kênh màu (Warm & Green disposable tint)
        val rScale = c * config.warmTint
        val gScale = c * config.greenTint
        val bScale = c * (2.0f - config.warmTint)

        // Bias bù trừ crushed blacks
        val shadowBias = contrastTranslate + config.shadowCrush

        val filmMatrixArray = floatArrayOf(
            rScale, 0f, 0f, 0f, shadowBias,
            0f, gScale, 0f, 0f, shadowBias,
            0f, 0f, bScale, 0f, shadowBias,
            0f, 0f, 0f, 1f, 0f
        )

        val filmMatrix = ColorMatrix(filmMatrixArray)

        // 3. Điều chỉnh độ bão hòa nhẹ (Saturation)
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(config.saturation)
        filmMatrix.postConcat(satMatrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(filmMatrix)
        }

        canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)
        return outputBitmap
    }
}
