package com.twoamcam.presentation.rolls

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twoamcam.core.storage.LocalPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tải ảnh an toàn cho GPU bằng cách downsample xuống kích thước tối đa 2048px,
 * tránh vượt ngưỡng GL_MAX_TEXTURE_SIZE (gây lỗi màn hình đen) và xử lý EXIF orientation.
 */
private fun loadOptimizedBitmap(filePath: String, maxDimension: Int = 1280): Bitmap? {
    return try {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return null

        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, boundsOpts)
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while ((origW / sampleSize) > maxDimension || (origH / sampleSize) > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var decoded = BitmapFactory.decodeFile(filePath, decodeOpts)
        if (decoded == null) {
            decodeOpts.inPreferredConfig = Bitmap.Config.RGB_565
            decoded = BitmapFactory.decodeFile(filePath, decodeOpts)
        }
        if (decoded == null) return null

        try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            var needsTransform = true
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> needsTransform = false
            }

            if (needsTransform) {
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated !== decoded) {
                    decoded.recycle()
                }
                rotated
            } else {
                decoded
            }
        } catch (_: Throwable) {
            decoded
        }
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun FullscreenImageViewer(
    photo: LocalPhoto,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var loadedBitmap by remember(photo.filePath) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(photo.filePath) { mutableStateOf(true) }

    LaunchedEffect(photo.filePath) {
        isLoading = true
        loadedBitmap = withContext(Dispatchers.IO) {
            loadOptimizedBitmap(photo.filePath, maxDimension = 1280)
        }
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Ảnh hiển thị toàn màn hình chính giữa
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap!!.asImageBitmap(),
                contentDescription = "Ảnh toàn màn hình ngày ${photo.dateKey}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isLoading) {
            Text(
                text = "Đang tải ảnh...",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Text(
                text = "Không thể tải ảnh",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Header Controls: Nút Back | Tiêu đề ngày | Nút Export
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nút Quay lại
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tiêu đề ngày tháng
            Text(
                text = photo.dateKey,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )

            // Nút Xuất sang Thư viện (Export)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onExport() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↓",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Footer Controls: Nút Xóa ảnh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showDeleteConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = Color.Red.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text("Xóa bức ảnh này?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Ảnh sẽ bị xóa vĩnh viễn khỏi cuộn Daily Roll.", color = Color.LightGray, fontSize = 13.sp)
            },
            containerColor = Color(0xFF1E1E1E),
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Xóa", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Hủy", color = Color.White)
                }
            }
        )
    }
}
