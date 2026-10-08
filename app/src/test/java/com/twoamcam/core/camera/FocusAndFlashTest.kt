package com.twoamcam.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAndFlashTest {

    @Test
    fun testDefaultFocusCalibrationValues() {
        val calibration = FocusCalibration()
        assertEquals(1.0f, calibration.portraitDistanceMeters, 0.001f)
        assertEquals(2.5f, calibration.groupDistanceMeters, 0.001f)
    }

    @Test
    fun testCustomFocusCalibration() {
        val custom = FocusCalibration(portraitDistanceMeters = 0.8f, groupDistanceMeters = 3.0f)
        assertEquals(0.8f, custom.portraitDistanceMeters, 0.001f)
        assertEquals(3.0f, custom.groupDistanceMeters, 0.001f)

        // Tính diopter: D = 1 / meters
        val portraitDiopter = 1.0f / custom.portraitDistanceMeters
        val groupDiopter = 1.0f / custom.groupDistanceMeters

        assertEquals(1.25f, portraitDiopter, 0.001f)
        assertEquals(0.333f, groupDiopter, 0.005f)
    }

    @Test
    fun testDiopterCalculationAndClamping() {
        val calibration = FocusCalibration(portraitDistanceMeters = 1.0f, groupDistanceMeters = 2.5f)
        val portraitDiopter = 1.0f / calibration.portraitDistanceMeters
        val groupDiopter = 1.0f / calibration.groupDistanceMeters

        assertEquals(1.0f, portraitDiopter, 0.001f)
        assertEquals(0.4f, groupDiopter, 0.001f)

        // Giả lập hardware có minimumFocusDistance = 5.0 diopters (khoảng 0.2m)
        val hardwareMinFocusDistance = 5.0f
        val clampedPortrait = portraitDiopter.coerceIn(0f, hardwareMinFocusDistance)
        val clampedGroup = groupDiopter.coerceIn(0f, hardwareMinFocusDistance)

        assertEquals(1.0f, clampedPortrait, 0.001f)
        assertEquals(0.4f, clampedGroup, 0.001f)

        // Giả lập trường hợp macro/close range bị vượt quá max diopter
        val extremeCloseRangeDistanceMeters = 0.1f // diopter = 10.0
        val extremeDiopter = 1.0f / extremeCloseRangeDistanceMeters
        val clampedExtreme = extremeDiopter.coerceIn(0f, hardwareMinFocusDistance)
        assertEquals(5.0f, clampedExtreme, 0.001f)
    }

    @Test
    fun testFlashSafetyInitialState() {
        val flashManager = FlashSafetyManager()
        assertFalse("Flash mặc định phải là false khi chưa inspect hardware", flashManager.isFlashAvailable)

        val status = flashManager.checkFlashAvailability(null)
        assertTrue(status is FlashStatus.Unavailable)
    }
}
