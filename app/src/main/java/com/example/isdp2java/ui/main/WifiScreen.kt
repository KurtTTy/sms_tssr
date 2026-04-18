package com.example.isdp2java.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
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
fun WifiScreen(
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
    var telcoName by rememberSaveable { mutableStateOf(initialTelco ?: "Wifi") }
    var customTelcoName by rememberSaveable { mutableStateOf("") }
    
    // Dynamic section counts
    var apLocationCount by rememberSaveable { mutableStateOf(5) }
    var apCoverageCount by rememberSaveable { mutableStateOf(5) }
    var idfCount by rememberSaveable { mutableStateOf(5) }
    var powerTappingCount by rememberSaveable { mutableStateOf(5) }

    val sessionTimestamp = rememberSaveable { SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date()) }
    val sectionImages = remember { mutableStateMapOf<String, Uri?>() }

    var viewerUri by remember { mutableStateOf<Uri?>(null) }
    var viewerFieldKey by remember { mutableStateOf<String?>(null) }
    var viewerFieldSection by remember { mutableStateOf<String?>(null) }
    var viewerFieldName by remember { mutableStateOf<String?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var customFolderName by remember { mutableStateOf<String?>(null) }
    var isRestoringProject by remember { mutableStateOf(true) }
    
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
            ((telcoName != "Wifi" && telcoName != "Neutral") || customTelcoName.isNotBlank())

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (initialFolder != null || isRestoringProject) return
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

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) return@rememberLauncherForActivityResult

        val photoUriString = tempPhotoUriString ?: return@rememberLauncherForActivityResult
        val fieldKey = activeFieldKey ?: return@rememberLauncherForActivityResult
        val originalUri = Uri.parse(photoUriString)

        scope.launch {
            val overlaidUri = withContext(Dispatchers.IO) {
                FileUtils.addOverlayToImage(context, originalUri, siteName, lat, lng, fullAddress)
            }
            overlaidUri?.let {
                sectionImages[fieldKey] = it
                viewerUri = it
                viewerFieldKey = fieldKey
                viewerFieldSection = activeFieldSection
                viewerFieldName = activeFieldName
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            if (activeFieldKey != null) {
                activeFieldSection?.let { section ->
                    activeFieldName?.let { field ->
                        FileUtils.createSurveyFile(context, "Wifi", siteName, section, field, sessionTimestamp, customFolderName)?.let { file ->
                            val uri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                            tempPhotoUriString = uri.toString()
                            cameraLauncher.launch(uri)
                        }
                    }
                }
            }
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

    LaunchedEffect(initialFolder, initialTelco) {
        isRestoringProject = true
        customFolderName = initialFolder
        siteName = ""
        isSiteNameConfirmed = false
        lat = ""
        lng = ""
        fullAddress = "Detecting location..."
        telcoName = initialTelco ?: "Wifi"
        customTelcoName = ""
        apLocationCount = 5
        apCoverageCount = 5
        idfCount = 5
        powerTappingCount = 5
        sectionImages.clear()
        viewerUri = null
        viewerFieldKey = null
        viewerFieldSection = null
        viewerFieldName = null
        activeFieldKey = null
        activeFieldSection = null
        activeFieldName = null
        tempPhotoUriString = null
        showReportOptions = false
        triedToProceed = false

        try {
            if (initialFolder != null) {
                val wifiDir = FileUtils.getSurveyFolder(context, "Wifi", "", "", initialFolder)
                if (wifiDir != null && wifiDir.exists()) {
                    withContext(Dispatchers.IO) {
                        val metadataFile = File(wifiDir, "metadata.properties")
                        if (metadataFile.exists()) {
                            val props = FileUtils.loadMetadata(wifiDir)
                            if (props != null) {
                                withContext(Dispatchers.Main) {
                                    siteName = props.getProperty("siteName", "")
                                    if (siteName.isNotBlank()) isSiteNameConfirmed = true
                                    lat = props.getProperty("lat", "")
                                    lng = props.getProperty("lng", "")
                                    fullAddress = props.getProperty("fullAddress", props.getProperty("siteAddress", ""))
                                    telcoName = props.getProperty("telcoName", telcoName)
                                    customTelcoName = props.getProperty("customTelcoName", "")

                                    apLocationCount = props.getProperty("apLocationCount", "5").toIntOrNull() ?: 5
                                    apCoverageCount = props.getProperty("apCoverageCount", "5").toIntOrNull() ?: 5
                                    idfCount = props.getProperty("idfCount", "5").toIntOrNull() ?: 5
                                    powerTappingCount = props.getProperty("powerTappingCount", "5").toIntOrNull() ?: 5

                                    props.stringPropertyNames().filter { it.startsWith("img_") }.forEach { propKey ->
                                        val key = propKey.substring(4)
                                        val relPath = props.getProperty(propKey)
                                        if (relPath != null) {
                                            val imgFile = File(wifiDir, relPath)
                                            if (imgFile.exists()) {
                                                sectionImages[key] = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", imgFile)
                                            }
                                        }
                                    }
                                    val mapPath = props.getProperty("mapSnapshotPath")
                                    if (mapPath != null) {
                                        val mapFile = File(wifiDir, mapPath)
                                        if (mapFile.exists()) {
                                            sectionImages["Site Location_Vicinity Map"] = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", mapFile)
                                        }
                                    }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                siteName = initialFolder
                                if (siteName.isNotBlank()) isSiteNameConfirmed = true
                            }
                        }
                    }
                }
            }
        } finally {
            isRestoringProject = false
        }
    }

    LaunchedEffect(isSiteNameConfirmed, customFolderName, lat, lng, fullAddress, telcoName, customTelcoName, sectionImages.size, apLocationCount, apCoverageCount, idfCount, powerTappingCount) {
        if (isRestoringProject) return@LaunchedEffect
        if (customFolderName != null || (isSiteNameConfirmed && siteName.isNotBlank())) {
            val folder = FileUtils.getSurveyFolder(context, "Wifi", siteName, sessionTimestamp, customFolderName)
            if (folder != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val metadataFile = File(folder, "metadata.properties")
                        val props = Properties()
                        props.setProperty("siteName", siteName)
                        props.setProperty("lat", lat)
                        props.setProperty("lng", lng)
                        props.setProperty("fullAddress", fullAddress)
                        props.setProperty("siteAddress", fullAddress) // Added for history consistency
                        props.setProperty("telcoName", telcoName)
                        props.setProperty("customTelcoName", customTelcoName)
                        props.setProperty("surveyType", "Wifi")
                        props.setProperty("apLocationCount", apLocationCount.toString())
                        props.setProperty("apCoverageCount", apCoverageCount.toString())
                        props.setProperty("idfCount", idfCount.toString())
                        props.setProperty("powerTappingCount", powerTappingCount.toString())
                        
                        sectionImages.forEach { (key, uri) ->
                            uri?.let { u ->
                                val relPath = FileUtils.findImageRelativePath(folder, u)
                                if (relPath != null) {
                                    props.setProperty("img_$key", relPath)
                                    if (key == "Site Location_Vicinity Map") {
                                        props.setProperty("mapSnapshotPath", relPath)
                                    }
                                }
                            }
                        }
                        FileUtils.saveMetadata(folder, props, "Wifi Metadata")
                    } catch (e: Exception) {}
                }
            }
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
                    if (!isRestoringProject && initialFolder == null && location != null) {
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

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { sourceUri ->
            val key = activeFieldKey ?: viewerFieldKey ?: return@let
            val section = activeFieldSection ?: viewerFieldSection ?: "General"
            val fieldName = activeFieldName ?: viewerFieldName ?: "Photo"
            
            scope.launch {
                val destFile = withContext(Dispatchers.IO) {
            FileUtils.createSurveyFile(context, "Wifi", siteName, section, fieldName, sessionTimestamp, customFolderName)
        }
                destFile?.let { file ->
                    val destUri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(sourceUri)?.use { input ->
                            context.contentResolver.openOutputStream(destUri)?.use { output -> input.copyTo(output) }
                        }
                    }
                    val overlaidUri = withContext(Dispatchers.IO) {
                        FileUtils.addOverlayToImage(context, destUri, siteName, lat, lng, fullAddress)
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

    fun captureMapSnapshot(isResnap: Boolean = false) {
        val map = mapRef ?: return
        
        // Ensure the map is laid out and has dimensions
        if (map.width <= 0 || map.height <= 0) {
            Toast.makeText(context, "Map not ready for snapshot", Toast.LENGTH_SHORT).show()
            return
        }

        val key = "Site Location_Vicinity Map"
        val section = "Site Location"
        val fieldName = "Vicinity Map"
        
        // Use a more unique name if resnapping to avoid cache/overlap issues
        val resnapSuffix = if (isResnap) "_resnap_${System.currentTimeMillis()}" else ""
        
        scope.launch {
            val destFile = withContext(Dispatchers.IO) {
                FileUtils.createSurveyFile(context, "Wifi", siteName, section, fieldName, sessionTimestamp, customFolderName, suffix = resnapSuffix)
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
                    FileUtils.addOverlayToImage(context, uri, siteName, lat, lng, fullAddress)
                }
                overlaidUri?.let {
                    sectionImages[key] = it
                    viewerUri = it
                    viewerFieldKey = key
                    viewerFieldSection = section
                    viewerFieldName = fieldName
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                    Toast.makeText(context, "Map snapshot captured", Toast.LENGTH_SHORT).show()
                }
                bitmap.recycle()
            }
        }
    }

    val sections = remember(apLocationCount, apCoverageCount, idfCount, powerTappingCount) {
        fun getDynamicFields(prefix: String, count: Int): List<String> {
            val list = (1..count).map { "$prefix${it.toString().padStart(2, '0')}" }.toMutableList()
            if (count < 50) list.add("ADD_DYNAMIC")
            if (count > 5) list.add("REMOVE_DYNAMIC")
            return list
        }
        listOf(
            TSSRSectionData("Site Location", listOf("Vicinity Map", "GPS Reading")),
            TSSRSectionData("Panoramic Antenna", (0..330 step 30).map { "${it}° Panoramic" }),
            TSSRSectionData("AP Location", getDynamicFields("AP", apLocationCount)),
            TSSRSectionData("AP Coverage", getDynamicFields("Coverage", apCoverageCount)),
            TSSRSectionData("IDF", getDynamicFields("IDF", idfCount)),
            TSSRSectionData("Power Tapping", getDynamicFields("Power", powerTappingCount))
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Text("Wifi Settings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
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
                    title = {
                        Column {
                            Text("Wifi Survey", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                            Text("Wifi Information", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = siteName,
                                onValueChange = { 
                                    siteName = it
                                    isSiteNameConfirmed = false
                                },
                                label = { Text("Site Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = triedToProceed && (siteName.isBlank() || (!isSiteNameConfirmed && customFolderName.isNullOrBlank())),
                                trailingIcon = {
                                    if (siteName.isNotBlank()) {
                                        IconButton(onClick = { 
                                            isSiteNameConfirmed = true
                                            Toast.makeText(context, "Confirmed: $siteName", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(imageVector = if (isSiteNameConfirmed) Icons.Default.CheckCircle else Icons.Default.Check, contentDescription = null, tint = if (isSiteNameConfirmed) ComposeColor(0xFF4CAF50) else LocalContentColor.current)
                                        }
                                    }
                                }
                            )
                            if (telcoName == "Wifi" || telcoName == "Neutral") {
                                OutlinedTextField(
                                    value = customTelcoName,
                                    onValueChange = { customTelcoName = it },
                                    label = { Text("Enter Telco Name") },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    singleLine = true,
                                    isError = triedToProceed && customTelcoName.isBlank(),
                                    trailingIcon = {
                                        if (customTelcoName.isNotBlank()) {
                                            Icon(Icons.Default.CheckCircle, null, tint = ComposeColor(0xFF4CAF50))
                                        }
                                    }
                                )
                            } else {
                                Text("Telco: $telcoName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f), isError = triedToProceed && lat.isBlank())
                                OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Lng") }, modifier = Modifier.weight(1f), isError = triedToProceed && lng.isBlank())
                            }
                            Text(text = fullAddress, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }

                items(sections) { section ->
                    CollapsibleWifiSection(
                        sectionData = section,
                        siteName = siteName,
                        isSiteNameConfirmed = isSiteNameConfirmed,
                        customFolderName = customFolderName,
                        sessionTimestamp = sessionTimestamp,
                        lat = lat,
                        lng = lng,
                        address = fullAddress,
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
                                if (!hasCamera) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                else {
                                    FileUtils.createSurveyFile(context, "Wifi", siteName, section.title, fieldName, sessionTimestamp, customFolderName)?.let { file ->
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
                                Toast.makeText(context, "Please fill in all required properties (Site Name, Coords, Telco) and confirm Site Name", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "Please fill in all required properties (Site Name, Coords, Telco) and confirm Site Name", Toast.LENGTH_SHORT).show()
                            } else {
                                captureMapSnapshot()
                            }
                        },
                        mapRef = { mapRef = it },
                        onAddClick = { title ->
                            triedToProceed = true
                            if (!isHeaderValid) {
                                Toast.makeText(context, "Please fill in all required properties (Site Name, Coords, Telco) and confirm Site Name", Toast.LENGTH_SHORT).show()
                            } else {
                                when (title) {
                                    "AP Location" -> if (apLocationCount < 50) apLocationCount++
                                    "AP Coverage" -> if (apCoverageCount < 50) apCoverageCount++
                                    "IDF" -> if (idfCount < 50) idfCount++
                                    "Power Tapping" -> if (powerTappingCount < 50) powerTappingCount++
                                }
                            }
                        },
                        onRemoveClick = { title ->
                            triedToProceed = true
                            if (!isHeaderValid) {
                                Toast.makeText(context, "Please fill in all required properties (Site Name, Coords, Telco) and confirm Site Name", Toast.LENGTH_SHORT).show()
                            } else {
                                when (title) {
                                    "AP Location" -> if (apLocationCount > 5) apLocationCount--
                                    "AP Coverage" -> if (apCoverageCount > 5) apCoverageCount--
                                    "IDF" -> if (idfCount > 5) idfCount--
                                    "Power Tapping" -> if (powerTappingCount > 5) powerTappingCount--
                                }
                            }
                        }
                    )
                }
                
                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            triedToProceed = true
                            if (!isHeaderValid) {
                                Toast.makeText(context, "Please fill in all required properties (Site Name, Coords, Telco) and confirm Site Name", Toast.LENGTH_SHORT).show()
                            } else {
                                showReportOptions = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("GENERATE WIFI REPORT", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(100.dp))
                }
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
                        generateWifiPDF(context, siteName, lat, lng, fullAddress, finalTelco, sections, sectionImages, customFolderName, sessionTimestamp)
                        showReportOptions = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save as PDF")
                    }
                    OutlinedButton(onClick = { 
                        generateWifiDocx(context, siteName, lat, lng, fullAddress, finalTelco, sections, sectionImages, customFolderName, sessionTimestamp)
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
                    Text("Images will be saved directly on device root", style = MaterialTheme.typography.bodySmall)
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
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                FileUtils.getSurveyFolder(context, "Wifi", siteName, sessionTimestamp, textValue)
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
                    Text("No folders found on device root")
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

    viewerUri?.let { currentViewerUri ->
        WifiImagePreviewDialog(
            uri = currentViewerUri,
            onDismiss = { viewerUri = null },
            onRetake = {
                val key = viewerFieldKey ?: ""
                val section = viewerFieldSection ?: ""
                val fieldName = viewerFieldName ?: "Photo"
                if (key.contains("Vicinity Map")) {
                    captureMapSnapshot(isResnap = true)
                } else {
                    activeFieldKey = key
                    activeFieldSection = section
                    activeFieldName = fieldName
                    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (!hasCamera) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    else {
                            FileUtils.createSurveyFile(context, "Wifi", siteName, section, fieldName, sessionTimestamp, customFolderName)?.let { file ->
                            val uri = FileProvider.getUriForFile(context, "com.example.isdp2java.fileprovider", file)
                            tempPhotoUriString = uri.toString()
                            cameraLauncher.launch(uri)
                        }
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

@Composable
fun CollapsibleWifiSection(
    sectionData: TSSRSectionData,
    siteName: String,
    isSiteNameConfirmed: Boolean,
    customFolderName: String?,
    sessionTimestamp: String,
    lat: String,
    lng: String,
    address: String,
    images: Map<String, Uri?>,
    onStartCamera: (String, String) -> Unit,
    onStartGallery: (String, String) -> Unit,
    onImageClick: (String, Uri, String) -> Unit,
    onScreenshot: () -> Unit,
    mapRef: (MapView) -> Unit,
    onAddClick: (String) -> Unit = {},
    onRemoveClick: (String) -> Unit = {}
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
                    if (sectionData.title == "Site Location") { WifiVicinityMapView(lat, lng, mapRef) }
                    sectionData.fields.chunked(2).forEach { rowFields ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowFields.forEach { field ->
                                when (field) {
                                    "ADD_DYNAMIC" -> {
                                        Box(modifier = Modifier.weight(1f)) {
                                            DynamicAddButton(onClick = { onAddClick(sectionData.title) })
                                        }
                                    }
                                    "REMOVE_DYNAMIC" -> {
                                        Box(modifier = Modifier.weight(1f)) {
                                            DynamicRemoveButton(onClick = { onRemoveClick(sectionData.title) })
                                        }
                                    }
                                    else -> {
                                        val key = "${sectionData.title}_$field"
                                        Box(modifier = Modifier.weight(1f)) {
                                            val isVicinityMap = field == "Vicinity Map"
                                            WifiPhotoField(
                                                label = field,
                                                currentUri = images[key],
                                                onCameraClick = {
                                                    if (images[key] == null) {
                                                        if (isVicinityMap) onScreenshot() else onStartCamera(key, field)
                                                    } else {
                                                        images[key]?.let { uri -> onImageClick(key, uri, field) }
                                                    }
                                                },
                                                onGalleryClick = { onStartGallery(key, field) },
                                                isMap = isVicinityMap
                                            )
                                        }
                                    }
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
fun WifiPhotoField(label: String, currentUri: Uri?, onCameraClick: () -> Unit, onGalleryClick: () -> Unit, isMap: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer).clickable { onCameraClick() }, contentAlignment = Alignment.Center) {
            if (currentUri != null) Image(painter = rememberAsyncImagePainter(currentUri), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(if (isMap) Icons.Default.Map else Icons.Default.AddAPhoto, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = onGalleryClick, contentPadding = PaddingValues(0.dp)) { Text("Gallery", fontSize = 10.sp) }
    }
}

@Composable
fun DynamicAddButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Add Item", style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun WifiImagePreviewDialog(uri: Uri, onDismiss: () -> Unit, onRetake: () -> Unit, onGallery: () -> Unit, isMap: Boolean = false) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = ComposeColor.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(painter = rememberAsyncImagePainter(uri), null, modifier = Modifier.fillMaxSize().align(Alignment.Center), contentScale = ContentScale.Fit)
                IconButton(onClick = onDismiss, modifier = Modifier.padding(16.dp).align(Alignment.TopStart).statusBarsPadding()) {
                    Icon(Icons.Default.Close, null, tint = ComposeColor.White)
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
fun DynamicRemoveButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Remove Item", style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Remove, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun WifiVicinityMapView(lat: String, lng: String, onMapReady: (MapView) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp))) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(18.0)
                    val startPoint = GeoPoint(lat.toDoubleOrNull() ?: 14.59, lng.toDoubleOrNull() ?: 120.98)
                    controller.setCenter(startPoint)
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    locationOverlay.enableMyLocation()
                    overlays.add(locationOverlay)
                    onMapReady(this)
                }
            },
            update = { /* Do nothing in update to preserve user zoom/pan */ }
        )
    }
}

private fun generateWifiPDF(context: Context, siteName: String, lat: String, lng: String, address: String, telco: String, sections: List<TSSRSectionData>, images: Map<String, Uri?>, customFolderName: String?, sessionTime: String) {
    val folder = FileUtils.getSurveyFolder(context, "Wifi", siteName, sessionTime, customFolderName) ?: return
    val sanitizedSiteName = siteName.ifBlank { "Wifi_Untitled" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    val pdfFile = File(folder, "${sanitizedSiteName}_${dateStr}.pdf")
    try {
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.add(Paragraph("WIFI SURVEY REPORT").setTextAlignment(TextAlignment.CENTER).setFontSize(20f).setBold())
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
                        } catch (e: Exception) {}
                    }
                }
                document.add(AreaBreak())
            }
        }
        document.close()
        Toast.makeText(context, "Wifi PDF saved to folder", Toast.LENGTH_SHORT).show()
        MediaScannerConnection.scanFile(context, arrayOf(pdfFile.absolutePath), null, null)
    } catch (e: Exception) {
        Log.e("WifiScreen", "PDF creation failed", e)
    }
}

private fun generateWifiDocx(context: Context, siteName: String, lat: String, lng: String, address: String, telco: String, sections: List<TSSRSectionData>, images: Map<String, Uri?>, customFolderName: String?, sessionTime: String) {
    val folder = FileUtils.getSurveyFolder(context, "Wifi", siteName, sessionTime, customFolderName) ?: return
    val sanitizedSiteName = siteName.ifBlank { "Wifi_Untitled" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    val docxFile = File(folder, "${sanitizedSiteName}_${dateStr}.docx")

    try {
        val document = XWPFDocument()
        val title = document.createParagraph()
        title.alignment = ParagraphAlignment.CENTER
        val titleRun = title.createRun()
        titleRun.isBold = true
        titleRun.fontSize = 20
        titleRun.setText("WIFI SURVEY REPORT")

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
                        } catch (e: Exception) {}
                    }
                }
                document.createParagraph().createRun().addBreak(BreakType.PAGE)
            }
        }
        FileOutputStream(docxFile).use { out -> document.write(out) }
        document.close()
        Toast.makeText(context, "Wifi DOCX saved to folder", Toast.LENGTH_SHORT).show()
        MediaScannerConnection.scanFile(context, arrayOf(docxFile.absolutePath), null, null)
    } catch (e: Exception) {
        Log.e("WifiScreen", "DOCX creation failed", e)
    }
}


