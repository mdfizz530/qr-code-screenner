package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.QrDatabase
import com.example.data.QrItem
import com.example.data.QrRepository
import com.example.scanner.QrCodeAnalyzer
import com.example.ui.theme.MyApplicationTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// Color Palette for Slate / Cyan Modern Aesthetic
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate400 = Color(0xFF94A3B8)
val Slate100 = Color(0xFFF1F5F9)
val Cyan400 = Color(0xFF38BDF8)
val Emerald400 = Color(0xFF34D399)
val Orange400 = Color(0xFFFB923C)
val Purple400 = Color(0xFFC084FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = QrDatabase.getDatabase(applicationContext)
        val repository = QrRepository(database.qrDao())
        val viewModelFactory = QrScannerViewModelFactory(repository)

        setContent {
            MyApplicationTheme(darkTheme = true) { // Always modern dark mode
                val viewModel: QrScannerViewModel = ViewModelProvider(this, viewModelFactory)[QrScannerViewModel::class.java]
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Slate900
                ) {
                    QrAppMainScreen(viewModel)
                }
            }
        }
    }
}

// ViewModel Factory
class QrScannerViewModelFactory(private val repository: QrRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QrScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QrScannerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Navigation Tabs
enum class ScanTab(val title: String, val iconFilled: ImageVector, val iconOutlined: ImageVector) {
    SCAN("Scan", Icons.Default.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    CREATE("Create", Icons.Default.AddBox, Icons.Outlined.AddBox),
    HISTORY("History", Icons.Default.History, Icons.Outlined.History)
}

// ViewModel
class QrScannerViewModel(private val repository: QrRepository) : ViewModel() {

    private val _currentTab = MutableStateFlow(ScanTab.SCAN)
    val currentTab: StateFlow<ScanTab> = _currentTab.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _scannedResult = MutableStateFlow<QrItem?>(null)
    val scannedResult: StateFlow<QrItem?> = _scannedResult.asStateFlow()

    private val _generatedBitmap = MutableStateFlow<Bitmap?>(null)
    val generatedBitmap: StateFlow<Bitmap?> = _generatedBitmap.asStateFlow()

    private val _generatedContent = MutableStateFlow<String?>(null)
    val generatedContent: StateFlow<String?> = _generatedContent.asStateFlow()

    private val _activeDetailItem = MutableStateFlow<QrItem?>(null)
    val activeDetailItem: StateFlow<QrItem?> = _activeDetailItem.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _historyFilter = MutableStateFlow("ALL") // "ALL", "SCANNED", "GENERATED"
    val historyFilter: StateFlow<String> = _historyFilter.asStateFlow()

    // Scanned items ignore duplicates scanning too quickly
    private var lastScannedText = ""
    private var lastScannedTime = 0L

    val historyItems: StateFlow<List<QrItem>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredHistory: StateFlow<List<QrItem>> = combine(
        repository.allHistory,
        _searchQuery,
        _historyFilter
    ) { history, search, filter ->
        history.filter { item ->
            val matchesSearch = item.content.contains(search, ignoreCase = true) ||
                    item.type.contains(search, ignoreCase = true)
            val matchesFilter = when (filter) {
                "SCANNED" -> !item.isGenerated
                "GENERATED" -> item.isGenerated
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectTab(tab: ScanTab) {
        _currentTab.value = tab
    }

    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    fun setFlash(on: Boolean) {
        _isFlashOn.value = on
    }

    fun onQrScanned(rawText: String) {
        val now = System.currentTimeMillis()
        if (rawText == lastScannedText && now - lastScannedTime < 2500) {
            // Cooldown of 2.5 seconds for duplicate scans
            return
        }
        lastScannedText = rawText
        lastScannedTime = now

        viewModelScope.launch {
            val type = detectQrType(rawText)
            val newItem = QrItem(
                content = rawText,
                type = type,
                isGenerated = false,
                timestamp = now
            )
            val id = repository.insert(newItem)
            _scannedResult.value = newItem.copy(id = id)
        }
    }

    fun clearScannedResult() {
        _scannedResult.value = null
    }

    fun generateQr(content: String, type: String) {
        viewModelScope.launch {
            val bitmap = generateStyledQrCode(content)
            _generatedBitmap.value = bitmap
            _generatedContent.value = content

            val newItem = QrItem(
                content = content,
                type = type,
                isGenerated = true,
                timestamp = System.currentTimeMillis()
            )
            repository.insert(newItem)
        }
    }

    fun clearGeneratedQr() {
        _generatedBitmap.value = null
        _generatedContent.value = null
    }

    fun viewItemDetails(item: QrItem?) {
        _activeDetailItem.value = item
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateHistoryFilter(filter: String) {
        _historyFilter.value = filter
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            if (_activeDetailItem.value?.id == id) {
                _activeDetailItem.value = null
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
            _activeDetailItem.value = null
        }
    }

    private fun detectQrType(content: String): String {
        return when {
            content.startsWith("http://", ignoreCase = true) ||
            content.startsWith("https://", ignoreCase = true) ||
            content.startsWith("www.", ignoreCase = true) -> "URL"
            content.startsWith("WIFI:", ignoreCase = true) -> "WIFI"
            content.startsWith("BEGIN:VCARD", ignoreCase = true) -> "CONTACT"
            content.startsWith("SMSTO:", ignoreCase = true) -> "SMS"
            content.startsWith("tel:", ignoreCase = true) -> "PHONE"
            else -> "TEXT"
        }
    }

    private fun generateStyledQrCode(content: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Slate blue gradient foreground representation
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val isPixelBlack = bitMatrix.get(x, y)
                    val pixelColor = if (isPixelBlack) {
                        // Use cyan/emerald styled color scheme gradient
                        if (x + y < size) AndroidColor.parseColor("#0ea5e9") else AndroidColor.parseColor("#10b981")
                    } else {
                        AndroidColor.WHITE
                    }
                    bitmap.setPixel(x, y, pixelColor)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// Sealed model for parsed representation
sealed class QrParsedData {
    data class Url(val url: String) : QrParsedData()
    data class Wifi(val ssid: String, val pass: String, val type: String) : QrParsedData()
    data class Contact(val name: String, val phone: String, val email: String, val org: String) : QrParsedData()
    data class Sms(val phone: String, val body: String) : QrParsedData()
    data class Phone(val phone: String) : QrParsedData()
    data class Text(val text: String) : QrParsedData()
}

fun parseQrRawContent(content: String): QrParsedData {
    return when {
        content.startsWith("http://", ignoreCase = true) ||
        content.startsWith("https://", ignoreCase = true) ||
        content.startsWith("www.", ignoreCase = true) -> {
            val url = if (content.startsWith("www.", ignoreCase = true)) "https://$content" else content
            QrParsedData.Url(url)
        }
        content.startsWith("WIFI:", ignoreCase = true) -> {
            val ssid = extractField(content, "S:")
            val pass = extractField(content, "P:")
            val type = extractField(content, "T:")
            QrParsedData.Wifi(ssid, pass, type)
        }
        content.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
            val name = extractVCard(content, "FN:") ?: extractVCard(content, "N:") ?: "Unknown"
            val phone = extractVCard(content, "TEL:") ?: ""
            val email = extractVCard(content, "EMAIL:") ?: ""
            val org = extractVCard(content, "ORG:") ?: ""
            QrParsedData.Contact(name, phone, email, org)
        }
        content.startsWith("SMSTO:", ignoreCase = true) -> {
            val rawClean = content.removePrefix("SMSTO:")
            val parts = rawClean.split(":", limit = 2)
            val phone = parts.getOrNull(0) ?: ""
            val body = parts.getOrNull(1) ?: ""
            QrParsedData.Sms(phone, body)
        }
        content.startsWith("tel:", ignoreCase = true) -> {
            QrParsedData.Phone(content.removePrefix("tel:"))
        }
        else -> QrParsedData.Text(content)
    }
}

private fun extractField(content: String, key: String): String {
    val regex = "${key}(.*?)(?=;|$)".toRegex()
    return regex.find(content)?.groupValues?.getOrNull(1) ?: ""
}

private fun extractVCard(content: String, key: String): String? {
    val lines = content.lines()
    for (line in lines) {
        if (line.startsWith(key, ignoreCase = true)) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) return parts[1].trim()
        } else if (line.contains(";") && line.substringBefore(";").startsWith(key.removeSuffix(":"), ignoreCase = true)) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) return parts[1].trim()
        }
    }
    return null
}

// MAIN UI FRAME
@Composable
fun QrAppMainScreen(viewModel: QrScannerViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val activeDetailItem by viewModel.activeDetailItem.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Slate800,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                ScanTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.iconFilled else tab.iconOutlined,
                                contentDescription = tab.title,
                                tint = if (isSelected) Cyan400 else Slate400
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                color = if (isSelected) Slate100 else Slate400,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Slate700
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        containerColor = Slate900
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Animated screen transition
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "screen_transition"
            ) { targetTab ->
                when (targetTab) {
                    ScanTab.SCAN -> ScanScreen(viewModel)
                    ScanTab.CREATE -> CreateScreen(viewModel)
                    ScanTab.HISTORY -> HistoryScreen(viewModel)
                }
            }

            // Detail Bottom Sheet View Overlay (Dialog styled)
            if (activeDetailItem != null) {
                DetailItemDialog(
                    item = activeDetailItem!!,
                    onDismiss = { viewModel.viewItemDetails(null) },
                    onDelete = {
                        viewModel.deleteHistoryItem(activeDetailItem!!.id)
                        viewModel.viewItemDetails(null)
                    }
                )
            }
        }
    }
}

// SCAN SCREEN
@Composable
fun ScanScreen(viewModel: QrScannerViewModel) {
    val context = LocalContext.current
    var hasCameraPermission by rememberSaveable { mutableStateOf(false) }
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val scannedResult by viewModel.scannedResult.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to scan codes", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onQrScanned = { rawText ->
                    viewModel.onQrScanned(rawText)
                },
                isFlashOn = isFlashOn
            )

            // Scanning Framing Target Canvas
            ScanningOverlayView(isScanning = scannedResult == null)

            // Flash & Controls overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Flash button
                FloatingActionButton(
                    onClick = { viewModel.toggleFlash() },
                    containerColor = Slate800.copy(alpha = 0.85f),
                    contentColor = if (isFlashOn) Cyan400 else Slate100,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp)
                        .testTag("flash_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Torch"
                    )
                }

                // Header hint
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Cyan400, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Align code inside the frame",
                            color = Slate100,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom popup results sheet
            if (scannedResult != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = true) { /* Consume taps */ },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .windowInsetsPadding(WindowInsets.safeDrawing),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp, 4.dp)
                                    .clip(CircleShape)
                                    .background(Slate700)
                                    .align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val typeIcon = getIconForType(scannedResult!!.type)
                                val typeColor = getColorForType(scannedResult!!.type)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(typeColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = typeIcon,
                                        contentDescription = null,
                                        tint = typeColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Scanned Successfully",
                                        color = Emerald400,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Type: ${scannedResult!!.type}",
                                        color = Slate400,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Slate900),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = scannedResult!!.content,
                                    color = Slate100,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(16.dp),
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Action buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.clearScannedResult() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                                    border = BorderStroke(1.dp, Slate700)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Again")
                                }

                                Button(
                                    onClick = {
                                        viewModel.viewItemDetails(scannedResult)
                                        viewModel.clearScannedResult()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan400)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Details")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // No camera permission screen representation
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Slate800),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Camera Access Required",
                    color = Slate100,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "We need camera permission so that you can scan QR codes in real-time.",
                    color = Slate400,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan400),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("request_camera_permission_button")
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Scanning viewfinder overlay drawing corners & horizontal laser scan
@Composable
fun ScanningOverlayView(isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserOffset"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val boxSize = 270.dp.toPx()

        val left = (width - boxSize) / 2
        val top = (height - boxSize) / 2
        val right = left + boxSize
        val bottom = top + boxSize

        // Draw overlay shadow scrim
        val scrimColor = Color.Black.copy(alpha = 0.65f)
        drawRect(color = scrimColor, size = Size(width, top))
        drawRect(color = scrimColor, topLeft = Offset(0f, bottom), size = Size(width, height - bottom))
        drawRect(color = scrimColor, topLeft = Offset(0f, top), size = Size(left, boxSize))
        drawRect(color = scrimColor, topLeft = Offset(right, top), size = Size(width - right, boxSize))

        // Draw transparent viewport frame outline
        drawRoundRect(
            color = Slate400.copy(alpha = 0.3f),
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(16.dp.toPx())
        )

        // Draw accent corner brackets
        val strokeWidth = 4.dp.toPx()
        val cornerLen = 24.dp.toPx()
        val accentColor = Cyan400

        // Top Left
        drawLine(color = accentColor, start = Offset(left, top), end = Offset(left + cornerLen, top), strokeWidth = strokeWidth)
        drawLine(color = accentColor, start = Offset(left, top), end = Offset(left, top + cornerLen), strokeWidth = strokeWidth)

        // Top Right
        drawLine(color = accentColor, start = Offset(right, top), end = Offset(right - cornerLen, top), strokeWidth = strokeWidth)
        drawLine(color = accentColor, start = Offset(right, top), end = Offset(right, top + cornerLen), strokeWidth = strokeWidth)

        // Bottom Left
        drawLine(color = accentColor, start = Offset(left, bottom), end = Offset(left + cornerLen, bottom), strokeWidth = strokeWidth)
        drawLine(color = accentColor, start = Offset(left, bottom), end = Offset(left, bottom - cornerLen), strokeWidth = strokeWidth)

        // Bottom Right
        drawLine(color = accentColor, start = Offset(right, bottom), end = Offset(right - cornerLen, bottom), strokeWidth = strokeWidth)
        drawLine(color = accentColor, start = Offset(right, bottom), end = Offset(right, bottom - cornerLen), strokeWidth = strokeWidth)

        // Animated laser scanning line
        if (isScanning) {
            val laserY = top + (boxSize * laserOffset)
            drawLine(
                color = Emerald400,
                start = Offset(left + 8.dp.toPx(), laserY),
                end = Offset(right - 8.dp.toPx(), laserY),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

// Embedded Camera Preview
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit,
    isFlashOn: Boolean,
    onCameraBind: (androidx.camera.core.Camera) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(isFlashOn) {
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, QrCodeAnalyzer { result ->
                            onQrScanned(result)
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    camera = boundCamera
                    onCameraBind(boundCamera)
                    boundCamera.cameraControl.enableTorch(isFlashOn)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        },
        modifier = modifier
    )
}

// CREATE SCREEN
@Composable
fun CreateScreen(viewModel: QrScannerViewModel) {
    var selectedCategory by remember { mutableStateOf("URL") } // "URL", "TEXT", "WIFI", "CONTACT", "SMS"
    val generatedBitmap by viewModel.generatedBitmap.collectAsStateWithLifecycle()
    val generatedContent by viewModel.generatedContent.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Fields
    var textInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var wifiSec by remember { mutableStateOf("WPA") }
    var wifiPassVisible by remember { mutableStateOf(false) }

    // vCard Fields
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var contactOrg by remember { mutableStateOf("") }

    // SMS Fields
    var smsPhone by remember { mutableStateOf("") }
    var smsBody by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Generate QR Code",
                color = Slate100,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Start
            )
        }

        // Category pickers grid
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("URL", Icons.Default.Language, Cyan400),
                    Triple("Text", Icons.Default.Notes, Slate400),
                    Triple("Wi-Fi", Icons.Default.Wifi, Emerald400),
                    Triple("Contact", Icons.Default.Person, Orange400),
                    Triple("SMS", Icons.AutoMirrored.Filled.Send, Purple400)
                ).forEach { (cat, icon, color) ->
                    val isSelected = selectedCategory == cat.uppercase()
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Slate800 else Slate850()
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isSelected) Modifier.border(1.5.dp, color, RoundedCornerShape(12.dp))
                                else Modifier
                            )
                            .clickable {
                                selectedCategory = cat.uppercase()
                                viewModel.clearGeneratedQr()
                            }
                            .testTag("cat_button_${cat.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                            Text(text = cat, color = Slate100, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Animated Form fields
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedCategory) {
                        "URL" -> {
                            Text("Website URL", color = Slate100, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                placeholder = { Text("https://example.com", color = Slate400) },
                                modifier = Modifier.fillMaxWidth().testTag("field_url"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Cyan400) }
                            )
                        }
                        "TEXT" -> {
                            Text("Text Content", color = Slate100, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = { Text("Enter plain text or message", color = Slate400) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .testTag("field_text"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                maxLines = 4
                            )
                        }
                        "WIFI" -> {
                            Text("Wi-Fi Credentials", color = Slate100, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = wifiSsid,
                                onValueChange = { wifiSsid = it },
                                label = { Text("Network Name (SSID)", color = Slate400) },
                                modifier = Modifier.fillMaxWidth().testTag("field_wifi_ssid"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = Emerald400) }
                            )
                            OutlinedTextField(
                                value = wifiPass,
                                onValueChange = { wifiPass = it },
                                label = { Text("Password", color = Slate400) },
                                modifier = Modifier.fillMaxWidth().testTag("field_wifi_pass"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                visualTransformation = if (wifiPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Emerald400) },
                                trailingIcon = {
                                    IconButton(onClick = { wifiPassVisible = !wifiPassVisible }) {
                                        Icon(
                                            imageVector = if (wifiPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Slate400
                                        )
                                    }
                                }
                            )
                            // Security Selectors
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("WPA", "WEP", "nopass").forEach { sec ->
                                    val isSelected = wifiSec == sec
                                    OutlinedButton(
                                        onClick = { wifiSec = sec },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) Emerald400.copy(alpha = 0.15f) else Color.Transparent,
                                            contentColor = if (isSelected) Emerald400 else Slate400
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) Emerald400 else Slate700
                                        )
                                    ) {
                                        Text(if (sec == "nopass") "Open" else sec, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        "CONTACT" -> {
                            Text("vCard Contact Card", color = Slate100, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = contactName,
                                onValueChange = { contactName = it },
                                label = { Text("Full Name", color = Slate400) },
                                modifier = Modifier.fillMaxWidth().testTag("field_contact_name"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Orange400) }
                            )
                            OutlinedTextField(
                                value = contactPhone,
                                onValueChange = { contactPhone = it },
                                label = { Text("Phone Number", color = Slate400) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("field_contact_phone"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Orange400) }
                            )
                            OutlinedTextField(
                                value = contactEmail,
                                onValueChange = { contactEmail = it },
                                label = { Text("Email", color = Slate400) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth().testTag("field_contact_email"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Orange400) }
                            )
                            OutlinedTextField(
                                value = contactOrg,
                                onValueChange = { contactOrg = it },
                                label = { Text("Organization / Company", color = Slate400) },
                                modifier = Modifier.fillMaxWidth().testTag("field_contact_org"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = Orange400) }
                            )
                        }
                        "SMS" -> {
                            Text("SMS Message", color = Slate100, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = smsPhone,
                                onValueChange = { smsPhone = it },
                                label = { Text("Recipient Phone Number", color = Slate400) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("field_sms_phone"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Purple400) }
                            )
                            OutlinedTextField(
                                value = smsBody,
                                onValueChange = { smsBody = it },
                                label = { Text("Message Text", color = Slate400) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .testTag("field_sms_body"),
                                colors = outTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                maxLines = 3
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Generate trigger button
                    Button(
                        onClick = {
                            val (fullContent, isContentValid) = when (selectedCategory) {
                                "URL" -> Pair(urlInput, urlInput.isNotBlank())
                                "TEXT" -> Pair(textInput, textInput.isNotBlank())
                                "WIFI" -> Pair("WIFI:S:$wifiSsid;T:$wifiSec;P:$wifiPass;;", wifiSsid.isNotBlank())
                                "CONTACT" -> {
                                    val vCard = """
                                        BEGIN:VCARD
                                        VERSION:3.0
                                        FN:$contactName
                                        TEL:$contactPhone
                                        EMAIL:$contactEmail
                                        ORG:$contactOrg
                                        END:VCARD
                                    """.trimIndent()
                                    Pair(vCard, contactName.isNotBlank())
                                }
                                "SMS" -> Pair("SMSTO:$smsPhone:$smsBody", smsPhone.isNotBlank())
                                else -> Pair("", false)
                            }

                            if (isContentValid) {
                                viewModel.generateQr(fullContent, selectedCategory)
                            } else {
                                Toast.makeText(context, "Please fill in the required details", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (selectedCategory) {
                                "WIFI" -> Emerald400
                                "CONTACT" -> Orange400
                                "SMS" -> Purple400
                                else -> Cyan400
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_qr_button")
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create QR Code", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        // Generated QR Result display card
        if (generatedBitmap != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .testTag("generated_qr_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Your QR Code",
                            color = Slate100,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        // Render bitmap
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(12.dp)
                        ) {
                            Image(
                                bitmap = generatedBitmap!!.asImageBitmap(),
                                contentDescription = "Generated QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Code details action block
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("QR Content", generatedContent)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied content to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate700)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    try {
                                        val sendIntent: Intent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, generatedContent)
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Share QR Content")
                                        context.startActivity(shareIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to share content", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate700)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Slate850(): Color = Color(0xFF131D31)

@Composable
fun outTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan400,
    unfocusedBorderColor = Slate700,
    focusedTextColor = Slate100,
    unfocusedTextColor = Slate100,
    focusedLabelColor = Cyan400,
    unfocusedLabelColor = Slate400,
    cursorColor = Cyan400
)

// HISTORY SCREEN
@Composable
fun HistoryScreen(viewModel: QrScannerViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val historyItems by viewModel.filteredHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scanner History",
                color = Slate100,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Clear All icon
            if (historyItems.isNotEmpty()) {
                IconButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        Toast.makeText(context, "History cleared successfully", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear all",
                        tint = Slate400
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("Search scans, links, Wi-Fi networks...", color = Slate400, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input"),
            colors = outTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Filter chips (All, Scanned, Generated)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "SCANNED", "GENERATED").forEach { filter ->
                val isSelected = historyFilter == filter
                ElevatedFilterChip(
                    selected = isSelected,
                    onClick = { viewModel.updateHistoryFilter(filter) },
                    label = { Text(filter.capitalize()) },
                    colors = FilterChipDefaults.elevatedFilterChipColors(
                        selectedContainerColor = Slate800,
                        selectedLabelColor = Cyan400,
                        containerColor = Slate900,
                        labelColor = Slate400
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // History items list representation
        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Slate700,
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "No history matches",
                        color = Slate400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Scanned and generated codes will show up here.",
                        color = Slate400,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(historyItems, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = { viewModel.viewItemDetails(item) }
                    )
                }
            }
        }
    }
}

// History row card representation
@Composable
fun HistoryItemCard(item: QrItem, onClick: () -> Unit) {
    val dateString = remember(item.timestamp) {
        val sdf = SimpleDateFormat("MMM d, yyyy - hh:mm a", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("history_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val typeIcon = getIconForType(item.type)
            val typeColor = getColorForType(item.type)

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(typeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.type,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isGenerated) Purple400.copy(alpha = 0.15f) else Cyan400.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (item.isGenerated) "Created" else "Scanned",
                            color = if (item.isGenerated) Purple400 else Cyan400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = getCleanDisplayContent(item.content, item.type),
                    color = Slate100,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateString,
                    color = Slate400,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Slate400,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Helpers for types representation
fun getIconForType(type: String): ImageVector {
    return when (type) {
        "URL" -> Icons.Default.Language
        "WIFI" -> Icons.Default.Wifi
        "CONTACT" -> Icons.Default.Person
        "SMS" -> Icons.AutoMirrored.Filled.Send
        "PHONE" -> Icons.Default.Phone
        else -> Icons.Default.Notes
    }
}

fun getColorForType(type: String): Color {
    return when (type) {
        "URL" -> Cyan400
        "WIFI" -> Emerald400
        "CONTACT" -> Orange400
        "SMS" -> Purple400
        "PHONE" -> Orange400
        else -> Slate400
    }
}

fun getCleanDisplayContent(content: String, type: String): String {
    val parsed = parseQrRawContent(content)
    return when (parsed) {
        is QrParsedData.Url -> parsed.url
        is QrParsedData.Wifi -> "Wi-Fi: ${parsed.ssid}"
        is QrParsedData.Contact -> parsed.name
        is QrParsedData.Sms -> "To: ${parsed.phone} (${parsed.body})"
        is QrParsedData.Phone -> "Phone: ${parsed.phone}"
        is QrParsedData.Text -> parsed.text
    }
}

// DETAIL SHEET / POPUP DIALOG
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailItemDialog(
    item: QrItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val parsed = remember(item.content) { parseQrRawContent(item.content) }
    val typeColor = getColorForType(item.type)
    val typeIcon = getIconForType(item.type)

    // Dynamic QR regeneration inside dialog
    var showQrOverlay by remember { mutableStateOf(false) }
    var regeneratedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.content) {
        // Pre-render styled QR code bitmap
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(item.content, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            regeneratedBitmap = bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Slate400)
            }
        },
        containerColor = Slate800,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(typeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = typeIcon, contentDescription = null, tint = typeColor, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(text = "QR Details", color = Slate100, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = item.type, color = typeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HorizontalDivider(color = Slate700)

                if (showQrOverlay && regeneratedBitmap != null) {
                    // Render regenerated QR representation
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        ) {
                            Image(
                                bitmap = regeneratedBitmap!!.asImageBitmap(),
                                contentDescription = "Regenerated Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Button(
                            onClick = { showQrOverlay = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate700)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back to Details")
                        }
                    }
                } else {
                    // Type custom details layout
                    when (parsed) {
                        is QrParsedData.Url -> {
                            Text(text = "Link Website", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = parsed.url, color = Slate100, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(parsed.url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No web browser found", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan400),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Website", fontWeight = FontWeight.Bold)
                            }
                        }
                        is QrParsedData.Wifi -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DetailField(label = "Network SSID (Name)", value = parsed.ssid)
                                DetailField(label = "Password", value = if (parsed.pass.isBlank()) "(Open Network)" else parsed.pass)
                                DetailField(label = "Security", value = parsed.type)
                            }

                            if (parsed.pass.isNotBlank()) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("WiFi Password", parsed.pass)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Password copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Copy Wi-Fi Password", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is QrParsedData.Contact -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DetailField(label = "Name", value = parsed.name)
                                if (parsed.phone.isNotBlank()) DetailField(label = "Phone", value = parsed.phone)
                                if (parsed.email.isNotBlank()) DetailField(label = "Email", value = parsed.email)
                                if (parsed.org.isNotBlank()) DetailField(label = "Company / Org", value = parsed.org)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (parsed.phone.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${parsed.phone}"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot dial calls", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Orange400),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Call", fontSize = 13.sp)
                                    }
                                }
                                if (parsed.email.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${parsed.email}"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open mail apps", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Orange400),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Email", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        is QrParsedData.Sms -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DetailField(label = "Send To", value = parsed.phone)
                                DetailField(label = "Message Body", value = parsed.body)
                            }

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${parsed.phone}")).apply {
                                            putExtra("sms_body", parsed.body)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot send SMS messages", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple400),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compose SMS", fontWeight = FontWeight.Bold)
                            }
                        }
                        is QrParsedData.Phone -> {
                            DetailField(label = "Phone Number", value = parsed.phone)

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${parsed.phone}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot dial calls", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Orange400),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Call Number", fontWeight = FontWeight.Bold)
                            }
                        }
                        is QrParsedData.Text -> {
                            DetailField(label = "Content Text", value = parsed.text)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Copied Text", parsed.text)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied text to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Text", fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${parsed.text}"))
                                            context.startActivity(searchIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Search Web", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = Slate700)

                    // Secondary action buttons block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showQrOverlay = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Show QR", fontSize = 13.sp)
                        }

                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Log", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun DetailField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Slate100, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
