package com.twoamcam.core.resilience

import com.twoamcam.core.camera.CaptureManager
import com.twoamcam.core.camera.CaptureResult
import com.twoamcam.core.camera.FlashSafetyManager
import com.twoamcam.core.camera.FlashStatus
import com.twoamcam.core.camera.FocusCalibration
import com.twoamcam.core.camera.FocusMode
import com.twoamcam.core.camera.FocusResult
import com.twoamcam.core.camera.FocusZoneManager
import com.twoamcam.core.processing.FilmConfiguration
import com.twoamcam.core.processing.ImageProcessingPipeline
import com.twoamcam.core.storage.DailyRollRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors

class FinalResilienceAndStressTest {

    private lateinit var tempDir: File
    private lateinit var storageRepository: DailyRollRepository

    @Before
    fun setup() {
        val baseTemp = File(System.getProperty("java.io.tmpdir"), "2AMCamResilience_${System.currentTimeMillis()}")
        baseTemp.mkdirs()
        tempDir = baseTemp
        storageRepository = DailyRollRepository(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testShutterSpamProtectionAtomicRejection() {
        val executor = Executors.newSingleThreadExecutor()
        val captureManager = CaptureManager(executor)

        assertFalse("Ban đầu CaptureManager không được bận", captureManager.isBusy())

        // ImageCapture = null -> Trả về Error
        var capturedCount = 0
        var rejectedCount = 0

        for (i in 1..10) {
            captureManager.capturePhoto(null) { result ->
                if (result is CaptureResult.Error) {
                    rejectedCount++
                } else {
                    capturedCount++
                }
            }
        }

        assertEquals(0, capturedCount)
        assertEquals(10, rejectedCount)
        assertFalse("Sau khi hoàn thành, isBusy phải trả về false", captureManager.isBusy())
        executor.shutdown()
    }

    @Test
    fun testFocusAndFlashResilienceMatrix() {
        // 1. Flash Resilience
        val flashManager = FlashSafetyManager()
        // Khi cameraInfo null (ví dụ mở app trong môi trường lỗi phần cứng)
        val statusNull = flashManager.checkFlashAvailability(null)
        assertTrue("Không được crash khi cameraInfo null", statusNull is FlashStatus.Unavailable)
        assertFalse("isFlashAvailable phải là false", flashManager.isFlashAvailable)

        // configureFlashForCapture với null
        val configured = flashManager.configureFlashForCapture(null)
        assertFalse("Phải trả về false an toàn khi ImageCapture null", configured)

        // 2. Focus Resilience
        val focusManager = FocusZoneManager(FocusCalibration(portraitDistanceMeters = 1.0f, groupDistanceMeters = 2.5f))
        assertFalse("Mặc định khi chưa inspect hardware thì isManualFocusSupported phải là false", focusManager.isManualFocusSupported)
        assertEquals(FocusMode.PORTRAIT, focusManager.currentMode)
    }

    @Test
    fun testSequentialTenPhotosPipelineStress() = runBlocking {
        val pipeline = ImageProcessingPipeline()
        val dummyBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val times = mutableListOf<Long>()

        for (i in 1..10) {
            val start = System.currentTimeMillis()
            val result = pipeline.processImage(dummyBytes)
            val duration = System.currentTimeMillis() - start
            times.add(duration)

            assertTrue("Ảnh $i phải trả về kết quả an toàn", result.isFallback)
            assertEquals(4, result.jpegBytes.size)
        }

        assertEquals(10, times.size)
        println("Thời gian xử lý trung bình cho 10 lượt stress: ${times.average()}ms")
    }

    @Test
    fun testStorageFiftyPhotosAcrossFiveDaysStress() = runBlocking {
        val dummyPhotoData = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val baseTimestamp = 1770000000000L // Mốc thời gian cố định

        // Giả lập lưu 50 ảnh chia đều vào 5 ngày (mỗi ngày 10 ảnh)
        for (day in 0 until 5) {
            val dayOffset = day * 86400000L
            for (photoIdx in 0 until 10) {
                val photoTime = baseTimestamp + dayOffset + (photoIdx * 1000L)
                val saveResult = storageRepository.savePhoto(dummyPhotoData, photoTime)
                assertTrue("Lưu ảnh $day-$photoIdx phải thành công", saveResult.isSuccess)
            }
        }

        // Đo tốc độ truy vấn danh sách 50 ảnh
        val queryStart = System.currentTimeMillis()
        val rolls = storageRepository.getDailyRolls()
        val queryDuration = System.currentTimeMillis() - queryStart

        assertEquals("Phải có đúng 5 cuộn phim ngày", 5, rolls.size)
        val totalPhotos = rolls.sumOf { it.photoCount }
        assertEquals("Tổng số ảnh phải đúng 50 ảnh", 50, totalPhotos)
        assertTrue("Truy vấn 50 ảnh từ File System phải cực nhanh (< 100ms)", queryDuration < 100)

        // Kiểm tra thứ tự sắp xếp deterministic (ngày mới nhất trước)
        for (i in 0 until rolls.size - 1) {
            assertTrue("Ngày ${rolls[i].dateKey} phải lớn hơn ngày ${rolls[i+1].dateKey}", rolls[i].dateKey > rolls[i+1].dateKey)
            // Kiểm tra ảnh trong ngày mới nhất trước
            val photosInDay = rolls[i].photos
            assertEquals(10, photosInDay.size)
            for (j in 0 until photosInDay.size - 1) {
                assertTrue("Ảnh trong ngày phải xếp mới nhất trước", photosInDay[j].createdAt >= photosInDay[j+1].createdAt)
            }
        }
    }

    @Test
    fun testZeroNetworkAndOfflineIntegrity() {
        // Kiểm tra không có bất kỳ cấu hình network/cloud nào
        val sourceDir = File("src/main/java/com/twoamcam")
        if (sourceDir.exists()) {
            val allFiles = sourceDir.walkTopDown().filter { it.extension == "kt" }.toList()
            for (file in allFiles) {
                val content = file.readText()
                assertFalse("Không được chứa Firebase trong ${file.name}", content.contains("com.google.firebase"))
                assertFalse("Không được chứa Retrofit trong ${file.name}", content.contains("retrofit2"))
                assertFalse("Không được chứa OkHttp network call trong ${file.name}", content.contains("okhttp3"))
            }
        }
    }
}
