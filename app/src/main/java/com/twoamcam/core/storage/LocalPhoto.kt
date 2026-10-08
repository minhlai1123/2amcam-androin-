package com.twoamcam.core.storage

import java.io.File

/**
 * Model đại diện cho một bức ảnh film đã được xử lý và lưu trữ cục bộ trong App-Specific Storage.
 */
data class LocalPhoto(
    val id: String,
    val fileName: String,
    val filePath: String,
    val createdAt: Long,
    val dateKey: String,
    val fileSize: Long
) {
    val file: File get() = File(filePath)
    val exists: Boolean get() = file.exists()
}
