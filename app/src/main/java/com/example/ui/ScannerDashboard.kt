package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ScanRecord
import com.example.data.network.NetworkScanner
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerDashboard(
    viewModel: ScanViewModel,
    modifier: Modifier = Modifier
) {
    val scans by viewModel.allScans.collectAsState()
    val selectedDetails by viewModel.selectedScanDetails.collectAsState()
    val queryState by viewModel.queryState.collectAsState()
    val currentQuery by viewModel.currentQuery.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Navigation and Tab State
    var activeTab by remember { mutableStateOf(1) } // Default to Device Trends to showcase data visualizer
    val tabs = listOf("Scanner Console", "Device Trends", "SQLite Query Engine", "Academic History")

    // Live scan parameters
    var targetInput by remember { mutableStateOf("swell.house") }
    var isScanning by remember { mutableStateOf(false) }
    val liveConsoleLogs = remember { mutableStateListOf<String>() }
    val consoleListState = rememberLazyListState()

    // Import Raw JSON sheet
    var showImportDialog by remember { mutableStateOf(false) }
    var jsonImportText by remember { mutableStateOf("") }
    var importErrorMsg by remember { mutableStateOf<String?>(null) }

    // Predefined SQL snippets helper
    val sqlSnippets = listOf(
        "SELECT target, duration, datetime(timestamp/1000, 'unixepoch', 'localtime') as Date FROM scans;",
        "SELECT deviceType, os, status, COUNT(*) as count FROM devices GROUP BY deviceType, os, status ORDER BY count DESC;",
        "SELECT datetime(timestamp/1000, 'unixepoch') as audit_time, hostname, ipAddress, deviceType, status FROM devices ORDER BY timestamp DESC;",
        "SELECT s.target, p.port, p.service FROM open_ports p JOIN scans s ON p.scanId = s.id;",
        "SELECT s.target, t.name as Technology FROM technologies t JOIN scans s ON t.scanId = s.id;",
        "SELECT s.target, p.path, p.statusCode FROM discovered_paths p JOIN scans s ON p.scanId = s.id WHERE p.statusCode = 200;",
        "SELECT s.target, v.description FROM vulnerabilities v JOIN scans s ON v.scanId = s.id;"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Doblix logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Doblix Ultimate Scanner",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "v2.1 Academic Lab Environment",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.testTag("import_json_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Import JSON",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.loadSampleAcademicScans()
                        },
                        enabled = !isImporting,
                        modifier = Modifier.testTag("import_samples_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Load Samples",
                            tint = if (isImporting) Color.Gray else MaterialTheme.colorScheme.tertiary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                tabs.forEachIndexed { index, label ->
                    val icon = when (index) {
                        0 -> Icons.Default.PlayArrow
                        1 -> Icons.Default.Share
                        2 -> Icons.Default.Edit
                        else -> Icons.Default.List
                    }
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(icon, contentDescription = label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        ),
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (activeTab) {
                0 -> ScannerConsoleTab(
                    targetInput = targetInput,
                    onTargetChange = { targetInput = it },
                    isScanning = isScanning,
                    logs = liveConsoleLogs,
                    listState = consoleListState,
                    onStartScan = {
                        isScanning = true
                        liveConsoleLogs.clear()
                        liveConsoleLogs.add("🚀 Booting up Doblix Ultimate Scanner Socket Daemon...")
                        scope.launch {
                            try {
                                val jsonResult = NetworkScanner.performScan(targetInput) { log ->
                                    liveConsoleLogs.add(log)
                                    scope.launch {
                                        if (liveConsoleLogs.size > 0) {
                                            consoleListState.animateScrollToItem(liveConsoleLogs.size - 1)
                                        }
                                    }
                                }
                                viewModel.importScanFromJson(jsonResult)
                                liveConsoleLogs.add("💾 Scan successfully written and parsed into Room SQLite Database!")
                            } catch (e: Exception) {
                                liveConsoleLogs.add("❌ Scan aborted: ${e.message}")
                            } finally {
                                isScanning = false
                            }
                        }
                    }
                )
                1 -> DeviceVisualizerTab(
                    viewModel = viewModel,
                    onNavigateToSql = { sql ->
                        viewModel.updateQuery(sql)
                        viewModel.executeQuery(sql)
                        activeTab = 2
                    }
                )
                2 -> SqlQueryTab(
                    queryState = queryState,
                    currentQuery = currentQuery,
                    onQueryChange = { viewModel.updateQuery(it) },
                    onExecute = { viewModel.executeQuery(currentQuery) },
                    snippets = sqlSnippets,
                    clipboardManager = clipboardManager
                )
                3 -> AcademicHistoryTab(
                    scans = scans,
                    selectedDetails = selectedDetails,
                    onSelectScan = { viewModel.selectScan(it) },
                    onClearSelection = { viewModel.clearSelection() },
                    onDeleteScan = { viewModel.deleteScan(it) },
                    onClearAll = { viewModel.clearAllScans() }
                )
            }

            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Parsing & Writing Academic Scans...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Building relations inside local SQLite schema.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // JSON Manual import dialog
            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showImportDialog = false
                        importErrorMsg = null
                    },
                    title = {
                        Text("Import Python Scan Output", color = MaterialTheme.colorScheme.primary)
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Paste the raw JSON report produced by Doblix Scanner. This will be automatically normalized and written to the SQLite database schemas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = jsonImportText,
                                onValueChange = { jsonImportText = it },
                                placeholder = { Text("{ \"target\": \"...\" }") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .testTag("json_import_field"),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            if (importErrorMsg != null) {
                                Text(
                                    text = importErrorMsg!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (jsonImportText.isBlank()) {
                                    importErrorMsg = "Please enter valid JSON content"
                                    return@Button
                                }
                                scope.launch {
                                    try {
                                        viewModel.importScanFromJson(jsonImportText)
                                        showImportDialog = false
                                        jsonImportText = ""
                                        importErrorMsg = null
                                    } catch (e: Exception) {
                                        importErrorMsg = "Failed to parse: ${e.message}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("confirm_import_button")
                        ) {
                            Text("Process & Insert", color = Color.Black)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showImportDialog = false
                            importErrorMsg = null
                        }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

// Tab 1: Live Interactive Console
@Composable
fun ScannerConsoleTab(
    targetInput: String,
    onTargetChange: (String) -> Unit,
    isScanning: Boolean,
    logs: List<String>,
    listState: LazyListState,
    onStartScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Launch Live Audit Daemon",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Executes DNS queries, socket connections, and OWASP header inspections on the targeted domain. Saves structured relational logs into local SQLite database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = onTargetChange,
                        label = { Text("Audit Target Domain") },
                        placeholder = { Text("e.g. swell.house") },
                        enabled = !isScanning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("target_input_field"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (!isScanning) onStartScan() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onStartScan,
                        enabled = !isScanning && targetInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("start_scan_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Scan",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Console Screen Logger
        Text(
            text = "Active Terminal Stream",
            style = MaterialTheme.typography.titleSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            if (logs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Console idle",
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Lab Terminal Ready",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Input a target address above and click run to begin logging.",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        val color = when {
                            log.contains("🟢") || log.contains("✅") -> MaterialTheme.colorScheme.tertiary
                            log.contains("❌") -> MaterialTheme.colorScheme.error
                            log.contains("⚠️") -> Color(0xFFFFB300)
                            else -> MaterialTheme.colorScheme.onBackground
                        }
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = color,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// Tab 2: SQLite Raw Engine
@Composable
fun SqlQueryTab(
    queryState: QueryState,
    currentQuery: String,
    onQueryChange: (String) -> Unit,
    onExecute: () -> Unit,
    snippets: List<String>,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Raw SQLite Query Runner",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Perform arbitrary SQL operations over scans, open_ports, technologies, discovered_paths, emails, and vulnerabilities schemas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Snippets helper scroll row
                Text(
                    "Quick SQL Presets:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    snippets.forEachIndexed { i, snippet ->
                        val shortName = when (i) {
                            0 -> "Show Scans"
                            1 -> "Ports Join"
                            2 -> "Techs Join"
                            3 -> "OK Paths"
                            else -> "Vulns Join"
                        }
                        SuggestionChip(
                            onClick = { onQueryChange(snippet) },
                            label = { Text(shortName, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = currentQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("sql_editor_field"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("SELECT * FROM scans;") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onExecute,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("execute_sql_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Execute",
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Execute SQLite Statement", color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Result table display
        Text(
            "SQL Execution Output",
            style = MaterialTheme.typography.titleSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            when (queryState) {
                is QueryState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Enter custom queries or use presets above to query local databases.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                is QueryState.Executing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is QueryState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Database error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "SQLite Syntax / Runtime Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = (queryState as QueryState.Error).message,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is QueryState.Success -> {
                    val result = (queryState as QueryState.Success).result
                    if (result.errorMessage != null) {
                        Text(
                            "Error: ${result.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    } else if (result.columns.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Statement executed successfully. Affected rows: ${result.affectedRows}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    } else if (result.rows.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Empty set returned. Columns: [${result.columns.joinToString(", ")}]",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    } else {
                        // Render full scrollable grid
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Column headers row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 6.dp)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                result.columns.forEach { colName ->
                                    Text(
                                        text = colName,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .width(130.dp)
                                            .padding(horizontal = 6.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Table data body
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(result.rows) { row ->
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                                .horizontalScroll(rememberScrollState())
                                        ) {
                                            row.forEach { cell ->
                                                Text(
                                                    text = cell,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    modifier = Modifier
                                                        .width(130.dp)
                                                        .padding(horizontal = 6.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tab 3: Academic History & Details inspector
@Composable
fun AcademicHistoryTab(
    scans: List<ScanRecord>,
    selectedDetails: DetailedScan?,
    onSelectScan: (ScanRecord) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteScan: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var showConfirmDeleteAll by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = selectedDetails,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
        },
        label = "HistoryTransition"
    ) { details ->
        if (details != null) {
            ScanDetailsView(
                details = details,
                onBack = onClearSelection,
                onDelete = {
                    onDeleteScan(details.scan.id)
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Stored Scan Datasets",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (scans.isNotEmpty()) {
                        TextButton(
                            onClick = { showConfirmDeleteAll = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear all", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear SQLite DB", fontSize = 12.sp)
                        }
                    }
                }

                if (scans.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "No audits",
                                tint = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Database Tables Empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Start interactive scanning, import logs, or click the reload icon in the top header to populate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(scans) { scan ->
                            ScanHistoryCard(
                                scan = scan,
                                onClick = { onSelectScan(scan) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteAll = false },
            title = { Text("Purge local SQLite DB?") },
            text = { Text("This deletes all records inside scans, open_ports, technologies, discovered_paths, emails, and vulnerabilities schemas.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showConfirmDeleteAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Purge DB", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteAll = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun ScanHistoryCard(
    scan: ScanRecord,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val dateStr = formatter.format(Date(scan.timestamp))

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("scan_card_${scan.id}")
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Scan icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scan.target,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Date: $dateStr | Time: ${scan.duration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Full detailed audit records view
@Composable
fun ScanDetailsView(
    details: DetailedScan,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val dateStr = formatter.format(Date(details.scan.timestamp))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Toolbar actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_to_history")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_scan_item")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Target Info Box
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TARGET HOST REPORT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = details.scan.target,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Database ID: #${details.scan.id} | Timestamp: $dateStr | Audit time: ${details.scan.duration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Open Ports Row
        ExpandableDetailsSection(
            title = "Open TCP Ports (Nmap)",
            count = details.openPorts.size,
            primaryColor = MaterialTheme.colorScheme.primary
        ) {
            if (details.openPorts.isEmpty()) {
                Text("No open TCP ports detected in scans.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                details.openPorts.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Port ${p.port}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            p.service.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Technologies stack (WhatWeb)
        ExpandableDetailsSection(
            title = "Technology Fingerprints (WhatWeb)",
            count = details.technologies.size,
            primaryColor = MaterialTheme.colorScheme.tertiary
        ) {
            if (details.technologies.isEmpty()) {
                Text("No signature fingerprints found.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    details.technologies.forEach { tech ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tech.name, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.tertiary
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Brute Forced Directories (Gobuster)
        ExpandableDetailsSection(
            title = "Directory Index (Gobuster)",
            count = details.paths.size,
            primaryColor = MaterialTheme.colorScheme.primary
        ) {
            if (details.paths.isEmpty()) {
                Text("No directories discovered during lookups.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                details.paths.forEach { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            path.path,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row {
                            Text(
                                "HTTP ${path.statusCode}",
                                fontFamily = FontFamily.Monospace,
                                color = if (path.statusCode == 200) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "${path.size}B",
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Security vulnerability advisories (Nikto)
        ExpandableDetailsSection(
            title = "Vulnerability Advisories (Nikto)",
            count = details.vulnerabilities.size,
            primaryColor = MaterialTheme.colorScheme.error
        ) {
            if (details.vulnerabilities.isEmpty()) {
                Text("No urgent warnings found. Security headers are securely set.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            } else {
                details.vulnerabilities.forEach { v ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Vuln icon",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.CenterVertically)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = v.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Emails Scraped (theHarvester)
        ExpandableDetailsSection(
            title = "Harvested Contacts (theHarvester)",
            count = details.emails.size,
            primaryColor = MaterialTheme.colorScheme.onBackground
        ) {
            if (details.emails.isEmpty()) {
                Text("No public email signatures parsed.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                details.emails.forEach { e ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Email",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = e.email,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Network Devices Discovered
        ExpandableDetailsSection(
            title = "Discovered Network Devices (SQLite)",
            count = details.devices.size,
            primaryColor = Color(0xFF00E5FF)
        ) {
            if (details.devices.isEmpty()) {
                Text("No devices mapped to this scan checkpoint.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                details.devices.forEach { dev ->
                    DeviceInventoryItem(
                        device = dev,
                        onRunSqlForDevice = {}
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
fun ExpandableDetailsSection(
    title: String,
    count: Int,
    primaryColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = primaryColor
                )
                Badge(
                    containerColor = primaryColor.copy(alpha = 0.2f),
                    contentColor = primaryColor
                ) {
                    Text(
                        "$count records",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

// FlowRow wrapper for technologies display
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val layoutWidth = constraints.maxWidth
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeables.forEach { placeable ->
            if (currentRowWidth + placeable.width > layoutWidth) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + 12 // Spacing placeholder
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        var totalHeight = 0
        rows.forEach { row ->
            val maxHeight = row.maxOfOrNull { it.height } ?: 0
            totalHeight += maxHeight + 12
        }

        layout(layoutWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val maxHeight = row.maxOfOrNull { it.height } ?: 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + 12
                }
                y += maxHeight + 12
            }
        }
    }
}
