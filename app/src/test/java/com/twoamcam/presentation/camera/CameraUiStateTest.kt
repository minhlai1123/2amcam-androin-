package com.twoamcam.presentation.camera

import com.twoamcam.core.camera.FocusMode
import com.twoamcam.core.storage.DailyRoll
import com.twoamcam.core.storage.LocalPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraUiStateTest {

    @Test
    fun testInitialCameraUiState() {
        val state = CameraUiState()
        assertEquals(FocusMode.PORTRAIT, state.focusMode)
        assertTrue("Mặc định flash sẵn sàng", state.isFlashAvailable)
        assertFalse("Mặc định không trong trạng thái chụp", state.isCapturing)
        assertEquals(AppScreen.CAMERA, state.currentScreen)
        assertNull(state.selectedPhoto)
        assertNull(state.latestPhoto)
        assertTrue(state.dailyRolls.isEmpty())
        assertFalse(state.showFlashFeedback)
    }

    @Test
    fun testNavigationStateTransitions() {
        var state = CameraUiState()
        assertEquals(AppScreen.CAMERA, state.currentScreen)

        // Mở Daily Rolls
        state = state.copy(currentScreen = AppScreen.DAILY_ROLLS)
        assertEquals(AppScreen.DAILY_ROLLS, state.currentScreen)

        // Chọn ảnh mở viewer
        val photo = LocalPhoto(
            id = "test.jpg",
            fileName = "test.jpg",
            filePath = "/path/test.jpg",
            createdAt = 1000L,
            dateKey = "2026-10-06",
            fileSize = 1024L
        )
        state = state.copy(currentScreen = AppScreen.PHOTO_VIEWER, selectedPhoto = photo)
        assertEquals(AppScreen.PHOTO_VIEWER, state.currentScreen)
        assertEquals(photo, state.selectedPhoto)

        // Quay lại Camera
        state = state.copy(currentScreen = AppScreen.CAMERA, selectedPhoto = null)
        assertEquals(AppScreen.CAMERA, state.currentScreen)
        assertNull(state.selectedPhoto)
    }

    @Test
    fun testFocusModeSelectionState() {
        var state = CameraUiState(focusMode = FocusMode.PORTRAIT)
        assertEquals(FocusMode.PORTRAIT, state.focusMode)

        state = state.copy(focusMode = FocusMode.GROUP)
        assertEquals(FocusMode.GROUP, state.focusMode)
    }

    @Test
    fun testCaptureStateFeedbackAndLock() {
        var state = CameraUiState(isCapturing = false, showFlashFeedback = false)

        // Khi bấm Shutter
        state = state.copy(isCapturing = true, showFlashFeedback = true)
        assertTrue(state.isCapturing)
        assertTrue(state.showFlashFeedback)

        // Sau 150ms tắt flash feedback
        state = state.copy(showFlashFeedback = false)
        assertTrue(state.isCapturing)
        assertFalse(state.showFlashFeedback)

        // Khi hoàn thành lưu
        state = state.copy(isCapturing = false)
        assertFalse(state.isCapturing)
    }

    @Test
    fun testDailyRollsListUpdateInState() {
        val p1 = LocalPhoto("1.jpg", "1.jpg", "/p1", 1000L, "2026-10-06", 500L)
        val p2 = LocalPhoto("2.jpg", "2.jpg", "/p2", 2000L, "2026-10-06", 600L)
        val roll = DailyRoll("2026-10-06", listOf(p2, p1))

        var state = CameraUiState()
        state = state.copy(dailyRolls = listOf(roll), latestPhoto = p2)

        assertEquals(1, state.dailyRolls.size)
        assertEquals(2, state.dailyRolls[0].photoCount)
        assertEquals("Ảnh mới nhất phải là p2", p2, state.latestPhoto)
    }
}
