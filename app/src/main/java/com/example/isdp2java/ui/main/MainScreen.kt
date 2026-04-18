package com.example.isdp2java.ui.main

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.isdp2java.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.isdp2java.utils.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onThemeToggle: () -> Unit,
    onLogout: () -> Unit,
    tssrFolder: String?,
    wifiFolder: String?,
    matsiFolder: String?,
    onTssrFolderChange: (String?) -> Unit,
    onWifiFolderChange: (String?) -> Unit,
    onMatsiFolderChange: (String?) -> Unit,
    onNavigateToTSSR: (String?, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showFolderSelect by remember { mutableStateOf(false) }
    var existingFolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var recentSurveys by remember { mutableStateOf<List<History>>(emptyList()) }
    var recentClearedBefore by remember { mutableStateOf(prefs.getLong("recent_clear_before", 0L)) }

    fun refreshFolders() {
        scope.launch(Dispatchers.IO) {
            val rootDir = Environment.getExternalStorageDirectory()
            val baseDir = File(rootDir, "SMS_ISDP_Surveys")
            if (!baseDir.exists()) baseDir.mkdirs()
            val folders = baseDir.listFiles { file -> file.isDirectory && File(file, "metadata.properties").exists() }
                ?.toList()
                ?: emptyList()

            val surveys = folders.mapNotNull { folder ->
                val metadataFile = File(folder, "metadata.properties")
                val props = FileUtils.loadMetadata(folder)
                if (props != null) {
                    val siteName = props.getProperty("siteName", folder.name)
                    val telco = props.getProperty("telcoName", "Unknown")
                    val type = props.getProperty("surveyType", "TSSR")
                    val address = props.getProperty("siteAddress") ?: props.getProperty("fullAddress", "")
                    val lastUpdated = maxOf(folder.lastModified(), metadataFile.lastModified())

                    var thumbnailPath = props.getProperty("mapSnapshotPath")
                    if (thumbnailPath == null) {
                        // Fallback logic: check for common map/gps keys
                        thumbnailPath = props.getProperty("img_A3.1 Site Location_Vicinity Map")
                            ?: props.getProperty("img_Site Location_Vicinity Map")
                                    ?: props.getProperty("img_Section 1_GPS Reading")
                                    ?: props.getProperty("mainImagePath")
                    }

                    val thumbnailUri = if (thumbnailPath != null) {
                        val imgFile = File(folder, thumbnailPath)
                        if (imgFile.exists()) Uri.fromFile(imgFile) else null
                    } else null

                    val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(lastUpdated))
                    History(
                        title = siteName,
                        date = date,
                        folderName = folder.name,
                        telco = telco,
                        surveyType = type,
                        address = address,
                        thumbnailUri = thumbnailUri
                    )
                        .let { history -> lastUpdated to history }
                } else null
            }
                .filter { it.first > recentClearedBefore }
                .sortedByDescending { it.first }
                .take(5)
                .map { it.second }

            withContext(Dispatchers.Main) {
                existingFolders = folders
                    .sortedByDescending { maxOf(it.lastModified(), File(it, "metadata.properties").lastModified()) }
                    .map { it.name }
                recentSurveys = surveys
            }
        }
    }

    val brands = remember {
        listOf(
            Brand("Globe", image = R.drawable.globe_logo),
            Brand("Smart", image = R.drawable.smart_logo),
            Brand("Dito", image = R.drawable.dito),
            Brand("Neutral", icon = Icons.Default.CellTower),
            Brand("Matsi", icon = Icons.Default.Business),
            Brand("Wifi", icon = Icons.Default.Wifi),
        )
    }

    LaunchedEffect(recentClearedBefore, tssrFolder, wifiFolder, matsiFolder) {
        refreshFolders()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    "ISDP Settings",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                NavigationDrawerItem(
                    label = { Text("Toggle Theme") },
                    selected = false,
                    onClick = {
                        onThemeToggle()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.WbSunny, contentDescription = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Surveys", modifier = Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.labelMedium)
                NavigationDrawerItem(
                    label = { Text("TSSR Survey") },
                    selected = false,
                    onClick = {
                        onNavigateToTSSR(tssrFolder, "Neutral")
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.CellTower, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Wifi Survey") },
                    selected = false,
                    onClick = {
                        onNavigateToTSSR(tssrFolder, "Wifi")
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Wifi, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Matsi Survey") },
                    selected = false,
                    onClick = {
                        onNavigateToTSSR(tssrFolder, "Matsi")
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Business, null) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Column {
                        Text("Selected Project Folder")
                        if (tssrFolder != null) Text(tssrFolder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        else Text("None", style = MaterialTheme.typography.bodySmall)
                    } },
                    selected = false,
                    onClick = {
                        refreshFolders()
                        showFolderSelect = true
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Folder, null) }
                )

                if (tssrFolder != null) {
                    NavigationDrawerItem(
                        label = { Text("Clear Folder Selection") },
                        selected = false,
                        onClick = {
                            onTssrFolderChange(null)
                            onWifiFolderChange(null)
                            onMatsiFolderChange(null)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Delete, null) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Text("Logout") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Text(
                                text = "ISDP",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            brands.take(3).forEach { brand ->
                                BrandBox(
                                    modifier = Modifier.weight(1f),
                                    brand = brand,
                                    onClick = {
                                        onNavigateToTSSR(null, brand.name)
                                    })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            brands.drop(3).take(3).forEach { brand ->
                                BrandBox(
                                    modifier = Modifier.weight(1f),
                                    brand = brand,
                                    onClick = {
                                        onNavigateToTSSR(null, brand.name)
                                    })
                            }
                            repeat(3 - brands.drop(3).take(3).size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 16.dp, 16.dp, 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Surveys",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (recentSurveys.isNotEmpty()) {
                            TextButton(onClick = {
                                val clearedAt = System.currentTimeMillis()
                                prefs.edit().putLong("recent_clear_before", clearedAt).apply()
                                recentClearedBefore = clearedAt
                                recentSurveys = emptyList()
                            }) {
                                Text("Clear Recent")
                            }
                        }
                    }
                }

                if (recentSurveys.isEmpty()) {
                    item {
                        Text(
                            "No recent surveys found.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(recentSurveys) { survey ->
                        ListItem(
                            headlineContent = { Text(survey.title) },
                            supportingContent = {
                                Column {
                                    Text("${survey.telco} • ${survey.date}")
                                    if (survey.address.isNotEmpty()) {
                                        Text(
                                            survey.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                if (survey.thumbnailUri != null) {
                                    AsyncImage(
                                        model = survey.thumbnailUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.History, null)
                                    }
                                }
                            },
                            trailingContent = {
                                Icon(Icons.Default.ChevronRight, null)
                            },
                            modifier = Modifier.clickable {
                                onTssrFolderChange(survey.folderName)
                                onWifiFolderChange(survey.folderName)
                                onMatsiFolderChange(survey.folderName)
                                onNavigateToTSSR(survey.folderName, survey.surveyType)
                            }
                        )
                    }
                }
            }
        }

        if (showFolderSelect) {
            FolderSelectionDialog(
                title = "Select Project Folder",
                folders = existingFolders,
                onFolderSelected = {
                    onTssrFolderChange(it)
                    onWifiFolderChange(it)
                    onMatsiFolderChange(it)
                    showFolderSelect = false
                },
                onFolderCreated = { folderName ->
                    scope.launch(Dispatchers.IO) {
                        FileUtils.createProjectFolder(folderName)
                        refreshFolders()
                    }
                },
                onDismiss = { showFolderSelect = false }
            )
        }
    }
}

@Composable
fun FolderSelectionDialog(
    title: String,
    folders: List<String>,
    onFolderSelected: (String) -> Unit,
    onFolderCreated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title)
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Create New Folder")
                }
            }
        },
        text = {
            Column {
                if (showCreateDialog) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("New Folder Name") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (newFolderName.isNotBlank()) {
                                    onFolderCreated(newFolderName)
                                    newFolderName = ""
                                    showCreateDialog = false
                                }
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Confirm")
                            }
                        }
                    )
                }

                if (folders.isEmpty()) {
                    Text("No folders found with metadata.properties in SMS_ISDP_Surveys")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(folders) { folder ->
                            ListItem(
                                headlineContent = { Text(folder) },
                                leadingContent = { Icon(Icons.Default.Folder, null) },
                                modifier = Modifier.clickable { onFolderSelected(folder) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BrandBox(modifier: Modifier = Modifier, brand: Brand, onClick: () -> Unit = {}) {
    Surface(modifier = modifier.aspectRatio(1f), shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, shadowElevation = 4.dp, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            brand.image?.let { imageRes ->
                Image(painter = painterResource(id = imageRes), contentDescription = brand.name, modifier = Modifier.size(32.dp))
            } ?: brand.icon?.let { icon ->
                Icon(icon, contentDescription = brand.name, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(text = brand.name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
