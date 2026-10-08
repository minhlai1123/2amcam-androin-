package com.twoamcam.core.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Quản lý lưu trữ ảnh cục bộ theo ngày (Daily Rolls) thuần File System trên Android.
 * Không sử dụng database (Zero Database), bảo đảm atomic save, deterministic sorting,
 * và an toàn thread/coroutine.
 */
class DailyRollRepository(
    private val baseDirectory: File
) {
    constructor(context: Context) : this(File(context.filesDir, "DailyRolls"))

    companion object {
        private const val TAG = "DailyRollRepository"

        private fun logI(msg: String) {
            try { android.util.Log.i(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
        }
        private fun logE(msg: String, tr: Throwable? = null) {
            try { android.util.Log.e(TAG, msg, tr) } catch (_: Throwable) { System.err.println("[$TAG] $msg") }
        }
    }

    private val storageMutex = Mutex()

    init {
        if (!baseDirectory.exists()) {
            baseDirectory.mkdirs()
        }
    }

    private fun getDayDateFormatter(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    private fun getFileTimestampFormatter(): SimpleDateFormat {
        return SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    /**
     * Lưu dữ liệu ảnh JPEG thành phẩm vào Daily Roll tương ứng với ngày chụp
     */
    suspend fun savePhoto(
        jpegBytes: ByteArray,
        captureTimestamp: Long = System.currentTimeMillis()
    ): Result<LocalPhoto> = withContext(Dispatchers.IO) {
        if (jpegBytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Dữ liệu ảnh JPEG rỗng"))
        }

        storageMutex.withLock {
            try {
                val date = Date(captureTimestamp)
                val dateKey = getDayDateFormatter().format(date)
                val dayDir = File(baseDirectory, dateKey)

                if (!dayDir.exists() && !dayDir.mkdirs()) {
                    throw IOException("Không thể tạo thư mục ngày: ${dayDir.absolutePath}")
                }

                // Tạo tên file duy nhất chống collision
                val baseName = "IMG_${getFileTimestampFormatter().format(date)}"
                var targetFile = File(dayDir, "$baseName.jpg")
                var counter = 1
                while (targetFile.exists()) {
                    targetFile = File(dayDir, "${baseName}_$counter.jpg")
                    counter++
                }

                // Atomic Write: Ghi vào file tạm .tmp rồi rename sang .jpg
                val tempFile = File(dayDir, ".${System.nanoTime()}.tmp")
                try {
                    FileOutputStream(tempFile).use { fos ->
                        fos.write(jpegBytes)
                        fos.flush()
                        try {
                            fos.fd.sync()
                        } catch (_: Exception) {
                            // Bỏ qua nếu filesystem không hỗ trợ fsync
                        }
                    }

                    if (targetFile.exists()) {
                        targetFile.delete()
                    }

                    val renamed = tempFile.renameTo(targetFile)
                    if (!renamed) {
                        // Fallback copy nếu renameTo thất bại trên một số filesystem
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    if (tempFile.exists()) tempFile.delete()
                    throw e
                }

                logI("Đã lưu thành công ảnh vào Daily Roll: ${targetFile.absolutePath} (${targetFile.length() / 1024} KB)")

                val localPhoto = LocalPhoto(
                    id = targetFile.name,
                    fileName = targetFile.name,
                    filePath = targetFile.absolutePath,
                    createdAt = captureTimestamp,
                    dateKey = dateKey,
                    fileSize = targetFile.length()
                )

                Result.success(localPhoto)
            } catch (t: Throwable) {
                logE("Lỗi khi lưu ảnh vào DailyRoll: ${t.message}", t)
                Result.failure(t)
            }
        }
    }

    /**
     * Lấy toàn bộ danh sách Daily Rolls sắp xếp theo ngày mới nhất trước
     */
    suspend fun getDailyRolls(): List<DailyRoll> = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            if (!baseDirectory.exists()) return@withContext emptyList()

            val dayDirs = baseDirectory.listFiles { file -> file.isDirectory } ?: return@withContext emptyList()

            val rolls = mutableListOf<DailyRoll>()

            for (dir in dayDirs) {
                val dateKey = dir.name
                val photos = getPhotosInDirectory(dir, dateKey)
                if (photos.isNotEmpty()) {
                    rolls.add(DailyRoll(dateKey = dateKey, photos = photos))
                }
            }

            // Sắp xếp ngày mới nhất lên đầu (Descending)
            rolls.sortedByDescending { it.dateKey }
        }
    }

    /**
     * Lấy danh sách ảnh trong một ngày cụ thể
     */
    suspend fun getPhotos(dateKey: String): List<LocalPhoto> = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            val dayDir = File(baseDirectory, dateKey)
            if (!dayDir.exists()) return@withContext emptyList()
            getPhotosInDirectory(dayDir, dateKey)
        }
    }

    private fun getPhotosInDirectory(dayDir: File, dateKey: String): List<LocalPhoto> {
        val files = dayDir.listFiles { file ->
            file.isFile && file.extension.equals("jpg", ignoreCase = true) && !file.name.startsWith(".")
        } ?: return emptyList()

        val photos = mutableListOf<LocalPhoto>()

        for (f in files) {
            val size = f.length()
            if (size <= 0) continue // Bỏ qua file rỗng / corrupted

            photos.add(
                LocalPhoto(
                    id = f.name,
                    fileName = f.name,
                    filePath = f.absolutePath,
                    createdAt = f.lastModified(),
                    dateKey = dateKey,
                    fileSize = size
                )
            )
        }

        // Sắp xếp ảnh mới nhất trước trong ngày (Descending)
        return photos.sortedByDescending { it.createdAt }
    }

    /**
     * Xóa một bức ảnh và dọn dẹp thư mục ngày nếu rỗng
     */
    suspend fun deletePhoto(photo: LocalPhoto): Boolean = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            try {
                val targetFile = File(photo.filePath)
                val deleted = if (targetFile.exists()) targetFile.delete() else true

                val parentDir = targetFile.parentFile
                if (parentDir != null && parentDir.exists()) {
                    val remaining = parentDir.list()
                    if (remaining == null || remaining.isEmpty()) {
                        parentDir.delete()
                    }
                }

                deleted
            } catch (t: Throwable) {
                logE("Lỗi khi xóa ảnh: ${t.message}", t)
                false
            }
        }
    }
}
