package com.example.isdp2java.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Geocoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.core.content.ContextCompat
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
import com.example.isdp2java.utils.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TSSRScreen(
    initialFolder: String? = null,
    initialTelco: String? = null,
    onBack: () -> Unit,
    onFolderChange: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var siteName by rememberSaveable { mutableStateOf("") }
    var isSiteNameConfirmed by rememberSaveable { mutableStateOf(false) }
    var lat by rememberSaveable { mutableStateOf("") }
    var lng by rememberSaveable { mutableStateOf("") }
    var fullAddress by rememberSaveable { mutableStateOf("Detecting location...") }
    var vehicleAccess by rememberSaveable { mutableStateOf("4 Wheeled") }
    var otherVehicleAccess by rememberSaveable { mutableStateOf("") }
    var telcoName by rememberSaveable { mutableStateOf(initialTelco ?: "Globe") }
    var customTelcoName by rememberSaveable { mutableStateOf("") }
    
    val sessionTimestamp = rememberSaveable { SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date()) }
    val sectionImages = remember { mutableStateMapOf<String, Uri?>() }

    var viewerUri by remember { mutableStateOf<Uri?>(null) }
    var viewerFieldKey by remember { mutableStateOf<String?>(null) }
    var viewerFieldSection by remember { mutableStateOf<String?>(null) }
    var viewerFieldName by remember { mutableStateOf<String?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var customFolderName by remember { mutableStateOf(initialFolder) }

    // Sync back to MainActivity when local folder state changes
    LaunchedEffect(initialFolder) {
        if (initialFolder != customFolderName) {
            customFolderName = initialFolder
        }
    }

    LaunchedEffect(customFolderName) {
        onFolderChange(customFolderName)
    }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSelectFolderDialog by remember { mutableStateOf(false) }
    var existingFolders by remember { mutableStateOf<List<String>>(emptyList()) }

    var showReportOptions by remember { mutableStateOf(false) }
    var triedToProceed by rememberSaveable { mutableStateOf(false) }

    val isHeaderValid = siteName.isNotBlank() && 
            (isSiteNameConfirmed || !customFolderName.isNullOrBlank()) && 
            lat.isNotBlank() && 
            lng.isNotBlank() && 
            (telcoName != "Neutral" || customTelcoName.isNotBlank())

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
                                FileUtils.getAddressFromLocation(context, location.latitude, location.longitude)
                            }
                        }
                    }
                }
            }
        }
    }

    var activeFieldKey by rememberSaveable { mutableStateOf<String?>(null) }
    var activeFieldSection by rememberSaveable { mutableStateOf<String?>(null) }
    var activeFieldName by rememberSaveable { mutableStateOf<String?>(null) }
    var tempPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var tempFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempPhotoUriString != null && activeFieldKey != null) {
            val originalUri = Uri.parse(tempPhotoUriString!!)
            scope.launch {
                val overlaidUri = withContext(Dispatchers.IO) {
                    FileUtils.addOverlayToImage(context, originalUri, siteName, lat, lng, fullAddress)
                }
                overlaidUri?.let { uri ->
                    sectionImages[activeFieldKey!!] = uri
                    viewerUri = uri
                    viewerFieldKey = activeFieldKey
                    viewerFieldSection = activeFieldSection
                    viewerFieldName = activeFieldName
                    
                    // Force media scan so it shows in device gallery
                    tempFilePath?.let { path ->
                        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                    }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedUri ->
        selectedUri?.let { sourceUri ->
            scope.launch {
                val overlaidUri = withContext(Dispatchers.IO) {
                    FileUtils.addOverlayToImage(context, sourceUri, siteName, lat, lng, fullAddress)
                }
                overlaidUri?.let { uri ->
                    sectionImages[activeFieldKey!!] = uri
                    viewerUri = uri
                    viewerFieldKey = activeFieldKey
                    viewerFieldSection = activeFieldSection
                    viewerFieldName = activeFieldName
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            activeFieldKey?.let { key ->
                activeFieldSection?.let { section ->
                    activeFieldName?.let { field ->
                        FileUtils.createSurveyFile(context, "TSSR", siteName, section, field, sessionTimestamp, customFolderName)?.let { file ->
                            tempFilePath = file.absolutePath
                            val uri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                            tempPhotoUriString = uri.toString()
                            cameraLauncher.launch(uri)
                        }
                    }
                }
            }
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            try {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500)
                    .setMinUpdateDistanceMeters(0.5f)
                    .build()
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            } catch (e: SecurityException) {}
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (!hasFine || !hasCoarse) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (initialFolder == null && location != null) {
                        if (lat.isEmpty()) lat = location.latitude.toString()
                        if (lng.isEmpty()) lng = location.longitude.toString()
                        scope.launch {
                            if (fullAddress == "Detecting location...") {
                                fullAddress = withContext(Dispatchers.IO) {
                                    FileUtils.getAddressFromLocation(context, location.latitude, location.longitude)
                                }
                            }
                        }
                    }
                }
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500)
                    .setMinUpdateDistanceMeters(0.5f)
                    .build()
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            } catch (e: SecurityException) {}
        }

        Configuration.getInstance().load(context, context.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    DisposableEffect(Unit) {
        onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    LaunchedEffect(initialFolder) {
        if (initialFolder != null) {
            val tssrDir = FileUtils.getSurveyFolder(context, "TSSR", "", "", initialFolder)
            if (tssrDir != null && tssrDir.exists()) {
                withContext(Dispatchers.IO) {
                    try {
                        val props = FileUtils.loadMetadata(tssrDir)
                        if (props != null) {
                            withContext(Dispatchers.Main) {
                                siteName = props.getProperty("siteName", "")
                                if (siteName.isNotBlank()) isSiteNameConfirmed = true
                                lat = props.getProperty("lat", "")
                                lng = props.getProperty("lng", "")
                                fullAddress = props.getProperty("fullAddress", "")
                                vehicleAccess = props.getProperty("vehicleAccess", "4 Wheeled")
                                otherVehicleAccess = props.getProperty("otherVehicleAccess", "")
                                if (initialTelco == null) {
                                    telcoName = props.getProperty("telcoName", telcoName)
                                }
                                
                                props.stringPropertyNames().filter { it.startsWith("img_") }.forEach { propKey ->
                                    val key = propKey.substring(4)
                                    val relPath = props.getProperty(propKey)
                                    val imgFile = File(tssrDir, relPath)
                                    if (imgFile.exists()) {
                                        sectionImages[key] = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", imgFile)
                                    }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                siteName = initialFolder.substringBeforeLast("_")
                                if (siteName.isNotBlank()) isSiteNameConfirmed = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TSSR", "Failed to load metadata", e)
                    }
                }
            }
        }
    }

    LaunchedEffect(isSiteNameConfirmed, customFolderName, lat, lng, fullAddress, vehicleAccess, otherVehicleAccess, telcoName, customTelcoName, sectionImages.size) {
        if (customFolderName != null || (isSiteNameConfirmed && siteName.isNotBlank())) {
            val folder = FileUtils.getSurveyFolder(context, "TSSR", siteName, sessionTimestamp, customFolderName)
            if (folder != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val props = Properties()
                        props.setProperty("siteName", siteName)
                        props.setProperty("lat", lat)
                        props.setProperty("lng", lng)
                        props.setProperty("fullAddress", fullAddress)
                        props.setProperty("vehicleAccess", vehicleAccess)
                        props.setProperty("otherVehicleAccess", otherVehicleAccess)
                        
                        val finalTelco = if (telcoName == "Neutral" && customTelcoName.isNotBlank()) customTelcoName else telcoName
                        props.setProperty("telcoName", finalTelco)
                        props.setProperty("surveyType", "TSSR")

                        sectionImages.forEach { (key, uri) ->
                            uri?.let { u ->
                                val relPath = FileUtils.findImageRelativePath(folder, u)
                                if (relPath != null) {
                                    props.setProperty("img_$key", relPath)
                                }
                            }
                        }
                        FileUtils.saveMetadata(folder, props, "TSSR Survey Metadata")
                    } catch (e: Exception) {
                        Log.e("TSSR", "Failed to save metadata", e)
                    }
                }
            }
        }
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

    var mapRef by remember { mutableStateOf<MapView?>(null) }

    fun captureMapSnapshot() {
        val map = mapRef ?: return
        val bitmap = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        map.draw(canvas)

        val key = "A3.1 Site Location_Vicinity Map"
        val folder = FileUtils.getSurveyFolder(context, "TSSR", siteName, sessionTimestamp, customFolderName) ?: return
        val subDir = File(folder, "A3_1_Site_Location")
        if (!subDir.exists()) subDir.mkdirs()

        val file = File(subDir, "A3_1_Site_Location_Vicinity_Map_${sessionTimestamp}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        val uri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
        
        scope.launch {
            val overlaidUri = withContext(Dispatchers.IO) {
                FileUtils.addOverlayToImage(context, uri, siteName, lat, lng, fullAddress)
            }
            overlaidUri?.let {
                sectionImages[key] = it
                viewerUri = it
                viewerFieldKey = key
                viewerFieldSection = "A3.1 Site Location"
                viewerFieldName = "Vicinity Map"
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                Toast.makeText(context, "Map snapshot captured", Toast.LENGTH_SHORT).show()
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
                                        val rootDir = Environment.getExternalStorageDirectory()
                                        val baseDir = File(rootDir, "SMS_ISDP_Surveys")
                                        if (!baseDir.exists()) baseDir.mkdirs()
                                        baseDir.listFiles { file -> file.isDirectory && File(file, "metadata.properties").exists() }?.map { it.name } ?: emptyList()
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
                title = { Text("TSSR Survey") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
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
                // Site Header Info
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = siteName,
                                onValueChange = { siteName = it; isSiteNameConfirmed = false },
                                label = { Text("Site Name") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                isError = triedToProceed && siteName.isBlank(),
                                trailingIcon = {
                                    if (isSiteNameConfirmed) {
                                        Icon(Icons.Default.CheckCircle, null, tint = ComposeColor(0xFF4CAF50))
                                    } else {
                                        IconButton(onClick = {
                                            if (siteName.isNotBlank()) {
                                                isSiteNameConfirmed = true
                                                Toast.makeText(context, "Site name confirmed", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.Check, null)
                                        }
                                    }
                                }
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f), singleLine = true, isError = triedToProceed && lat.isBlank())
                            OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Lng") }, modifier = Modifier.weight(1f), singleLine = true, isError = triedToProceed && lng.isBlank())
                        }

                        Text(text = fullAddress, style = MaterialTheme.typography.bodySmall, maxLines = 2)

                        // Telco Selection
                        Text("Select Telco Brand", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Globe", "Smart", "DITO", "Neutral").forEach { t ->
                                FilterChip(
                                    selected = telcoName == t,
                                    onClick = { telcoName = t },
                                    label = { Text(t, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (telcoName == "Neutral") {
                            OutlinedTextField(
                                value = customTelcoName,
                                onValueChange = { customTelcoName = it },
                                label = { Text("Specify Telco") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = triedToProceed && customTelcoName.isBlank()
                            )
                        }

                        // Vehicle Access Selection
                        Text("Vehicle Access", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("4 Wheeled", "2 Wheeled", "Others").forEach { v ->
                                FilterChip(
                                    selected = vehicleAccess == v,
                                    onClick = { vehicleAccess = v },
                                    label = { Text(v, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (vehicleAccess == "Others") {
                            OutlinedTextField(
                                value = otherVehicleAccess,
                                onValueChange = { otherVehicleAccess = it },
                                label = { Text("Specify Access") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        
                        if (customFolderName != null) {
                            Text("Current Folder: $customFolderName", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            items(sections) { section ->
                    CollapsibleTSSRSection(
                        sectionData = section,
                        lat = lat,
                        lng = lng,
                        images = sectionImages,
                        onStartCamera = { key, fieldName ->
                            triedToProceed = true
                            if (!isHeaderValid) {
                                Toast.makeText(context, "Please fill in all required properties (Site Name, Coords, Telco) and confirm Site Name", Toast.LENGTH_SHORT).show()
                            } else {
                                activeFieldKey = key
                                activeFieldSection = section.title
                                activeFieldName = fieldName
                                val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (!hasCamera) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                } else {
                                    FileUtils.createSurveyFile(context, "TSSR", siteName, section.title, fieldName, sessionTimestamp, customFolderName)?.let { file ->
                                        tempFilePath = file.absolutePath
                                        val uri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                                        tempPhotoUriString = uri.toString()
                                        cameraLauncher.launch(uri)
                                    }
                                }
                            }
                        },
                        onStartGallery = { key, fieldName ->
                            triedToProceed = true
                            if (!isHeaderValid) {
                                Toast.makeText(context, "Please fill in all required properties and confirm Site Name", Toast.LENGTH_SHORT).show()
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
                        onScreenshot = {
                            triedToProceed = true
                            if (!isHeaderValid) {
                                Toast.makeText(context, "Please fill in all required properties and confirm Site Name", Toast.LENGTH_SHORT).show()
                            } else {
                                captureMapSnapshot()
                            }
                        },
                        mapRef = { mapRef = it }
                    )
                }
                
                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            triedToProceed = true
                            if (!isHeaderValid) {
                                Toast.makeText(context, "Please fill in all required properties and confirm Site Name", Toast.LENGTH_SHORT).show()
                            } else {
                                showReportOptions = true
                            }
                        },
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

        if (showReportOptions) {
            val finalTelco = if (telcoName == "Neutral" && customTelcoName.isNotBlank()) customTelcoName else telcoName
            AlertDialog(
                onDismissRequest = { showReportOptions = false },
                title = { Text("Report Options") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This allows you to specify a custom directory name. Metadata will be shared within the folder.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("Folder Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (textValue.isNotBlank()) {
                            customFolderName = textValue
                            scope.launch { withContext(Dispatchers.IO) { FileUtils.getSurveyFolder(context, "TSSR", siteName, sessionTimestamp, textValue) } }
                            showCreateFolderDialog = false
                            Toast.makeText(context, "Folder set to: $textValue", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Confirm") }
                },
                dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } }
            )
        }

        if (showSelectFolderDialog) {
            AlertDialog(
                onDismissRequest = { showSelectFolderDialog = false },
                title = { Text("Select Existing Site Folder") },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        if (existingFolders.isEmpty()) {
                            item { Text("No existing TSSR folders found.") }
                        } else {
                            items(existingFolders) { folder ->
                                ListItem(
                                    headlineContent = { Text(folder) },
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
                dismissButton = { TextButton(onClick = { showSelectFolderDialog = false }) { Text("Cancel") } }
            )
        }

        if (viewerUri != null) {
            ImagePreviewDialog(
                uri = viewerUri!!,
                onDismiss = { viewerUri = null },
                onRetake = {
                    viewerUri = null
                    activeFieldKey = viewerFieldKey
                    activeFieldSection = viewerFieldSection
                    activeFieldName = viewerFieldName
                    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (!hasCamera) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    else {
                        FileUtils.createSurveyFile(context, "TSSR", siteName, activeFieldSection!!, activeFieldName!!, sessionTimestamp, customFolderName)?.let { file ->
                            tempFilePath = file.absolutePath
                            val uri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                            tempPhotoUriString = uri.toString()
                            cameraLauncher.launch(uri)
                        }
                    }
                },
                onGallery = {
                    viewerUri = null
                    activeFieldKey = viewerFieldKey
                    activeFieldSection = viewerFieldSection
                    activeFieldName = viewerFieldName
                    galleryLauncher.launch("image/*")
                },
                isMap = viewerFieldName == "Vicinity Map"
            )
        }
    }
}

private fun generatePDF(
    context: Context,
    siteName: String,
    lat: String,
    lng: String,
    address: String,
    telco: String,
    sections: List<TSSRSectionData>,
    images: Map<String, Uri?>,
    customFolderName: String?,
    sessionTime: String
) {
    val folder = FileUtils.getSurveyFolder(context, "TSSR", siteName, sessionTime, customFolderName) ?: return
    val sanitizedSiteName = siteName.ifBlank { "TSSR_Untitled" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    val pdfFile = File(folder, "${sanitizedSiteName}_${dateStr}.pdf")
    try {
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.add(Paragraph("TSSR SURVEY REPORT").setTextAlignment(TextAlignment.CENTER).setFontSize(20f).setBold())
        document.add(Paragraph("Telco: $telco").setBold())
        document.add(Paragraph("Site Name: $siteName"))
        document.add(Paragraph("Location: $lat, $lng"))
        document.add(Paragraph("Address: $address"))
        document.add(Paragraph("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"))

        sections.forEach { section ->
            val fieldImages = section.fields.filter { field ->
                field != "ADD_DYNAMIC" && field != "REMOVE_DYNAMIC" && images["${section.title}_$field"] != null
            }
            if (fieldImages.isNotEmpty()) {
                document.add(Paragraph("\n${section.title}").setBold().setFontSize(16f))
                fieldImages.forEach { field ->
                    val key = "${section.title}_$field"
                    images[key]?.let { uri ->
                        document.add(Paragraph(field).setFontSize(12f))
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val imageData = ImageDataFactory.create(input.readBytes())
                                val pdfImage = com.itextpdf.layout.element.Image(imageData)
                                pdfImage.setMaxWidth(500f)
                                pdfImage.setMaxHeight(300f)
                                document.add(pdfImage)
                            }
                        } catch (e: Exception) {
                            Log.e("TSSRScreen", "Failed to add image to PDF: $field", e)
                        }
                    }
                }
                document.add(AreaBreak())
            }
        }
        document.close()
        Toast.makeText(context, "TSSR PDF saved to folder", Toast.LENGTH_SHORT).show()
        MediaScannerConnection.scanFile(context, arrayOf(pdfFile.absolutePath), null, null)
    } catch (e: Exception) {
        Log.e("TSSRScreen", "PDF creation failed", e)
        Toast.makeText(context, "PDF creation failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun generateDocx(
    context: Context,
    siteName: String,
    lat: String,
    lng: String,
    address: String,
    telco: String,
    sections: List<TSSRSectionData>,
    images: Map<String, Uri?>,
    customFolderName: String?,
    sessionTime: String
) {
    val folder = FileUtils.getSurveyFolder(context, "TSSR", siteName, sessionTime, customFolderName) ?: return
    val sanitizedSiteName = siteName.ifBlank { "TSSR_Untitled" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    val docxFile = File(folder, "${sanitizedSiteName}_${dateStr}.docx")

    try {
        val document = XWPFDocument()
        val title = document.createParagraph()
        title.alignment = ParagraphAlignment.CENTER
        val titleRun = title.createRun()
        titleRun.isBold = true
        titleRun.fontSize = 20
        titleRun.setText("TSSR SURVEY REPORT")

        val info = document.createParagraph()
        val infoRun = info.createRun()
        infoRun.setText("Telco: $telco")
        infoRun.addBreak()
        infoRun.setText("Site Name: $siteName")
        infoRun.addBreak()
        infoRun.setText("Location: $lat, $lng")
        infoRun.addBreak()
        infoRun.setText("Address: $address")
        infoRun.addBreak()
        infoRun.setText("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")

        sections.forEach { section ->
            val fieldImages = section.fields.filter { field ->
                field != "ADD_DYNAMIC" && field != "REMOVE_DYNAMIC" && images["${section.title}_$field"] != null
            }
            if (fieldImages.isNotEmpty()) {
                val sectionPara = document.createParagraph()
                val sectionRun = sectionPara.createRun()
                sectionRun.isBold = true
                sectionRun.fontSize = 16
                sectionRun.addBreak()
                sectionRun.setText(section.title)

                fieldImages.forEach { field ->
                    val key = "${section.title}_$field"
                    val uri = images[key]
                    if (uri != null) {
                        val fieldPara = document.createParagraph()
                        val fieldRun = fieldPara.createRun()
                        fieldRun.setText(field)
                        fieldRun.addBreak()
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                fieldRun.addPicture(input, XWPFDocument.PICTURE_TYPE_JPEG, field, Units.toEMU(400.0), Units.toEMU(300.0))
                            }
                        } catch (e: Exception) {
                            Log.e("TSSRScreen", "Failed to add image to DOCX: $field", e)
                        }
                    }
                }
                document.createParagraph().createRun().addBreak(BreakType.PAGE)
            }
        }
        FileOutputStream(docxFile).use { out -> document.write(out) }
        document.close()
        Toast.makeText(context, "TSSR DOCX saved to folder", Toast.LENGTH_SHORT).show()
        MediaScannerConnection.scanFile(context, arrayOf(docxFile.absolutePath), null, null)
    } catch (e: Exception) {
        Log.e("TSSRScreen", "DOCX creation failed", e)
        Toast.makeText(context, "DOCX creation failed: ${e.message}", Toast.LENGTH_LONG).show()
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
fun CollapsibleTSSRSection(
    sectionData: TSSRSectionData,
    lat: String,
    lng: String,
    images: Map<String, Uri?>,
    onStartCamera: (String, String) -> Unit,
    onStartGallery: (String, String) -> Unit,
    onImageClick: (String, Uri, String) -> Unit,
    onScreenshot: () -> Unit,
    mapRef: (MapView) -> Unit
) {
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
                                    TSSRPhotoField(
                                        label = field,
                                        currentUri = images[key],
                                        allowCamera = !isVicinityMap,
                                        onCameraClick = {
                                            if (images[key] == null) {
                                                if (isVicinityMap) {
                                                    onScreenshot()
                                                } else {
                                                    onStartCamera(key, field)
                                                }
                                            } else {
                                                onImageClick(key, images[key]!!, field)
                                            }
                                        },
                                        onGalleryClick = { onStartGallery(key, field) },
                                        isMap = isVicinityMap
                                    )
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
