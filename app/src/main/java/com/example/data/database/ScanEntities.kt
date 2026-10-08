package com.example.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// 1. Scan Record Entity (Root Table)
@Entity(tableName = "scans")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val target: String,
    val timestamp: Long,
    val duration: String
)

// 2. Open Ports Entity
@Entity(
    tableName = "open_ports",
    foreignKeys = [
        ForeignKey(
            entity = ScanRecord::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["scanId"])]
)
data class OpenPortRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val port: Int,
    val service: String
)

// 3. Technologies Entity
@Entity(
    tableName = "technologies",
    foreignKeys = [
        ForeignKey(
            entity = ScanRecord::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["scanId"])]
)
data class TechRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val name: String
)

// 4. Discovered Paths Entity
@Entity(
    tableName = "discovered_paths",
    foreignKeys = [
        ForeignKey(
            entity = ScanRecord::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["scanId"])]
)
data class PathRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val path: String,
    val statusCode: Int,
    val size: Int
)

// 5. Vulnerabilities Entity
@Entity(
    tableName = "vulnerabilities",
    foreignKeys = [
        ForeignKey(
            entity = ScanRecord::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["scanId"])]
)
data class VulnRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val description: String
)

// 6. Emails Entity
@Entity(
    tableName = "emails",
    foreignKeys = [
        ForeignKey(
            entity = ScanRecord::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["scanId"])]
)
data class EmailRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val email: String
)

// 7. Devices Entity (Discovered Network Devices & Distribution)
@Entity(
    tableName = "devices",
    foreignKeys = [
        ForeignKey(
            entity = ScanRecord::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["scanId"])]
)
data class DeviceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val ipAddress: String,
    val macAddress: String,
    val hostname: String,
    val deviceType: String, // "Server", "Workstation", "Mobile", "IoT / Smart Device", "Network Gateway"
    val os: String,         // "Linux", "Windows", "macOS", "Android", "Embedded RTOS"
    val status: String,     // "Active", "Vulnerable", "Secured"
    val timestamp: Long
)

// Aggregation helper
data class DeviceTypeCount(
    val deviceType: String,
    val count: Int
)

// DAO interface
@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scans WHERE id = :scanId")
    suspend fun getScanById(scanId: Long): ScanRecord?

    @Query("SELECT * FROM open_ports WHERE scanId = :scanId")
    suspend fun getOpenPorts(scanId: Long): List<OpenPortRecord>

    @Query("SELECT * FROM technologies WHERE scanId = :scanId")
    suspend fun getTechnologies(scanId: Long): List<TechRecord>

    @Query("SELECT * FROM discovered_paths WHERE scanId = :scanId")
    suspend fun getDiscoveredPaths(scanId: Long): List<PathRecord>

    @Query("SELECT * FROM vulnerabilities WHERE scanId = :scanId")
    suspend fun getVulnerabilities(scanId: Long): List<VulnRecord>

    @Query("SELECT * FROM emails WHERE scanId = :scanId")
    suspend fun getEmails(scanId: Long): List<EmailRecord>

    @Query("SELECT * FROM devices WHERE scanId = :scanId")
    suspend fun getDevices(scanId: Long): List<DeviceRecord>

    @Query("SELECT * FROM devices ORDER BY timestamp ASC")
    fun getAllDevices(): Flow<List<DeviceRecord>>

    @Query("SELECT deviceType, COUNT(*) as count FROM devices GROUP BY deviceType")
    fun getDeviceTypeDistribution(): Flow<List<DeviceTypeCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpenPorts(ports: List<OpenPortRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTechnologies(techs: List<TechRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaths(paths: List<PathRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVulnerabilities(vulns: List<VulnRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<DeviceRecord>)

    @Query("DELETE FROM scans WHERE id = :scanId")
    suspend fun deleteScanById(scanId: Long)

    @Query("DELETE FROM scans")
    suspend fun deleteAllScans()
}
