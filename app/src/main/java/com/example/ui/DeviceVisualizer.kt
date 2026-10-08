package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.DeviceRecord
import com.example.data.database.ScanRecord
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class ChartViewType {
    LINE_TREND,
    COLUMN_VOLUME
}

enum class MetricFilter {
    ALL_DEVICES,
    SERVERS,
    WORKSTATIONS,
    IOT,
    MOBILE,
    GATEWAYS
}

data class ScanTrendGroup(
    val scanId: Long,
    val target: String,
    val timestamp: Long,
    val totalCount: Int,
    val serverCount: Int,
    val workstationCount: Int,
    val iotCount: Int,
    val mobileCount: Int,
    val gatewayCount: Int,
    val vulnerableCount: Int,
    val securedCount: Int
)

@Composable
fun DeviceVisualizerTab(
    viewModel: ScanViewModel,
    onNavigateToSql: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val allScans by viewModel.allScans.collectAsState()
    val allDevices by viewModel.allDevices.collectAsState()

    var selectedMetric by remember { mutableStateOf(MetricFilter.ALL_DEVICES) }
    var chartViewType by remember { mutableStateOf(ChartViewType.LINE_TREND) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") } // "ALL", "Vulnerable", "Secured", "Active"

    // Group devices by scan session to construct chronological trend data
    val trendGroups = remember(allScans, allDevices) {
        val scanMap = allScans.associateBy { it.id }
        val devicesByScan = allDevices.groupBy { it.scanId }

        allScans.sortedBy { it.timestamp }.map { scan ->
            val devs = devicesByScan[scan.id] ?: emptyList()
            ScanTrendGroup(
                scanId = scan.id,
                target = scan.target,
                timestamp = scan.timestamp,
                totalCount = devs.size,
                serverCount = devs.count { it.deviceType.contains("Server", ignoreCase = true) },
                workstationCount = devs.count { it.deviceType.contains("Workstation", ignoreCase = true) },
                iotCount = devs.count { it.deviceType.contains("IoT", ignoreCase = true) },
                mobileCount = devs.count { it.deviceType.contains("Mobile", ignoreCase = true) },
                gatewayCount = devs.count { it.deviceType.contains("Gateway", ignoreCase = true) },
                vulnerableCount = devs.count { it.status.equals("Vulnerable", ignoreCase = true) },
                securedCount = devs.count { it.status.equals("Secured", ignoreCase = true) }
            )
        }
    }

    // Chart model producer for Vico
    val chartModelProducer = remember { CartesianChartModelProducer() }
    val dateFormatter = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

    // X-Axis label formatter mapping index -> Date / Target (never returns empty string)
    val bottomAxisFormatter = remember(trendGroups) {
        CartesianValueFormatter { _, value, _ ->
            val index = value.toInt()
            if (index in trendGroups.indices) {
                val group = trendGroups[index]
                val formatted = dateFormatter.format(Date(group.timestamp))
                if (formatted.isNotEmpty()) formatted else "S#${index + 1}"
            } else {
                "S#${index + 1}"
            }
        }
    }

    // Update Vico Chart Model whenever filters or trends change
    LaunchedEffect(trendGroups, selectedMetric, chartViewType) {
        if (trendGroups.isNotEmpty()) {
            val dataPoints = trendGroups.map { group ->
                when (selectedMetric) {
                    MetricFilter.ALL_DEVICES -> group.totalCount
                    MetricFilter.SERVERS -> group.serverCount
                    MetricFilter.WORKSTATIONS -> group.workstationCount
                    MetricFilter.IOT -> group.iotCount
                    MetricFilter.MOBILE -> group.mobileCount
                    MetricFilter.GATEWAYS -> group.gatewayCount
                }
            }

            chartModelProducer.runTransaction {
                if (chartViewType == ChartViewType.LINE_TREND) {
                    lineSeries {
                        series(dataPoints)
                    }
                } else {
                    columnSeries {
                        series(dataPoints)
                    }
                }
            }
        }
    }

    // Device category totals
    val totalDevices = allDevices.size
    val serverTotal = allDevices.count { it.deviceType.contains("Server", ignoreCase = true) }
    val workstationTotal = allDevices.count { it.deviceType.contains("Workstation", ignoreCase = true) }
    val iotTotal = allDevices.count { it.deviceType.contains("IoT", ignoreCase = true) }
    val mobileTotal = allDevices.count { it.deviceType.contains("Mobile", ignoreCase = true) }
    val gatewayTotal = allDevices.count { it.deviceType.contains("Gateway", ignoreCase = true) }
    val vulnerableTotal = allDevices.count { it.status.equals("Vulnerable", ignoreCase = true) }
    val securedTotal = allDevices.count { it.status.equals("Secured", ignoreCase = true) }

    // Filtered device inventory
    val filteredDevices = remember(allDevices, searchQuery, selectedMetric, statusFilter) {
        allDevices.filter { dev ->
            val matchesSearch = searchQuery.isBlank() ||
                dev.hostname.contains(searchQuery, ignoreCase = true) ||
                dev.ipAddress.contains(searchQuery, ignoreCase = true) ||
                dev.macAddress.contains(searchQuery, ignoreCase = true) ||
                dev.os.contains(searchQuery, ignoreCase = true)

            val matchesMetric = when (selectedMetric) {
                MetricFilter.ALL_DEVICES -> true
                MetricFilter.SERVERS -> dev.deviceType.contains("Server", ignoreCase = true)
                MetricFilter.WORKSTATIONS -> dev.deviceType.contains("Workstation", ignoreCase = true)
                MetricFilter.IOT -> dev.deviceType.contains("IoT", ignoreCase = true)
                MetricFilter.MOBILE -> dev.deviceType.contains("Mobile", ignoreCase = true)
                MetricFilter.GATEWAYS -> dev.deviceType.contains("Gateway", ignoreCase = true)
            }

            val matchesStatus = when (statusFilter) {
                "ALL" -> true
                "Vulnerable" -> dev.status.equals("Vulnerable", ignoreCase = true)
                "Secured" -> dev.status.equals("Secured", ignoreCase = true)
                "Active" -> dev.status.equals("Active", ignoreCase = true)
                else -> true
            }

            matchesSearch && matchesMetric && matchesStatus
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("device_visualizer_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Banner & Quick Insights
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Device Trends",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "SQLite Device Distribution Trends",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$totalDevices devices recorded across ${allScans.size} scan sessions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // SQL Query Engine shortcut
                        FilledTonalButton(
                            onClick = {
                                onNavigateToSql("SELECT deviceType, os, status, COUNT(*) as count FROM devices GROUP BY deviceType, os, status ORDER BY count DESC;")
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("run_sql_devices_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SQL Query", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Stat Metric Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricStatCard(
                            title = "Total Discovered",
                            value = totalDevices.toString(),
                            subtitle = "Across all networks",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = "Vulnerable",
                            value = vulnerableTotal.toString(),
                            subtitle = if (totalDevices > 0) "${(vulnerableTotal * 100) / totalDevices}% of inventory" else "0%",
                            color = Color(0xFFFF5252),
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = "Secured",
                            value = securedTotal.toString(),
                            subtitle = "Passing checks",
                            color = Color(0xFF00E676),
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = "Infra Density",
                            value = if (allScans.isNotEmpty()) String.format(Locale.US, "%.1f", totalDevices.toDouble() / allScans.size) else "0.0",
                            subtitle = "Avg / audit scan",
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Main Interactive Chart Section
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Chart Controls Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Device Temporal Trajectory",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Historical trend by scan checkpoint (${dateFormatter.format(Date(trendGroups.firstOrNull()?.timestamp ?: System.currentTimeMillis()))} - ${dateFormatter.format(Date(trendGroups.lastOrNull()?.timestamp ?: System.currentTimeMillis()))})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }

                        // Toggle Line vs Column
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp)
                        ) {
                            IconToggleButton(
                                checked = chartViewType == ChartViewType.LINE_TREND,
                                onCheckedChange = { chartViewType = ChartViewType.LINE_TREND },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("chart_type_line")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Line Chart",
                                    tint = if (chartViewType == ChartViewType.LINE_TREND) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconToggleButton(
                                checked = chartViewType == ChartViewType.COLUMN_VOLUME,
                                onCheckedChange = { chartViewType = ChartViewType.COLUMN_VOLUME },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("chart_type_column")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Column Chart",
                                    tint = if (chartViewType == ChartViewType.COLUMN_VOLUME) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Metric Chips Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val metrics = listOf(
                            MetricFilter.ALL_DEVICES to "All Types ($totalDevices)",
                            MetricFilter.SERVERS to "Servers ($serverTotal)",
                            MetricFilter.WORKSTATIONS to "Workstations ($workstationTotal)",
                            MetricFilter.IOT to "IoT / Smart ($iotTotal)",
                            MetricFilter.MOBILE to "Mobile ($mobileTotal)",
                            MetricFilter.GATEWAYS to "Gateways ($gatewayTotal)"
                        )

                        items(metrics) { (metric, label) ->
                            FilterChip(
                                selected = selectedMetric == metric,
                                onClick = { selectedMetric = metric },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("metric_chip_${metric.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Vico Chart Host
                    if (trendGroups.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F111A))
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            if (chartViewType == ChartViewType.LINE_TREND) {
                                CartesianChartHost(
                                    chart = rememberCartesianChart(
                                        rememberLineCartesianLayer(),
                                        startAxis = VerticalAxis.rememberStart(),
                                        bottomAxis = HorizontalAxis.rememberBottom(
                                            valueFormatter = bottomAxisFormatter,
                                            itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned() }
                                        )
                                    ),
                                    modelProducer = chartModelProducer,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("vico_line_chart")
                                )
                            } else {
                                CartesianChartHost(
                                    chart = rememberCartesianChart(
                                        rememberColumnCartesianLayer(),
                                        startAxis = VerticalAxis.rememberStart(),
                                        bottomAxis = HorizontalAxis.rememberBottom(
                                            valueFormatter = bottomAxisFormatter,
                                            itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned() }
                                        )
                                    ),
                                    modelProducer = chartModelProducer,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("vico_column_chart")
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color(0xFF0F111A), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No scan sessions recorded yet. Run a scan or tap 'Load Samples'.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Category & OS Breakdown Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category Distribution Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Device Categories",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        CategoryProgressBar("Servers", serverTotal, totalDevices, Color(0xFF00E5FF))
                        CategoryProgressBar("Workstations", workstationTotal, totalDevices, Color(0xFF7C4DFF))
                        CategoryProgressBar("IoT Devices", iotTotal, totalDevices, Color(0xFFFFB300))
                        CategoryProgressBar("Mobile", mobileTotal, totalDevices, Color(0xFF00E676))
                        CategoryProgressBar("Gateways", gatewayTotal, totalDevices, Color(0xFFFF4081))
                    }
                }

                // Operating System Breakdown Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Operating Systems",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val linuxCount = allDevices.count { it.os.contains("Linux", ignoreCase = true) || it.os.contains("Ubuntu", ignoreCase = true) || it.os.contains("Debian", ignoreCase = true) }
                        val windowsCount = allDevices.count { it.os.contains("Windows", ignoreCase = true) }
                        val rtosCount = allDevices.count { it.os.contains("RTOS", ignoreCase = true) || it.os.contains("OpenWrt", ignoreCase = true) || it.os.contains("ESP32", ignoreCase = true) }
                        val androidCount = allDevices.count { it.os.contains("Android", ignoreCase = true) }
                        val macosCount = allDevices.count { it.os.contains("macOS", ignoreCase = true) }

                        CategoryProgressBar("Linux / UNIX", linuxCount, totalDevices, Color(0xFF29B6F6))
                        CategoryProgressBar("Windows", windowsCount, totalDevices, Color(0xFF0288D1))
                        CategoryProgressBar("Embedded RTOS", rtosCount, totalDevices, Color(0xFFFF7043))
                        CategoryProgressBar("Android", androidCount, totalDevices, Color(0xFF66BB6A))
                        CategoryProgressBar("macOS", macosCount, totalDevices, Color(0xFFAB47BC))
                    }
                }
            }
        }

        // 4. Device Inventory Search & Status Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Discovered Devices Inventory (${filteredDevices.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Status filter chips
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "Vulnerable", "Secured", "Active").forEach { status ->
                            FilterChip(
                                selected = statusFilter == status,
                                onClick = { statusFilter = status },
                                label = { Text(status, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (status) {
                                        "Vulnerable" -> Color(0xFFFF5252).copy(alpha = 0.2f)
                                        "Secured" -> Color(0xFF00E676).copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    },
                                    selectedLabelColor = when (status) {
                                        "Vulnerable" -> Color(0xFFFF5252)
                                        "Secured" -> Color(0xFF00E676)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                ),
                                modifier = Modifier.testTag("status_filter_$status")
                            )
                        }
                    }
                }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter by IP, MAC, Hostname, or OS...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_search_input")
                )
            }
        }

        // 5. Device Items List
        if (filteredDevices.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (allDevices.isEmpty()) "No devices found in database. Run a scan or click 'Load Samples' at the top." else "No devices matching filter criteria.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredDevices) { device ->
                DeviceInventoryItem(
                    device = device,
                    onRunSqlForDevice = { ip ->
                        onNavigateToSql("SELECT * FROM devices WHERE ipAddress = '$ip';")
                    }
                )
            }
        }
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategoryProgressBar(
    name: String,
    count: Int,
    total: Int,
    color: Color
) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    val percent = (fraction * 100).toInt()

    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "$count ($percent%)", fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun DeviceInventoryItem(
    device: DeviceRecord,
    onRunSqlForDevice: (String) -> Unit
) {
    val statusColor = when (device.status.lowercase()) {
        "vulnerable" -> Color(0xFFFF5252)
        "secured" -> Color(0xFF00E676)
        else -> Color(0xFF00E5FF)
    }

    val typeIcon: ImageVector = when {
        device.deviceType.contains("Server", ignoreCase = true) -> Icons.Default.Share
        device.deviceType.contains("Workstation", ignoreCase = true) -> Icons.Default.Home
        device.deviceType.contains("Mobile", ignoreCase = true) -> Icons.Default.Phone
        device.deviceType.contains("IoT", ignoreCase = true) -> Icons.Default.Place
        else -> Icons.Default.Settings
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .testTag("device_card_${device.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = device.deviceType,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = device.hostname,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = device.status.uppercase(),
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.ipAddress,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${device.macAddress}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${device.deviceType} • ${device.os}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick SQL Query Icon
            IconButton(
                onClick = { onRunSqlForDevice(device.ipAddress) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Query this device",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
