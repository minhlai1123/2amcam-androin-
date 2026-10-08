package com.twoamcam.core.processing

/**
 * Tập hợp các tham số hiệu chỉnh (calibration constants) cho phong cách ảnh Disposable Flash-Film trên Android.
 * Dễ dàng tinh chỉnh tone màu, tương phản, vignette và grain mà không cần sửa đổi camera engine.
 */
data class FilmConfiguration(
    // Tương phản cao phong cách flash đêm (1.0 là gốc, 1.20 - 1.30 cho flash pop)
    val contrast: Float = 1.24f,
    // Độ sáng tổng thể / Flash highlight boost
    val brightness: Float = 1.04f,
    // Độ bão hòa màu hơi muted nhẹ kiểu film analog
    val saturation: Float = 0.92f,
    // Dìm vùng tối (crushed blacks offset)
    val shadowCrush: Float = -12f,
    // Tone ấm nhẹ cho da flash
    val warmTint: Float = 1.06f,
    // Tone ngả xanh lá nhẹ đặc trưng của film dùng 1 lần chụp đêm
    val greenTint: Float = 1.03f,
    // Cường độ bo tối 4 góc (Vignette)
    val vignetteIntensity: Float = 0.58f,
    // Bán kính phủ sóng của vignette
    val vignetteRadius: Float = 0.85f,
    // Cường độ hạt noise film
    val grainIntensity: Float = 0.07f,
    // Chất lượng nén JPEG xuất xưởng (0 - 100)
    val jpegQuality: Int = 92
) {
    companion object {
        val DEFAULT = FilmConfiguration()
    }
}
