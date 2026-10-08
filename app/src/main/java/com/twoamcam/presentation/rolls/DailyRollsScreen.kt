package com.twoamcam.presentation.rolls

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twoamcam.core.storage.DailyRoll
import com.twoamcam.core.storage.LocalPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DailyRollsScreen(
    dailyRolls: List<DailyRoll>,
    onClose: () -> Unit,
    onPhotoClick: (LocalPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DAILY ROLLS",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)

        if (dailyRolls.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa có ảnh nào trong cuộn phim",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(dailyRolls, key = { it.dateKey }) { roll ->
                    RollSection(
                        roll = roll,
                        onPhotoClick = onPhotoClick
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RollSection(
    roll: DailyRoll,
    onPhotoClick: (LocalPhoto) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Tiêu đề ngày tháng
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = roll.dateKey,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${roll.photoCount} ảnh",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Lưới ảnh 3 cột thủ công bằng Rows
        val chunkedPhotos = roll.photos.chunked(3)
        chunkedPhotos.forEach { rowPhotos ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (photo in rowPhotos) {
                    Box(modifier = Modifier.weight(1f)) {
                        ThumbnailItem(
                            photo = photo,
                            onClick = { onPhotoClick(photo) }
                        )
                    }
                }
                // Điền chỗ trống nếu hàng cuối ít hơn 3 ảnh
                val remaining = 3 - rowPhotos.size
                for (i in 0 until remaining) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThumbnailItem(
    photo: LocalPhoto,
    onClick: () -> Unit
) {
    var thumbBitmap by remember(photo.filePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photo.filePath) {
        withContext(Dispatchers.IO) {
            // Tối ưu hóa bộ nhớ: Giảm kích thước ảnh mẫu (inSampleSize = 8) cho thumbnail
            val options = BitmapFactory.Options().apply {
                inSampleSize = 8
                inPreferredConfig = Bitmap.Config.RGB_565 // Tiết kiệm 50% RAM so với ARGB_8888
            }
            thumbBitmap = BitmapFactory.decodeFile(photo.filePath, options)
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap!!.asImageBitmap(),
                contentDescription = "Thumbnail ${photo.fileName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
