package com.twoamcam.core.storage

/**
 * Model đại diện cho cuộn phim theo ngày (Daily Roll) chứa danh sách các ảnh chụp trong ngày.
 */
data class DailyRoll(
    val dateKey: String,
    val photos: List<LocalPhoto>
) {
    val photoCount: Int get() = photos.size
}
