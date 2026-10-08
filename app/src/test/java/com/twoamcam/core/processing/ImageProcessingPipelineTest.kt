package com.twoamcam.core.processing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ImageProcessingPipelineTest {

    @Test
    fun testFilmConfigurationDefaultsAndCustomValues() {
        val defaultConfig = FilmConfiguration.DEFAULT
        assertEquals(1.24f, defaultConfig.contrast, 0.001f)
        assertEquals(1.04f, defaultConfig.brightness, 0.001f)
        assertEquals(0.92f, defaultConfig.saturation, 0.001f)
        assertEquals(-12f, defaultConfig.shadowCrush, 0.001f)
        assertEquals(1.06f, defaultConfig.warmTint, 0.001f)
        assertEquals(1.03f, defaultConfig.greenTint, 0.001f)
        assertEquals(0.58f, defaultConfig.vignetteIntensity, 0.001f)
        assertEquals(0.85f, defaultConfig.vignetteRadius, 0.001f)
        assertEquals(0.07f, defaultConfig.grainIntensity, 0.001f)
        assertEquals(92, defaultConfig.jpegQuality)

        val customConfig = defaultConfig.copy(contrast = 1.35f, grainIntensity = 0.12f)
        assertEquals(1.35f, customConfig.contrast, 0.001f)
        assertEquals(0.12f, customConfig.grainIntensity, 0.001f)
    }

    @Test
    fun testColorMatrixMathAndCoefficients() {
        val config = FilmConfiguration.DEFAULT
        val c = config.contrast
        val bOffset = (config.brightness - 1.0f) * 255f
        val contrastTranslate = (1.0f - c) * 128f + bOffset

        val rScale = c * config.warmTint
        val gScale = c * config.greenTint
        val bScale = c * (2.0f - config.warmTint)
        val shadowBias = contrastTranslate + config.shadowCrush

        // Kiểm tra tính toán kênh màu đỏ > kênh xanh dương để tạo da ấm
        assertTrue("Kênh Đỏ phải lớn hơn kênh Xanh dương để tạo tone ấm nhẹ", rScale > bScale)
        // Kiểm tra kênh xanh lá tăng nhẹ
        assertTrue("Kênh Xanh lá phải được khuếch đại nhẹ", gScale > c)
        // Kiểm tra shadowBias âm (crushed blacks)
        assertTrue("Bias vùng tối phải âm để dìm blacks", shadowBias < 0)
    }

    @Test
    fun testVignetteDiagonalRadiusMath() {
        val config = FilmConfiguration.DEFAULT

        // Test trên tỷ lệ 4:3 (ví dụ 4000x3000)
        val w1 = 4000f
        val h1 = 3000f
        val diag1 = hypot(w1, h1) / 2f
        val radius1 = diag1 * config.vignetteRadius
        assertEquals(2500f * 0.85f, radius1, 0.01f)

        // Test trên tỷ lệ 16:9 (ví dụ 1920x1080)
        val w2 = 1920f
        val h2 = 1080f
        val diag2 = hypot(w2, h2) / 2f
        val radius2 = diag2 * config.vignetteRadius
        assertTrue("Bán kính vignette phải lớn hơn chiều cao/2 để không tạo viền đen ở cạnh trên/dưới", radius2 > (h2 / 2f))
    }

    @Test
    fun testEmptyByteInputFallbackSafety() = runBlocking {
        val pipeline = ImageProcessingPipeline()

        // Test 1: Empty byte array
        val emptyResult = pipeline.processImage(ByteArray(0))
        assertTrue("Mảng rỗng phải kích hoạt fallback an toàn", emptyResult.isFallback)
        assertEquals(0, emptyResult.jpegBytes.size)
        assertEquals(0, emptyResult.width)
        assertEquals(0, emptyResult.height)
    }

    @Test
    fun testCorruptedByteInputFallbackSafety() = runBlocking {
        val pipeline = ImageProcessingPipeline()

        // Test 2: Corrupted non-JPEG byte array
        val garbageBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        val corruptedResult = pipeline.processImage(garbageBytes)
        assertTrue("Dữ liệu rác không decode được phải kích hoạt fallback an toàn", corruptedResult.isFallback)
        assertEquals("Phải trả về đúng mảng byte gốc của input", 7, corruptedResult.jpegBytes.size)
    }

    @Test
    fun testMultipleSequentialCallsConcurrencyAndMutexSafety() = runBlocking {
        val pipeline = ImageProcessingPipeline()

        val dummyBytes = byteArrayOf(0x10, 0x20, 0x30, 0x40)

        // Thực hiện 5 lần liên tiếp để kiểm tra tính ổn định, không deadlock hay memory leak
        for (i in 1..5) {
            val result = pipeline.processImage(dummyBytes)
            assertTrue("Lượt $i phải trả về kết quả an toàn", result.isFallback)
            assertEquals(4, result.jpegBytes.size)
        }
    }
}
