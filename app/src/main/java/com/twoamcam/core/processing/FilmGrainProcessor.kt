package com.twoamcam.core.processing

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import java.util.Random

/**
 * Xử lý hạt Film Grain (Noise) hữu cơ trên Android.
 * Sử dụng kỹ thuật tiled procedural noise tile (256x256) với random seed và ma trận dịch chuyển
 * ngẫu nhiên cho từng ảnh: Vừa đảm bảo hạt mịn tự nhiên không lặp, vừa tiết kiệm RAM tuyệt đối (< 300KB).
 */
class FilmGrainProcessor {

    private val noiseTileSize = 256

    /**
     * Áp dụng lớp hạt film hữu cơ lên Bitmap mục tiêu
     */
    fun applyFilmGrain(
        targetBitmap: Bitmap,
        intensity: Float = 0.07f,
        seed: Long = System.currentTimeMillis()
    ) {
        if (intensity <= 0.005f) return

        val random = Random(seed)

        // 1. Tạo một noise tile nhỏ 256x256 trong bộ nhớ tạm
        val noisePixels = IntArray(noiseTileSize * noiseTileSize)
        val alphaBase = (intensity.coerceIn(0f, 1f) * 255).toInt().coerceIn(5, 50)

        for (i in noisePixels.indices) {
            // Giá trị ngẫu nhiên từ tối đến sáng nhẹ (grayscale noise)
            val v = random.nextInt(256)
            noisePixels[i] = Color.argb(alphaBase, v, v, v)
        }

        val noiseTileBitmap = Bitmap.createBitmap(
            noisePixels,
            noiseTileSize,
            noiseTileSize,
            Bitmap.Config.ARGB_8888
        )

        try {
            // 2. Sử dụng BitmapShader với TileMode.REPEAT để phủ lên toàn bộ ảnh kích thước lớn
            val shader = BitmapShader(noiseTileBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

            // Dịch chuyển ma trận (random offset) để mỗi ảnh có vị trí hạt hoàn toàn khác biệt
            val matrix = Matrix()
            matrix.postTranslate(random.nextInt(1000).toFloat(), random.nextInt(1000).toFloat())
            shader.setLocalMatrix(matrix)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.shader = shader
            }

            val canvas = Canvas(targetBitmap)
            canvas.drawRect(0f, 0f, targetBitmap.width.toFloat(), targetBitmap.height.toFloat(), paint)
        } finally {
            // Giải phóng ngay noise tile bitmap sau khi vẽ
            noiseTileBitmap.recycle()
        }
    }
}
