package com.twoamcam.presentation.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import com.twoamcam.core.storage.LocalPhoto
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.twoamcam.core.camera.FocusMode
import com.twoamcam.presentation.rolls.DailyRollsScreen
import com.twoamcam.presentation.rolls.FullscreenImageViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var previousScreen by remember { mutableStateOf(AppScreen.DAILY_ROLLS) }
    var pendingExportPhoto by remember { mutableStateOf<LocalPhoto?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingExportPhoto?.let { photo ->
                viewModel.exportPhoto(photo)
                pendingExportPhoto = null
            }
        } else {
            pendingExportPhoto = null
            viewModel.showToast("Cần quyền bộ nhớ để xuất ảnh vào Thư viện")
        }
    }

    val handleExportPhoto: (LocalPhoto) -> Unit = { photo ->
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            val storageGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (storageGranted) {
                viewModel.exportPhoto(photo)
            } else {
                pendingExportPhoto = photo
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            viewModel.exportPhoto(photo)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Hiển thị toast thông báo nếu có
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    // Điều hướng toàn cục giữa Camera -> Daily Rolls -> Fullscreen Viewer
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState.currentScreen) {
            AppScreen.DAILY_ROLLS -> {
                DailyRollsScreen(
                    dailyRolls = uiState.dailyRolls,
                    onClose = { viewModel.navigateTo(AppScreen.CAMERA) },
                    onPhotoClick = { photo ->
                        previousScreen = AppScreen.DAILY_ROLLS
                        viewModel.openPhotoViewer(photo)
                    }
                )
            }

            AppScreen.PHOTO_VIEWER -> {
                uiState.selectedPhoto?.let { photo ->
                    FullscreenImageViewer(
                        photo = photo,
                        onBack = {
                            viewModel.navigateTo(previousScreen)
                        },
                        onExport = { handleExportPhoto(photo) },
                        onDelete = { viewModel.deletePhoto(photo) }
                    )
                } ?: run {
                    viewModel.navigateTo(previousScreen)
                }
            }

            AppScreen.CAMERA -> {
                CameraMainView(
                    viewModel = viewModel,
                    uiState = uiState,
                    hasCameraPermission = hasCameraPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenLatestPhoto = {
                        uiState.latestPhoto?.let { photo ->
                            previousScreen = AppScreen.CAMERA
                            viewModel.openPhotoViewer(photo)
                        } ?: run {
                            viewModel.navigateTo(AppScreen.DAILY_ROLLS)
                        }
                    },
                    onBindCamera = { surfaceProvider ->
                        viewModel.bindCameraToLifecycle(lifecycleOwner, surfaceProvider)
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun CameraMainView(
    viewModel: CameraViewModel,
    uiState: CameraUiState,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenLatestPhoto: () -> Unit,
    onBindCamera: (androidx.camera.core.Preview.SurfaceProvider) -> Unit
) {
    val shutterScale by animateFloatAsState(
        targetValue = if (uiState.isCapturing) 0.93f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "shutterScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasCameraPermission) {
            // Màn hình tối giản yêu cầu cấp quyền Camera
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CẦN QUYỀN TRUY CẬP CAMERA",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "2AM Cam chụp ảnh direct-flash hoàn toàn offline trong bóng đêm.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .clickable { onRequestPermission() }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "CẤP QUYỀN",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            // Camera Native Viewfinder thật qua PreviewView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        onBindCamera(surfaceProvider)
                    }
                }
            )

            // Flash Feedback Overlay (Chớp trắng 150ms khi bấm chụp)
            AnimatedVisibility(
                visible = uiState.showFlashFeedback,
                enter = fadeIn(animationSpec = tween(50)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.85f))
                )
            }

            // Header Bar: Cảnh báo flash nếu phần cứng không hỗ trợ
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                if (!uiState.isFlashAvailable) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "FLASH KHÔNG KHẢ DỤNG",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Footer Bar: Mode Selector & Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Focus Mode Selector: Portrait vs Group
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    FocusMode.values().forEach { mode ->
                        val isSelected = uiState.focusMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color.White else Color.Black.copy(alpha = 0.4f))
                                .clickable { viewModel.selectFocusMode(mode) }
                                .padding(horizontal = 18.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = mode.label,
                                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Action Row: Latest Photo Thumbnail | Shutter Button | ROLLS Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Góc dưới trái: Thumbnail ảnh vừa chụp
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LatestPhotoThumbnail(
                            latestPhoto = uiState.latestPhoto,
                            onClick = onOpenLatestPhoto
                        )
                    }

                    // Trung tâm: Shutter Button lớn hình tròn nổi bật
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(shutterScale)
                            .border(4.dp, Color.White, CircleShape)
                            .clickable(enabled = !uiState.isCapturing) {
                                viewModel.triggerShutter()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isCapturing) Color.Gray.copy(alpha = 0.6f) else Color.White)
                        )
                    }

                    // Góc dưới phải: Nút ROLLS
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable { viewModel.navigateTo(AppScreen.DAILY_ROLLS) }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "ROLLS",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestPhotoThumbnail(
    latestPhoto: com.twoamcam.core.storage.LocalPhoto?,
    onClick: () -> Unit
) {
    var thumbBitmap by remember(latestPhoto?.filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(latestPhoto?.filePath) {
        val path = latestPhoto?.filePath
        if (path != null) {
            withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = 8
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                thumbBitmap = BitmapFactory.decodeFile(path, opts)
            }
        } else {
            thumbBitmap = null
        }
    }

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap!!.asImageBitmap(),
                contentDescription = "Ảnh vừa chụp gần nhất",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "🎞",
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }
}
