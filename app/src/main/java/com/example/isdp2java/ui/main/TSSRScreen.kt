package com.example.isdp2java.ui.main

import android.Manifest
import android.content.Context
import android.graphics.*
import android.location.Geocoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import coil.compose.rememberAsyncImagePainter
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import org.apache.poi.xwpf.usermodel.*
import org.apache.poi.util.Units

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TSSRScreen(initialFolder: String? = null, initialTelco: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var siteName by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf("") }
    var lng by rememberSaveable { mutableStateOf("") }
    var fullAddress by rememberSaveable { mutableStateOf("Detecting location...") }
    var vehicleAccess by rememberSaveable { mutableStateOf("4 Wheeled") }
    var otherVehicleAccess by rememberSaveable { mutableStateOf("") }
    var telcoName by rememberSaveable { mutableStateOf(initialTelco ?: "Globe") }
    var wifiTelcoName by rememberSaveable { mutableStateOf("") }
    
    val sessionTimestamp = rememberSaveable { SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date()) }
    val sectionImages = remember { mutableStateMapOf<String, Uri?>() }

    var viewerUri by remember { mutableStateOf<Uri?>(null) }
    var viewerFieldKey by remember { mutableStateOf<String?>(null) }
    var viewerFieldSection by remember { mutableStateOf<String?>(null) }
    var viewerFieldName by remember { mutableStateOf<String?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var customFolderName by rememberSaveable { mutableStateOf<String?>(initialFolder) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSelectFolderDialog by remember { mutableStateOf(false) }
    var existingFolders by remember { mutableStateOf<List<String>>(emptyList()) }

    var showReportOptions by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (initialFolder != null) return
                result.lastLocation?.let { location ->
                    if (lat.isEmpty() || lng.isEmpty()) {
                        lat = location.latitude.toString()
                        lng = location.longitude.toString()
                        scope.launch {
                            fullAddress = withContext(Dispatchers.IO) {
                                getAddressFromLocation(context, location.latitude, location.longitude)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(initialFolder) {
        if (initialFolder != null) {
            val tssrDir = getSiteFolder(context, "", "", initialFolder)
            if (tssrDir != null && tssrDir.exists()) {
                withContext(Dispatchers.IO) {
                    val metadataFile = File(tssrDir, "metadata.properties")
                    if (metadataFile.exists()) {
                        val props = Properties()
                        try {
                            metadataFile.inputStream().use { props.load(it) }
                            withContext(Dispatchers.Main) {
                                siteName = props.getProperty("siteName", "")
                                lat = props.getProperty("lat", "")
                                lng = props.getProperty("lng", "")
                                fullAddress = props.getProperty("fullAddress", "")
                                vehicleAccess = props.getProperty("vehicleAccess", "4 Wheeled")
                                otherVehicleAccess = props.getProperty("otherVehicleAccess", "")
                                if (initialTelco == null || initialTelco == "Wifi") {
                                    telcoName = props.getProperty("telcoName", telcoName)
                                }
                                wifiTelcoName = props.getProperty("wifiTelcoName", "")
                                
                                props.stringPropertyNames().filter { it.startsWith("img_") }.forEach { propKey ->
                                    val key = propKey.substring(4)
                                    val relPath = props.getProperty(propKey)
                                    val imgFile = File(tssrDir, relPath)
                                    if (imgFile.exists()) {
                                        sectionImages[key] = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", imgFile)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TSSR", "Failed to load metadata", e)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            siteName = initialFolder.substringBeforeLast("_")
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(siteName, lat, lng, fullAddress, vehicleAccess, otherVehicleAccess, telcoName, wifiTelcoName, sectionImages.size) {
        if (siteName.isNotBlank() || customFolderName != null) {
            val folder = getSiteFolder(context, siteName, sessionTimestamp, customFolderName)
            if (folder != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val metadataFile = File(folder, "metadata.properties")
                        val props = Properties()
                        props.setProperty("siteName", siteName)
                        props.setProperty("lat", lat)
                        props.setProperty("lng", lng)
                        props.setProperty("fullAddress", fullAddress)
                        props.setProperty("vehicleAccess", vehicleAccess)
                        props.setProperty("otherVehicleAccess", otherVehicleAccess)
                        props.setProperty("telcoName", telcoName)
                        props.setProperty("wifiTelcoName", wifiTelcoName)
                        
                        sectionImages.forEach { (key, uri) ->
                            try {
                                val fileName = uri?.lastPathSegment ?: ""
                                folder.listFiles { f -> f.isDirectory }?.forEach { sectionDir ->
                                    val imgFile = File(sectionDir, fileName)
                                    if (imgFile.exists()) {
                                        props.setProperty("img_$key", "${sectionDir.name}/$fileName")
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                        metadataFile.outputStream().use { props.store(it, "TSSR Metadata") }
                    } catch (e: Exception) {
                        Log.e("TSSR", "Metadata save failed", e)
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
        
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (initialFolder == null && location != null) {
                    if (lat.isEmpty()) lat = location.latitude.toString()
                    if (lng.isEmpty()) lng = location.longitude.toString()
                    scope.launch {
                        if (fullAddress == "Detecting location...") {
                            fullAddress = withContext(Dispatchers.IO) {
                                getAddressFromLocation(context, location.latitude, location.longitude)
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {}

        Configuration.getInstance().load(context, context.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {}
    }

    DisposableEffect(Unit) {
        onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    val sections = remember {
        listOf(
            TSSRSectionData("A3.1 Site Location", listOf("Vicinity Map", "GPS Reading", "Antenna Blockage")),
            TSSRSectionData("A3.2 Site & Tower", listOf("Site and Tower", "Defect Report", "Access Road", "Entrance Obstacle 1", "Entrance Obstacle 2", "Stairs", "Door/Gate", "Front View")),
            TSSRSectionData("Panoramic Antenna", (0..330 step 30).map { "${it}° Panoramic" }),
            TSSRSectionData("Sector Antennas", listOf("Sector 1", "Sector 2", "Sector 3").flatMap { s -> listOf("$s Coverage", "$s Ports", "$s AMB", "$s RRU Location", "$s Overview") }),
            TSSRSectionData("Microwave Photos", (1..20).map { "MW $it" }),
            TSSRSectionData("Outdoor Panoramic", listOf("Rear to Entrance", "Left to Right", "Front to Rear", "Right to Left")),
            TSSRSectionData("Tower Top Views", listOf("Top to Entrance", "Top to Right", "Top to Rear", "Top to Left")),
            TSSRSectionData("Cabin Photos", (1..7).map { "Cabin1 Gen $it" } + (1..6).map { "Cabin2 Gen $it" }),
            TSSRSectionData("Basepad Photos", (1..8).map { "Basepad $it" }),
            TSSRSectionData("Wireless Equipment", listOf("BBU1", "BBU2", "BBU3")),
            TSSRSectionData("Transport Equipment", listOf("Bayface Layout", "GE/FE Ports")),
            TSSRSectionData("ODF / OSP", listOf("ODF Bayface", "ODF Ports", "ODF Cabinet")),
            TSSRSectionData("Proposed Installation", listOf("Proposed BBU", "Proposed DCDU", "Proposed Cabinet"))
        )
    }

    var activeFieldKey by rememberSaveable { mutableStateOf<String?>(null) }
    var activeFieldSection by rememberSaveable { mutableStateOf<String?>(null) }
    var activeFieldName by rememberSaveable { mutableStateOf<String?>(null) }
    var tempPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var tempFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempPhotoUriString != null && activeFieldKey != null) {
            val originalUri = Uri.parse(tempPhotoUriString)
            scope.launch {
                val overlaidUri = withContext(Dispatchers.IO) {
                    addOverlayToImage(context, originalUri, siteName, lat, lng, fullAddress)
                }
                overlaidUri?.let {
                    sectionImages[activeFieldKey!!] = it
                    viewerUri = it
                    viewerFieldKey = activeFieldKey
                    viewerFieldSection = activeFieldSection
                    viewerFieldName = activeFieldName
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { sourceUri ->
            val key = activeFieldKey ?: viewerFieldKey ?: return@let
            val section = activeFieldSection ?: viewerFieldSection ?: "General"
            val fieldName = activeFieldName ?: viewerFieldName ?: "Photo"
            
            scope.launch {
                val destFile = withContext(Dispatchers.IO) {
                    createTSSRFile(context, siteName, section, fieldName, sessionTimestamp, customFolderName)
                }
                destFile?.let { file ->
                    val destUri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(sourceUri)?.use { input ->
                            context.contentResolver.openOutputStream(destUri)?.use { output -> input.copyTo(output) }
                        }
                    }
                    val overlaidUri = withContext(Dispatchers.IO) {
                        addOverlayToImage(context, destUri, siteName, lat, lng, fullAddress)
                    }
                    overlaidUri?.let {
                        sectionImages[key] = it
                        viewerUri = it
                        viewerFieldKey = key
                        viewerFieldSection = section
                        viewerFieldName = fieldName
                        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                    }
                }
            }
        }
    }

    var mapRef by remember { mutableStateOf<MapView?>(null) }

    fun captureMapSnapshot() {
        val map = mapRef ?: return
        val key = "A3.1 Site Location_Vicinity Map"
        val section = "A3.1 Site Location"
        val fieldName = "Vicinity Map"
        
        scope.launch {
            val destFile = withContext(Dispatchers.IO) {
                createTSSRFile(context, siteName, section, fieldName, sessionTimestamp, customFolderName)
            }
            destFile?.let { file ->
                val bitmap = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                map.draw(canvas)
                
                withContext(Dispatchers.IO) {
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                }
                val uri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                val overlaidUri = withContext(Dispatchers.IO) {
                    addOverlayToImage(context, uri, siteName, lat, lng, fullAddress)
                }
                overlaidUri?.let {
                    sectionImages[key] = it
                    viewerUri = it
                    viewerFieldKey = key
                    viewerFieldSection = section
                    viewerFieldName = fieldName
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                }
                bitmap.recycle()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Text("TSSR Settings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider()
                    
                    Column(modifier = Modifier.weight(1f)) {
                        NavigationDrawerItem(
                            label = { Text("Select Existing Folder") },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    val folders = withContext(Dispatchers.IO) {
                                        val appDir = File(context.getExternalFilesDir(null), "SMS_ISDP")
                                        if (appDir.exists()) {
                                            appDir.listFiles { file -> file.isDirectory }?.map { it.name } ?: emptyList()
                                        } else emptyList()
                                    }
                                    existingFolders = folders
                                    drawerState.close()
                                    showSelectFolderDialog = true
                                }
                            },
                            icon = { Icon(Icons.Default.Folder, null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        NavigationDrawerItem(
                            label = { Text("Create/Rename Folder") },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                showCreateFolderDialog = true
                            },
                            icon = { Icon(Icons.Default.CreateNewFolder, null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        
                        if (customFolderName != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            NavigationDrawerItem(
                                label = { Text("Reset to Default Folder") },
                                selected = false,
                                onClick = {
                                    customFolderName = null
                                    scope.launch { drawerState.close() }
                                    Toast.makeText(context, "Reset to default naming", Toast.LENGTH_SHORT).show()
                                },
                                icon = { Icon(Icons.Default.RestartAlt, null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("TSSR Report", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (customFolderName != null) {
                                Text("Folder: $customFolderName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Site Information", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = siteName, onValueChange = { siteName = it }, label = { Text("Site Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            if (telcoName == "Wifi") {
                                OutlinedTextField(value = wifiTelcoName, onValueChange = { wifiTelcoName = it }, label = { Text("Telco Name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            } else {
                                Text("Telco: $telcoName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Lng") }, modifier = Modifier.weight(1f))
                            }
                            Text(text = fullAddress, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                            
                            Divider(modifier = Modifier.padding(vertical = 16.dp))
                            Text("Vehicle Access", fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = vehicleAccess == "2 Wheeled", onClick = { vehicleAccess = "2 Wheeled" })
                                Text("2 Wheeled", fontSize = 14.sp)
                                RadioButton(selected = vehicleAccess == "4 Wheeled", onClick = { vehicleAccess = "4 Wheeled" })
                                Text("4 Wheeled", fontSize = 14.sp)
                                RadioButton(selected = vehicleAccess == "Other", onClick = { vehicleAccess = "Other" })
                                Text("Other", fontSize = 14.sp)
                            }
                            if (vehicleAccess == "Other") {
                                OutlinedTextField(value = otherVehicleAccess, onValueChange = { otherVehicleAccess = it }, label = { Text("Specify") }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                items(sections) { section ->
                    CollapsibleTSSRSection(
                        sectionData = section,
                        siteName = siteName,
                        sessionTimestamp = sessionTimestamp,
                        lat = lat,
                        lng = lng,
                        address = fullAddress,
                        images = sectionImages,
                        onStartCamera = { key, fieldName ->
                            if (siteName.isBlank() && customFolderName.isNullOrBlank()) {
                                Toast.makeText(context, "Please enter Site Name", Toast.LENGTH_SHORT).show()
                            } else {
                                activeFieldKey = key
                                activeFieldSection = section.title
                                activeFieldName = fieldName
                                createTSSRFile(context, siteName, section.title, fieldName, sessionTimestamp, customFolderName)?.let { file ->
                                    val uri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                                    tempPhotoUriString = uri.toString()
                                    tempFilePath = file.absolutePath
                                    cameraLauncher.launch(uri)
                                }
                            }
                        },
                        onStartGallery = { key, fieldName ->
                            if (siteName.isBlank() && customFolderName.isNullOrBlank()) {
                                Toast.makeText(context, "Please enter Site Name", Toast.LENGTH_SHORT).show()
                            } else {
                                activeFieldKey = key
                                activeFieldSection = section.title
                                activeFieldName = fieldName
                                galleryLauncher.launch("image/*")
                            }
                        },
                        onImageClick = { key, uri, fieldName ->
                            viewerUri = uri
                            viewerFieldKey = key
                            viewerFieldSection = section.title
                            viewerFieldName = fieldName
                        },
                        onScreenshot = { captureMapSnapshot() },
                        mapRef = { mapRef = it }
                    )
                }
                
                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showReportOptions = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(8.dp))
                        Text("GENERATE REPORT", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(100.dp))
                }
            }
        }
    }

    if (showReportOptions) {
        AlertDialog(
            onDismissRequest = { showReportOptions = false },
            title = { Text("Report Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val finalTelco = if (telcoName == "Wifi") wifiTelcoName else telcoName
                    Text("Telco: $finalTelco")
                    Button(onClick = { 
                        generatePDF(context, siteName, lat, lng, fullAddress, finalTelco, sections, sectionImages, customFolderName, sessionTimestamp)
                        showReportOptions = false 
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save as PDF")
                    }
                    OutlinedButton(onClick = { 
                        generateDocx(context, siteName, lat, lng, fullAddress, finalTelco, sections, sectionImages, customFolderName, sessionTimestamp)
                        showReportOptions = false 
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save as DOCX")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showReportOptions = false }) { Text("Cancel") } }
        )
    }

    if (showCreateFolderDialog) {
        var textValue by remember { mutableStateOf(customFolderName ?: "") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Set/Create Site Folder") },
            text = {
                Column {
                    Text("Images will be saved in SMS_ISDP/[Name]", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        placeholder = { Text("Enter folder name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textValue.isNotBlank()) {
                        customFolderName = textValue
                        // Physically create the folder
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                getSiteFolder(context, siteName, sessionTimestamp, textValue)
                            }
                        }
                        showCreateFolderDialog = false
                        Toast.makeText(context, "Folder set to: $textValue", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSelectFolderDialog) {
        AlertDialog(
            onDismissRequest = { showSelectFolderDialog = false },
            title = { Text("Select Existing Site Folder") },
            text = {
                if (existingFolders.isEmpty()) {
                    Text("No folders found in SMS_ISDP")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(existingFolders) { folder ->
                            ListItem(
                                headlineContent = { Text(folder) },
                                leadingContent = { Icon(Icons.Default.FolderOpen, null) },
                                modifier = Modifier.clickable {
                                    customFolderName = folder
                                    showSelectFolderDialog = false
                                    Toast.makeText(context, "Folder selected: $folder", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSelectFolderDialog = false }) { Text("Close") } }
        )
    }

    if (viewerUri != null) {
        ImagePreviewDialog(
            uri = viewerUri!!,
            onDismiss = { viewerUri = null },
            onRetake = {
                val key = viewerFieldKey ?: ""
                val section = viewerFieldSection ?: ""
                val fieldName = viewerFieldName ?: "Photo"
                if (key.contains("Vicinity Map")) {
                    captureMapSnapshot()
                } else {
                    activeFieldKey = key
                    activeFieldSection = section
                    activeFieldName = fieldName
                    createTSSRFile(context, siteName, section, fieldName, sessionTimestamp, customFolderName)?.let { file ->
                        val uri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                        tempPhotoUriString = uri.toString()
                        tempFilePath = file.absolutePath
                        cameraLauncher.launch(uri)
                    }
                }
                viewerUri = null
            },
            onGallery = {
                activeFieldKey = viewerFieldKey
                activeFieldSection = viewerFieldSection
                activeFieldName = viewerFieldName
                galleryLauncher.launch("image/*")
                viewerUri = null
            },
            isMap = viewerFieldKey?.contains("Vicinity Map") == true
        )
    }
}

private fun getSiteFolder(context: Context, siteName: String, sessionTimestamp: String, customFolderName: String?): File? {
    val appDir = context.getExternalFilesDir(null) ?: return null
    val baseDir = File(appDir, "SMS_ISDP")
    if (!baseDir.exists()) baseDir.mkdirs()
    
    val parentFolderName = if (!customFolderName.isNullOrBlank()) {
        customFolderName!!.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    } else {
        val sanitizedSiteName = siteName.ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        "${sanitizedSiteName}_$sessionTimestamp"
    }
    val dir = File(baseDir, parentFolderName)
    if (!dir.exists() && !dir.mkdirs()) return null
    return dir
}

private fun generatePDF(context: Context, siteName: String, lat: String, lng: String, fullAddress: String, telco: String, sections: List<TSSRSectionData>, sectionImages: Map<String, Uri?>, customFolderName: String?, sessionTimestamp: String) {
    val folder = getSiteFolder(context, siteName, sessionTimestamp, customFolderName) ?: return
    val sanitizedSiteName = siteName.ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    val pdfFile = File(folder, "${sanitizedSiteName}_${dateStr}.pdf")
    
    try {
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        
        document.add(Paragraph("TSSR REPORT").setTextAlignment(TextAlignment.CENTER).setFontSize(20f).setBold())
        document.add(Paragraph("Telco: $telco").setBold())
        document.add(Paragraph("Site Name: $siteName"))
        document.add(Paragraph("Location: $lat, $lng"))
        document.add(Paragraph("Address: $fullAddress"))
        document.add(Paragraph("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"))
        
        sections.forEach { section ->
            val fieldImages = section.fields.filter { sectionImages["${section.title}_$it"] != null }
            if (fieldImages.isNotEmpty()) {
                document.add(Paragraph("\n${section.title}").setBold().setFontSize(16f))
                fieldImages.forEach { field ->
                    val key = "${section.title}_$field"
                    val uri = sectionImages[key]
                    if (uri != null) {
                        document.add(Paragraph(field).setFontSize(12f))
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val bytes = input.readBytes()
                                val imageData = ImageDataFactory.create(bytes)
                                val pdfImage = com.itextpdf.layout.element.Image(imageData)
                                pdfImage.setMaxWidth(500f)
                                pdfImage.setMaxHeight(300f)
                                document.add(pdfImage)
                            }
                        } catch (e: Exception) {}
                    }
                }
                document.add(AreaBreak())
            }
        }
        document.close()
        Toast.makeText(context, "PDF saved to ${folder.name}", Toast.LENGTH_LONG).show()
        MediaScannerConnection.scanFile(context, arrayOf(pdfFile.absolutePath), null, null)
    } catch (e: Exception) {
        Log.e("TSSR", "PDF creation failed", e)
    }
}

private fun generateDocx(context: Context, siteName: String, lat: String, lng: String, fullAddress: String, telco: String, sections: List<TSSRSectionData>, sectionImages: Map<String, Uri?>, customFolderName: String?, sessionTimestamp: String) {
    val folder = getSiteFolder(context, siteName, sessionTimestamp, customFolderName) ?: return
    val sanitizedSiteName = siteName.ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    val docxFile = File(folder, "${sanitizedSiteName}_${dateStr}.docx")

    try {
        val document = XWPFDocument()
        val title = document.createParagraph()
        title.alignment = ParagraphAlignment.CENTER
        val titleRun = title.createRun()
        titleRun.isBold = true
        titleRun.fontSize = 20
        titleRun.setText("TSSR REPORT")

        val info = document.createParagraph()
        val infoRun = info.createRun()
        infoRun.setText("Telco: $telco")
        infoRun.addBreak()
        infoRun.setText("Site Name: $siteName")
        infoRun.addBreak()
        infoRun.setText("Location: $lat, $lng")
        infoRun.addBreak()
        infoRun.setText("Address: $fullAddress")
        infoRun.addBreak()
        infoRun.setText("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")

        sections.forEach { section ->
            val fieldImages = section.fields.filter { sectionImages["${section.title}_$it"] != null }
            if (fieldImages.isNotEmpty()) {
                val sectionPara = document.createParagraph()
                val sectionRun = sectionPara.createRun()
                sectionRun.isBold = true
                sectionRun.fontSize = 16
                sectionRun.addBreak()
                sectionRun.setText(section.title)

                fieldImages.forEach { field ->
                    val key = "${section.title}_$field"
                    val uri = sectionImages[key]
                    if (uri != null) {
                        val fieldPara = document.createParagraph()
                        val fieldRun = fieldPara.createRun()
                        fieldRun.setText(field)
                        fieldRun.addBreak()
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                fieldRun.addPicture(input, XWPFDocument.PICTURE_TYPE_JPEG, field, Units.toEMU(400.0), Units.toEMU(300.0))
                            }
                        } catch (e: Exception) {}
                    }
                }
                document.createParagraph().createRun().addBreak(BreakType.PAGE)
            }
        }
        FileOutputStream(docxFile).use { out -> document.write(out) }
        document.close()
        Toast.makeText(context, "DOCX saved to ${folder.name}", Toast.LENGTH_LONG).show()
        MediaScannerConnection.scanFile(context, arrayOf(docxFile.absolutePath), null, null)
    } catch (e: Exception) {
        Log.e("TSSR", "DOCX creation failed", e)
    }
}

private fun sectionDirToImages(context: Context, sectionDir: File): Map<String, Uri> {
    val images = mutableMapOf<String, Uri>()
    sectionDir.listFiles { f -> f.extension == "jpg" }?.forEach { file ->
        val name = file.nameWithoutExtension
        val parts = name.split("_")
        if (parts.size >= 2) {
            val fieldName = parts[1].replace("_", " ")
            val sectionName = sectionDir.name.replace("_", " ")
            val key = "${sectionName}_$fieldName"
            images[key] = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
        }
    }
    return images
}

@Composable
fun ImagePreviewDialog(uri: Uri, onDismiss: () -> Unit, onRetake: () -> Unit, onGallery: () -> Unit, isMap: Boolean = false) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = ComposeColor.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(painter = rememberAsyncImagePainter(uri), contentDescription = null, modifier = Modifier.fillMaxSize().align(Alignment.Center), contentScale = ContentScale.Fit)
                IconButton(onClick = onDismiss, modifier = Modifier.padding(16.dp).align(Alignment.TopStart).statusBarsPadding()) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = ComposeColor.White)
                }
                Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(), color = ComposeColor.Black.copy(alpha = 0.6f)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onRetake() }) {
                            Icon(if (isMap) Icons.Default.Map else Icons.Default.Replay, "Action", tint = ComposeColor.White)
                            Text(if (isMap) "Resnap Map" else "Retake", color = ComposeColor.White, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onGallery() }) {
                            Icon(Icons.Default.PhotoLibrary, "Gallery", tint = ComposeColor.White)
                            Text("Gallery", color = ComposeColor.White, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onDismiss() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ComposeColor.White)
                            Text("Back", color = ComposeColor.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleTSSRSection(sectionData: TSSRSectionData, siteName: String, sessionTimestamp: String, lat: String, lng: String, address: String, images: Map<String, Uri?>, onStartCamera: (String, String) -> Unit, onStartGallery: (String, String) -> Unit, onImageClick: (String, Uri, String) -> Unit, onScreenshot: () -> Unit, mapRef: (MapView) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = sectionData.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (sectionData.title == "A3.1 Site Location") { VicinityMapView(lat, lng, mapRef) }
                    sectionData.fields.chunked(2).forEach { rowFields ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowFields.forEach { field ->
                                val key = "${sectionData.title}_$field"
                                Box(modifier = Modifier.weight(1f)) {
                                    val isVicinityMap = field == "Vicinity Map"
                                    TSSRPhotoField(label = field, currentUri = images[key], allowCamera = !isVicinityMap, onCameraClick = { if (images[key] == null) { if (isVicinityMap) onScreenshot() else onStartCamera(key, field) } else { onImageClick(key, images[key]!!, field) } }, onGalleryClick = { onStartGallery(key, field) }, isMap = isVicinityMap)
                                }
                            }
                            if (rowFields.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TSSRPhotoField(label: String, currentUri: Uri?, allowCamera: Boolean, onCameraClick: () -> Unit, onGalleryClick: () -> Unit, isMap: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer).clickable { onCameraClick() }, contentAlignment = Alignment.Center) {
            if (currentUri != null) { Image(painter = rememberAsyncImagePainter(currentUri), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            else { Icon(if (isMap) Icons.Default.Map else if (allowCamera) Icons.Default.AddAPhoto else Icons.Default.PhotoLibrary, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        TextButton(onClick = onGalleryClick, contentPadding = PaddingValues(0.dp)) { Text("Gallery", fontSize = 10.sp) }
    }
}

@Composable
fun VicinityMapView(lat: String, lng: String, onMapReady: (MapView) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp))) {
        AndroidView(factory = { ctx -> MapView(ctx).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(18.0); val startPoint = GeoPoint(lat.toDoubleOrNull() ?: 14.59, lng.toDoubleOrNull() ?: 120.98); controller.setCenter(startPoint); val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this); locationOverlay.enableMyLocation(); overlays.add(locationOverlay); onMapReady(this) } }, update = { view -> val point = GeoPoint(lat.toDoubleOrNull() ?: 14.59, lng.toDoubleOrNull() ?: 120.98); view.controller.setCenter(point) })
    }
}

private fun getAddressFromLocation(context: Context, latitude: Double, longitude: Double): String {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]
            "${addr.locality ?: ""}, ${addr.adminArea ?: ""}, ${addr.countryName ?: ""}\n${addr.getAddressLine(0) ?: ""}"
        } else "Address not found"
    } catch (e: Exception) { "Address detection failed" }
}

private fun createTSSRFile(context: Context, siteName: String, section: String, fieldName: String, sessionTime: String, customFolderName: String? = null): File? {
    try {
        val sanitizedSection = section.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val sanitizedFieldName = fieldName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
        val folder = getSiteFolder(context, siteName, sessionTime, customFolderName) ?: return null
        val subDir = File(folder, sanitizedSection)
        if (!subDir.exists() && !subDir.mkdirs()) return null
        return File(subDir, "${sanitizedSection}_${sanitizedFieldName}_${date}_$time.jpg").apply { if (!exists()) createNewFile() }
    } catch (e: Exception) { return null }
}

private fun addOverlayToImage(context: Context, uri: Uri, siteName: String, lat: String, lng: String, address: String): Uri? {
    return try {
        val originalBitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return uri
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply { color = Color.WHITE; textSize = originalBitmap.height / 45f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); setShadowLayer(2f, 1f, 1f, Color.BLACK) }
        val overlayText = """
            Site Name: $siteName
            Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}
            Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}
            Lat: $lat, Lng: $lng
            $address
        """.trimIndent().split("\n")
        var y = mutableBitmap.height - (overlayText.size * paint.textSize * 1.2f) - 20f
        for (line in overlayText) {
            val textWidth = paint.measureText(line)
            canvas.drawText(line, mutableBitmap.width - textWidth - 20f, y, paint)
            y += paint.textSize * 1.2f
        }
        context.contentResolver.openOutputStream(uri)?.use { out -> mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        if (originalBitmap != mutableBitmap) originalBitmap.recycle()
        mutableBitmap.recycle()
        uri
    } catch (e: Exception) { uri }
}
