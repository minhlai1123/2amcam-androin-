package com.twoamcam.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.hypot

/**
 * Xử lý hiệu ứng Vignette quang học: Tối nhẹ 4 góc ảnh để gom ánh sáng vào trung tâm.
 * Tự động thích ứng với mọi kích thước và tỉ lệ khung hình (Aspect Ratio).
 */
class VignetteProcessor {

    /**
     * Vẽ lớp phủ Vignette lên Bitmap
     */
    fun applyVignette(
        targetBitmap: Bitmap,
        config: FilmConfiguration
    ) {
        val width = targetBitmap.width.toFloat()
        val height = targetBitmap.height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f

        // Bán kính tính theo đường chéo ảnh kết hợp với hệ số radius trong config
        val diagonal = hypot(width, height) / 2f
        val radius = diagonal * config.vignetteRadius

        if (radius <= 0f || config.vignetteIntensity <= 0f) return

        // Độ tối ở các góc viền dựa trên vignetteIntensity
        val cornerAlpha = (config.vignetteIntensity.coerceIn(0f, 1f) * 255).toInt()
        val edgeColor = Color.argb(cornerAlpha, 0, 0, 0)
        val centerColor = Color.argb(0, 0, 0, 0)

        val colors = intArrayOf(centerColor, centerColor, edgeColor)
        val stops = floatArrayOf(0.0f, 0.45f, 1.0f)

        val radialGradient = RadialGradient(
            centerX,
            centerY,
            radius,
            colors,
            stops,
            Shader.TileMode.CLAMP
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = radialGradient
            // Sử dụng PorterDuff.Mode.SRC_OVER hoặc MULTIPLY để tạo độ sâu bóng tối tự nhiên
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        val canvas = Canvas(targetBitmap)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}
