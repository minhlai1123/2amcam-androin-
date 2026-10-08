package com.twoamcam.presentation.camera

import android.app.Application
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.twoamcam.core.camera.CameraEngine
import com.twoamcam.core.camera.CaptureResult
import com.twoamcam.core.camera.FocusMode
import com.twoamcam.core.processing.ImageProcessingPipeline
import com.twoamcam.core.storage.DailyRoll
import com.twoamcam.core.storage.DailyRollRepository
import com.twoamcam.core.storage.LocalPhoto
import com.twoamcam.core.storage.MediaStoreSaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    CAMERA,
    DAILY_ROLLS,
    PHOTO_VIEWER
}

data class CameraUiState(
    val focusMode: FocusMode = FocusMode.PORTRAIT,
    val isFlashAvailable: Boolean = true,
    val isCapturing: Boolean = false,
    val isCameraReady: Boolean = false,
    val isCameraError: Boolean = false,
    val cameraErrorMessage: String? = null,
    val showFlashFeedback: Boolean = false,
    val latestPhoto: LocalPhoto? = null,
    val dailyRolls: List<DailyRoll> = emptyList(),
    val currentScreen: AppScreen = AppScreen.CAMERA,
    val selectedPhoto: LocalPhoto? = null,
    val toastMessage: String? = null
)

class CameraViewModel(
    application: Application
) : AndroidViewModel(application) {

    val cameraEngine: CameraEngine = CameraEngine(application)
    val imagePipeline: ImageProcessingPipeline = ImageProcessingPipeline()
    val dailyRollRepository: DailyRollRepository = DailyRollRepository(application)
    val mediaStoreSaver: MediaStoreSaver = MediaStoreSaver(application)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        cameraEngine.initialize(
            onReady = {
                _uiState.update {
                    it.copy(isCameraReady = true)
                }
            },
            onError = { error ->
                _uiState.update {
                    it.copy(
                        isCameraReady = false,
                        isCameraError = true,
                        cameraErrorMessage = error.localizedMessage
                    )
                }
            }
        )
        loadDailyRolls()
    }

    fun bindCameraToLifecycle(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        cameraEngine.bindCamera(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            onBound = {
                _uiState.update {
                    it.copy(
                        isFlashAvailable = cameraEngine.flashSafetyManager.isFlashAvailable,
                        isCameraReady = true,
                        isCameraError = false
                    )
                }
            },
            onError = { error ->
                _uiState.update {
                    it.copy(
                        isCameraReady = false,
                        isCameraError = true,
                        cameraErrorMessage = error.localizedMessage
                    )
                }
            }
        )
    }

    fun selectFocusMode(mode: FocusMode) {
        if (_uiState.value.focusMode == mode) return
        cameraEngine.setFocusMode(mode)
        _uiState.update {
            it.copy(focusMode = mode)
        }
    }

    fun triggerShutter() {
        if (_uiState.value.isCapturing) return

        _uiState.update {
            it.copy(
                isCapturing = true,
                showFlashFeedback = true
            )
        }

        // Tắt hiệu ứng chớp trắng UI sau 150ms
        viewModelScope.launch {
            delay(150)
            _uiState.update { it.copy(showFlashFeedback = false) }
        }

        cameraEngine.takePhoto { result ->
            viewModelScope.launch {
                when (result) {
                    is CaptureResult.Success -> {
                        // 1. Chạy Film Processing Pipeline
                        val filmResult = imagePipeline.processImage(result.jpegBytes)

                        // 2. Lưu ảnh vào Daily Rolls local storage
                        val saveResult = dailyRollRepository.savePhoto(filmResult.jpegBytes)

                        saveResult.onSuccess { photo ->
                            loadDailyRolls()
                            _uiState.update {
                                it.copy(
                                    isCapturing = false,
                                    latestPhoto = photo
                                )
                            }
                        }.onFailure { err ->
                            _uiState.update {
                                it.copy(
                                    isCapturing = false,
                                    toastMessage = "Lỗi lưu ảnh: ${err.localizedMessage}"
                                )
                            }
                        }
                    }
                    is CaptureResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isCapturing = false,
                                toastMessage = "Chụp thất bại: ${result.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadDailyRolls() {
        viewModelScope.launch {
            val rolls = dailyRollRepository.getDailyRolls()
            val firstPhoto = rolls.firstOrNull()?.photos?.firstOrNull()
            _uiState.update {
                it.copy(
                    dailyRolls = rolls,
                    latestPhoto = firstPhoto ?: it.latestPhoto
                )
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update {
            it.copy(currentScreen = screen)
        }
    }

    fun openPhotoViewer(photo: LocalPhoto) {
        _uiState.update {
            it.copy(
                selectedPhoto = photo,
                currentScreen = AppScreen.PHOTO_VIEWER
            )
        }
    }

    fun deletePhoto(photo: LocalPhoto) {
        viewModelScope.launch {
            val success = dailyRollRepository.deletePhoto(photo)
            if (success) {
                loadDailyRolls()
                _uiState.update {
                    it.copy(
                        selectedPhoto = null,
                        currentScreen = AppScreen.DAILY_ROLLS
                    )
                }
            } else {
                _uiState.update {
                    it.copy(toastMessage = "Không thể xóa bức ảnh này")
                }
            }
        }
    }

    fun exportPhoto(photo: LocalPhoto) {
        viewModelScope.launch {
            val result = mediaStoreSaver.exportPhoto(photo)
            result.onSuccess {
                _uiState.update {
                    it.copy(toastMessage = "Đã lưu ảnh vào Thư viện")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(toastMessage = "Lỗi xuất ảnh: ${err.localizedMessage}")
                }
            }
        }
    }

    fun showToast(message: String) {
        _uiState.update {
            it.copy(toastMessage = message)
        }
    }

    fun clearToast() {
        _uiState.update {
            it.copy(toastMessage = null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraEngine.release()
    }
}
