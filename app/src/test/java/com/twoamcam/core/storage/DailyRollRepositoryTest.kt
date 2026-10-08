package com.twoamcam.core.storage

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DailyRollRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: DailyRollRepository

    @Before
    fun setup() {
        val baseTemp = File(System.getProperty("java.io.tmpdir"), "2AMCamTest_${System.currentTimeMillis()}")
        baseTemp.mkdirs()
        tempDir = baseTemp
        repository = DailyRollRepository(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSavePhotoAtomicAndFileCreated() = runBlocking {
        val dummyData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x12, 0x34)
        val timestamp = System.currentTimeMillis()

        val result = repository.savePhoto(dummyData, timestamp)
        assertTrue("Lưu ảnh phải thành công", result.isSuccess)

        val localPhoto = result.getOrNull()
        assertNotNull(localPhoto)
        assertEquals(dummyData.size.toLong(), localPhoto!!.fileSize)
        assertTrue("File ảnh phải thực sự tồn tại trên đĩa", File(localPhoto.filePath).exists())

        // Kiểm tra không còn file rác .tmp trong thư mục
        val dayDir = File(localPhoto.filePath).parentFile
        val tmpFiles = dayDir?.listFiles { file -> file.name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("Không được để lại file tạm .tmp", 0, tmpFiles.size)
    }

    @Test
    fun testDateGroupingInLocalTimezone() = runBlocking {
        val dummyData = byteArrayOf(0x01, 0x02, 0x03)

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }

        val todayTime = System.currentTimeMillis()
        val yesterdayTime = todayTime - 86400000L // - 1 ngày

        val todayKey = formatter.format(Date(todayTime))
        val yesterdayKey = formatter.format(Date(yesterdayTime))

        repository.savePhoto(dummyData, todayTime)
        repository.savePhoto(dummyData, yesterdayTime)

        val rolls = repository.getDailyRolls()
        assertEquals("Phải có 2 cuộn ngày khác nhau", 2, rolls.size)

        // Sắp xếp ngày mới nhất trước
        assertEquals("Cuộn đầu tiên phải là ngày hôm nay", todayKey, rolls[0].dateKey)
        assertEquals("Cuộn thứ hai phải là ngày hôm qua", yesterdayKey, rolls[1].dateKey)
    }

    @Test
    fun testCollisionResolutionWithSameTimestamp() = runBlocking {
        val dummyData1 = byteArrayOf(0x11, 0x22)
        val dummyData2 = byteArrayOf(0x33, 0x44, 0x55)
        val fixedTimestamp = 1700000000000L

        val result1 = repository.savePhoto(dummyData1, fixedTimestamp)
        val result2 = repository.savePhoto(dummyData2, fixedTimestamp)

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)

        val photo1 = result1.getOrNull()!!
        val photo2 = result2.getOrNull()!!

        assertFalse("Tên file không được trùng nhau", photo1.fileName == photo2.fileName)
        assertTrue("File 1 phải tồn tại", File(photo1.filePath).exists())
        assertTrue("File 2 phải tồn tại", File(photo2.filePath).exists())
        assertEquals(2, photo1.fileSize)
        assertEquals(3, photo2.fileSize)
    }

    @Test
    fun testDeterministicSortingWithinDay() = runBlocking {
        val dummy = byteArrayOf(0x0A, 0x0B)
        val baseTime = System.currentTimeMillis()

        // Lưu ảnh 1 lúc baseTime, ảnh 2 lúc baseTime + 1000
        val p1 = repository.savePhoto(dummy, baseTime).getOrNull()!!
        val p2 = repository.savePhoto(dummy, baseTime + 1000).getOrNull()!!

        val photos = repository.getPhotos(p1.dateKey)
        assertEquals(2, photos.size)
        // Ảnh mới nhất phải đứng đầu danh sách
        assertEquals("Ảnh p2 (mới hơn) phải đứng trước", p2.id, photos[0].id)
        assertEquals("Ảnh p1 (cũ hơn) phải đứng sau", p1.id, photos[1].id)
    }

    @Test
    fun testCorruptedOrEmptyFileIgnored() = runBlocking {
        val dummy = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val photo = repository.savePhoto(dummy).getOrNull()!!

        val dayDir = File(photo.filePath).parentFile!!

        // Tạo 1 file rỗng 0 bytes và 1 file không phải .jpg
        File(dayDir, "corrupted_empty.jpg").createNewFile()
        File(dayDir, "temp_cache.tmp").writeBytes(byteArrayOf(0x01, 0x02))

        val readPhotos = repository.getPhotos(photo.dateKey)
        assertEquals("Chỉ được đọc file hợp lệ > 0 bytes và đuôi .jpg", 1, readPhotos.size)
        assertEquals(photo.id, readPhotos[0].id)
    }

    @Test
    fun testDeletePhotoAndCleanupEmptyDayFolder() = runBlocking {
        val dummy = byteArrayOf(0x01, 0x02, 0x03)
        val photo = repository.savePhoto(dummy).getOrNull()!!
        val file = File(photo.filePath)
        val dayDir = file.parentFile!!

        assertTrue(file.exists())
        assertTrue(dayDir.exists())

        val deleted = repository.deletePhoto(photo)
        assertTrue("Xóa file phải thành công", deleted)
        assertFalse("File ảnh không còn tồn tại", file.exists())
        assertFalse("Thư mục ngày rỗng phải được dọn dẹp tự động", dayDir.exists())

        val rolls = repository.getDailyRolls()
        assertEquals(0, rolls.size)
    }

    @Test
    fun testConcurrentSaveSafety() = runBlocking {
        val dummy = byteArrayOf(0xDE.toByte(), 0xAD.toByte())

        // Chạy 5 tác vụ lưu đồng thời
        val jobs = (1..5).map { index ->
            async {
                repository.savePhoto(dummy, System.currentTimeMillis() + index)
            }
        }

        val results = jobs.awaitAll()
        results.forEach { res ->
            assertTrue("Tác vụ lưu đồng thời phải thành công", res.isSuccess)
        }

        val rolls = repository.getDailyRolls()
        val totalPhotos = rolls.sumOf { it.photoCount }
        assertEquals("Tất cả 5 ảnh lưu đồng thời phải được lưu toàn vẹn", 5, totalPhotos)
    }
}
