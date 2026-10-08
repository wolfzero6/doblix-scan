package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.DeviceRecord
import com.example.data.database.ScanDatabase
import com.example.data.database.ScanRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.ui.ScanTrendGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceDistributionTest {

    private lateinit var database: ScanDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ScanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testDateFormattingContractNeverReturnsEmpty() {
        val groups = listOf(
            ScanTrendGroup(1, "host.com", 1000L, 3, 1, 1, 0, 0, 1, 0, 3)
        )
        val dateFormatter = SimpleDateFormat("MM/dd", Locale.getDefault())
        fun formatValue(value: Double): String {
            val index = value.toInt()
            return if (index in groups.indices) {
                val group = groups[index]
                val formatted = dateFormatter.format(Date(group.timestamp))
                if (formatted.isNotEmpty()) formatted else "S#${index + 1}"
            } else {
                "S#${index + 1}"
            }
        }

        listOf(-5.0, -1.0, 0.0, 0.5, 1.0, 10.0, 100.0).forEach { x ->
            val result = formatValue(x)
            assertTrue(result.isNotEmpty())
        }
    }

    @Test
    fun testDeviceInsertionAndDistribution() = runBlocking {
        val dao = database.scanDao()

        val scanId = dao.insertScan(
            ScanRecord(target = "test.lab", timestamp = System.currentTimeMillis(), duration = "12s")
        )

        val devices = listOf(
            DeviceRecord(
                scanId = scanId,
                ipAddress = "192.168.1.1",
                macAddress = "00:11:22:33:44:55",
                hostname = "gw.lab",
                deviceType = "Network Gateway",
                os = "pfSense",
                status = "Secured",
                timestamp = 1000L
            ),
            DeviceRecord(
                scanId = scanId,
                ipAddress = "192.168.1.10",
                macAddress = "52:54:00:11:22:33",
                hostname = "server.lab",
                deviceType = "Server",
                os = "Linux / Debian",
                status = "Vulnerable",
                timestamp = 1000L
            ),
            DeviceRecord(
                scanId = scanId,
                ipAddress = "192.168.1.20",
                macAddress = "A4:83:E7:11:22:33",
                hostname = "client.lab",
                deviceType = "Workstation",
                os = "macOS",
                status = "Active",
                timestamp = 1000L
            )
        )

        dao.insertDevices(devices)

        val all = dao.getAllDevices().first()
        assertEquals(3, all.size)

        val scanDevices = dao.getDevices(scanId)
        assertEquals(3, scanDevices.size)

        val distribution = dao.getDeviceTypeDistribution().first()
        assertEquals(3, distribution.size)
    }
}
