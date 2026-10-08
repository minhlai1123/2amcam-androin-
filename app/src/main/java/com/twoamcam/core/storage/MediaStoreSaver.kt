package com.twoamcam.core.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Quản lý xuất ảnh từ Daily Roll cục bộ sang Thư viện ảnh hệ thống (MediaStore).
 * Hỗ trợ tối ưu cả Android 10+ (Scoped Storage) và Android <= 9 (Legacy MediaStore & MediaScanner).
 */
class MediaStoreSaver(
    private val context: Context
) {
    companion object {
        private const val TAG = "MediaStoreSaver"
        private const val ALBUM_DIRECTORY = "Pictures/2AM Cam"

        private fun logI(msg: String) {
            try { android.util.Log.i(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
        }
        private fun logE(msg: String, tr: Throwable? = null) {
            try { android.util.Log.e(TAG, msg, tr) } catch (_: Throwable) { System.err.println("[$TAG] $msg") }
        }
    }

    /**
     * Xuất một ảnh LocalPhoto sang Thư viện hệ thống
     */
    suspend fun exportPhoto(photo: LocalPhoto): Result<Uri> = withContext(Dispatchers.IO) {
        val sourceFile = File(photo.filePath)
        if (!sourceFile.exists()) {
            return@withContext Result.failure(IOException("File ảnh cục bộ không tồn tại: ${photo.filePath}"))
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Scoped Storage chuẩn mực
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, photo.fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.DATE_TAKEN, photo.createdAt)
                    put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_DIRECTORY)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val imageUri = resolver.insert(collectionUri, contentValues)
                    ?: throw IOException("Không thể tạo bản ghi MediaStore mới")

                try {
                    resolver.openOutputStream(imageUri).use { outputStream ->
                        if (outputStream == null) throw IOException("Không thể mở OutputStream cho $imageUri")
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        outputStream.flush()
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)

                    logI("Đã xuất ảnh thành công sang MediaStore (Scoped Storage): $imageUri")
                    Result.success(imageUri)
                } catch (t: Throwable) {
                    try { resolver.delete(imageUri, null, null) } catch (_: Exception) {}
                    throw t
                }
            } else {
                // Android <= 9 (API <= 28): Public Pictures directory + MediaScannerConnection
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val albumDir = File(picturesDir, "2AM Cam")
                if (!albumDir.exists()) {
                    albumDir.mkdirs()
                }

                val targetFile = File(albumDir, photo.fileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }

                // Kích hoạt MediaScanner để thư viện hệ thống nhận diện file ngay lập tức
                val scanDeferred = CompletableDeferred<Uri?>()
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("image/jpeg")
                ) { _, uri ->
                    scanDeferred.complete(uri)
                }

                val scannedUri = withTimeoutOrNull(3000L) {
                    scanDeferred.await()
                } ?: Uri.fromFile(targetFile)

                logI("Đã xuất ảnh thành công sang Thư viện (Legacy Storage): $scannedUri")
                Result.success(scannedUri)
            }
        } catch (t: Throwable) {
            logE("Lỗi khi xuất ảnh sang Thư viện: ${t.message}", t)
            Result.failure(t)
        }
    }
}
