package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.RawQueryResult
import com.example.data.database.ScanDatabase
import com.example.data.database.ScanRecord
import com.example.data.database.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface QueryState {
    object Idle : QueryState
    object Executing : QueryState
    data class Success(val result: RawQueryResult) : QueryState
    data class Error(val message: String) : QueryState
}

data class DetailedScan(
    val scan: ScanRecord,
    val openPorts: List<com.example.data.database.OpenPortRecord> = emptyList(),
    val technologies: List<com.example.data.database.TechRecord> = emptyList(),
    val paths: List<com.example.data.database.PathRecord> = emptyList(),
    val vulnerabilities: List<com.example.data.database.VulnRecord> = emptyList(),
    val emails: List<com.example.data.database.EmailRecord> = emptyList(),
    val devices: List<com.example.data.database.DeviceRecord> = emptyList()
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScanRepository

    init {
        val database = ScanDatabase.getDatabase(application)
        repository = ScanRepository(database)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = repository.allScans.first()
                if (existing.isEmpty()) {
                    loadSampleAcademicScans()
                }
            } catch (e: Exception) {
                // Ignore initialization check errors
            }
        }
    }

    val allScans: StateFlow<List<ScanRecord>> = repository.allScans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allDevices: StateFlow<List<com.example.data.database.DeviceRecord>> = repository.allDevices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedScanDetails = MutableStateFlow<DetailedScan?>(null)
    val selectedScanDetails: StateFlow<DetailedScan?> = _selectedScanDetails.asStateFlow()

    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState.asStateFlow()

    private val _currentQuery = MutableStateFlow("SELECT * FROM scans ORDER BY timestamp DESC LIMIT 5;")
    val currentQuery: StateFlow<String> = _currentQuery.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun updateQuery(sql: String) {
        _currentQuery.value = sql
    }

    fun selectScan(scan: ScanRecord) {
        viewModelScope.launch {
            val ports = repository.getOpenPorts(scan.id)
            val techs = repository.getTechnologies(scan.id)
            val paths = repository.getDiscoveredPaths(scan.id)
            val vulns = repository.getVulnerabilities(scan.id)
            val emails = repository.getEmails(scan.id)
            val devices = repository.getDevices(scan.id)
            _selectedScanDetails.value = DetailedScan(
                scan = scan,
                openPorts = ports,
                technologies = techs,
                paths = paths,
                vulnerabilities = vulns,
                emails = emails,
                devices = devices
            )
        }
    }

    fun clearSelection() {
        _selectedScanDetails.value = null
    }

    fun executeQuery(sql: String) {
        if (sql.isBlank()) {
            _queryState.value = QueryState.Error("Query cannot be empty")
            return
        }
        _queryState.value = QueryState.Executing
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.executeRawQuery(sql)
            withContext(Dispatchers.Main) {
                if (result.errorMessage != null) {
                    _queryState.value = QueryState.Error(result.errorMessage)
                } else {
                    _queryState.value = QueryState.Success(result)
                }
            }
        }
    }

    fun deleteScan(scanId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteScan(scanId)
            withContext(Dispatchers.Main) {
                if (_selectedScanDetails.value?.scan?.id == scanId) {
                    _selectedScanDetails.value = null
                }
            }
        }
    }

    fun clearAllScans() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllData()
            withContext(Dispatchers.Main) {
                _selectedScanDetails.value = null
                _queryState.value = QueryState.Idle
            }
        }
    }

    suspend fun importScanFromJson(jsonString: String): Long {
        return repository.importScanFromJson(jsonString)
    }

    fun loadSampleAcademicScans() {
        _isImporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // Load high-fidelity realistic academic lab scans based on the user's Python tools (nmap, whatweb, gobuster, nikto, theharvester)
            val samples = listOf(
                """{
                    "target": "swell.house",
                    "timestamp": "2026-06-30T18:00:00Z",
                    "summary": { "scan_time": "1m 15s" },
                    "tools": {
                        "nmap": {
                            "status": "success",
                            "ports": [
                                { "port": 80, "service": "http" },
                                { "port": 443, "service": "https" },
                                { "port": 22, "service": "ssh" }
                            ]
                        },
                        "whatweb": {
                            "status": "success",
                            "technologies": ["Nginx", "React.js", "HSTS"]
                        },
                        "gobuster": {
                            "status": "success",
                            "paths": [
                                { "path": "/admin", "status": 401, "size": 150 },
                                { "path": "/js", "status": 200, "size": 1200 },
                                { "path": "/images", "status": 200, "size": 850 }
                            ]
                        },
                        "nikto": {
                            "status": "success",
                            "vulnerabilities": [
                                "Header manquant: content-security-policy - CSP manquant - risque XSS",
                                "Version Nginx ancienne: nginx/1.18.0"
                            ]
                        },
                        "theharvester": {
                            "status": "success",
                            "emails": ["contact@swell.house", "support@swell.house"]
                        }
                    }
                }""",
                """{
                    "target": "adventureyogi.com",
                    "timestamp": "2026-06-30T18:15:00Z",
                    "summary": { "scan_time": "2m 4s" },
                    "tools": {
                        "nmap": {
                            "status": "success",
                            "ports": [
                                { "port": 80, "service": "http" },
                                { "port": 443, "service": "https" }
                            ]
                        },
                        "whatweb": {
                            "status": "success",
                            "technologies": ["Apache", "WordPress", "jQuery"]
                        },
                        "gobuster": {
                            "status": "success",
                            "paths": [
                                { "path": "/wp-login", "status": 200, "size": 4500 },
                                { "path": "/wp-content", "status": 403, "size": 220 }
                            ]
                        },
                        "nikto": {
                            "status": "success",
                            "vulnerabilities": [
                                "Header manquant: x-frame-options - XFO manquant - risque Clickjacking",
                                "Version Apache à vérifier: Apache/2.4"
                            ]
                        },
                        "theharvester": {
                            "status": "success",
                            "emails": ["info@adventureyogi.com"]
                        }
                    }
                }""",
                """{
                    "target": "drivethru.bookinglayer.com",
                    "timestamp": "2026-06-30T18:30:00Z",
                    "summary": { "scan_time": "45s" },
                    "tools": {
                        "nmap": {
                            "status": "success",
                            "ports": [
                                { "port": 443, "service": "https" }
                            ]
                        },
                        "whatweb": {
                            "status": "success",
                            "technologies": ["Cloudflare", "React.js"]
                        },
                        "gobuster": {
                            "status": "success",
                            "paths": [
                                { "path": "/api", "status": 403, "size": 85 }
                            ]
                        },
                        "nikto": {
                            "status": "success",
                            "vulnerabilities": [
                                "Header manquant: strict-transport-security - HSTS manquant - risque MITM"
                            ]
                        },
                        "theharvester": {
                            "status": "success",
                            "emails": ["admin@bookinglayer.com", "support@bookinglayer.com"]
                        }
                    }
                }""",
                """{
                    "target": "internal-lab-mesh.local",
                    "timestamp": "2026-07-07T10:00:00Z",
                    "summary": { "scan_time": "1m 40s" },
                    "tools": {
                        "nmap": {
                            "status": "success",
                            "ports": [
                                { "port": 22, "service": "ssh" },
                                { "port": 80, "service": "http" },
                                { "port": 1883, "service": "mqtt" },
                                { "port": 8080, "service": "http-proxy" }
                            ]
                        },
                        "whatweb": {
                            "status": "success",
                            "technologies": ["Linux / Embedded", "Mosquitto MQTT", "Grafana"]
                        },
                        "gobuster": {
                            "status": "success",
                            "paths": [
                                { "path": "/metrics", "status": 200, "size": 3200 },
                                { "path": "/dashboard", "status": 200, "size": 8400 }
                            ]
                        },
                        "nikto": {
                            "status": "success",
                            "vulnerabilities": [
                                "Unauthenticated MQTT broker exposed on port 1883",
                                "Default telemetry credentials detected"
                            ]
                        },
                        "theharvester": {
                            "status": "success",
                            "emails": ["iot-admin@lab-mesh.local"]
                        }
                    },
                    "devices": [
                        { "ip": "10.0.1.1", "mac": "00:E0:4C:11:22:33", "hostname": "edge-core-gw", "type": "Network Gateway", "os": "Embedded OpenWrt", "status": "Secured" },
                        { "ip": "10.0.1.10", "mac": "B8:27:EB:44:55:66", "hostname": "rpi4-telemetry-srv", "type": "Server", "os": "Linux / Debian 12", "status": "Active" },
                        { "ip": "10.0.1.101", "mac": "24:62:AB:77:88:99", "hostname": "lab-temp-sensor-01", "type": "IoT / Smart Device", "os": "FreeRTOS / ESP32", "status": "Vulnerable" },
                        { "ip": "10.0.1.102", "mac": "24:62:AB:77:88:9A", "hostname": "lab-power-meter-02", "type": "IoT / Smart Device", "os": "FreeRTOS / ESP32", "status": "Vulnerable" },
                        { "ip": "10.0.1.103", "mac": "5C:CF:7F:AA:BB:CC", "hostname": "hvac-controller-03", "type": "IoT / Smart Device", "os": "Embedded RTOS", "status": "Vulnerable" },
                        { "ip": "10.0.1.42", "mac": "3C:22:FB:DD:EE:FF", "hostname": "engineer-macbook-pro", "type": "Workstation", "os": "macOS Sonoma 14", "status": "Secured" },
                        { "ip": "10.0.1.75", "mac": "D4:61:9D:11:00:22", "hostname": "android-audit-scanner", "type": "Mobile", "os": "Android 14", "status": "Secured" }
                    ]
                }""",
                """{
                    "target": "corp-cloud-cluster.net",
                    "timestamp": "2026-07-14T14:20:00Z",
                    "summary": { "scan_time": "2m 30s" },
                    "tools": {
                        "nmap": {
                            "status": "success",
                            "ports": [
                                { "port": 443, "service": "https" },
                                { "port": 6443, "service": "k8s-api" },
                                { "port": 22, "service": "ssh" }
                            ]
                        },
                        "whatweb": {
                            "status": "success",
                            "technologies": ["Kubernetes", "Envoy Proxy", "Ubuntu Linux", "Go"]
                        },
                        "gobuster": {
                            "status": "success",
                            "paths": [
                                { "path": "/healthz", "status": 200, "size": 15 },
                                { "path": "/api/v1", "status": 401, "size": 95 }
                            ]
                        },
                        "nikto": {
                            "status": "success",
                            "vulnerabilities": [
                                "Kubernetes API port 6443 reachable without client certificate filtering"
                            ]
                        },
                        "theharvester": {
                            "status": "success",
                            "emails": ["devops@corp-cloud-cluster.net", "security@corp-cloud-cluster.net"]
                        }
                    },
                    "devices": [
                        { "ip": "172.16.0.1", "mac": "FE:ED:DE:AD:BE:EF", "hostname": "cloud-border-router", "type": "Network Gateway", "os": "Junos OS / Virtual", "status": "Secured" },
                        { "ip": "172.16.1.10", "mac": "52:54:00:AA:11:01", "hostname": "k8s-master-01", "type": "Server", "os": "Linux / Ubuntu 24.04", "status": "Vulnerable" },
                        { "ip": "172.16.1.11", "mac": "52:54:00:AA:11:02", "hostname": "k8s-worker-01", "type": "Server", "os": "Linux / Ubuntu 24.04", "status": "Active" },
                        { "ip": "172.16.1.12", "mac": "52:54:00:AA:11:03", "hostname": "k8s-worker-02", "type": "Server", "os": "Linux / Ubuntu 24.04", "status": "Active" },
                        { "ip": "172.16.1.13", "mac": "52:54:00:AA:11:04", "hostname": "k8s-worker-03", "type": "Server", "os": "Linux / Ubuntu 24.04", "status": "Active" },
                        { "ip": "172.16.2.20", "mac": "E4:54:E8:22:33:44", "hostname": "devops-workstation", "type": "Workstation", "os": "Linux / Fedora 40", "status": "Secured" },
                        { "ip": "172.16.2.25", "mac": "A0:C5:89:55:66:77", "hostname": "sec-ops-thinkpad", "type": "Workstation", "os": "Windows 11 Enterprise", "status": "Secured" },
                        { "ip": "172.16.3.50", "mac": "70:85:C2:88:99:AA", "hostname": "badge-reader-gate", "type": "IoT / Smart Device", "os": "Embedded RTOS", "status": "Secured" },
                        { "ip": "172.16.4.15", "mac": "BC:D1:1F:BB:CC:DD", "hostname": "oncall-pixel-phone", "type": "Mobile", "os": "Android 15", "status": "Active" }
                    ]
                }"""
            )

            samples.forEach { sample ->
                repository.importScanFromJson(sample)
            }

            withContext(Dispatchers.Main) {
                _isImporting.value = false
            }
        }
    }
}
