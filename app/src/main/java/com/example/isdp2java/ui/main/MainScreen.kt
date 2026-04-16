package com.example.isdp2java.ui.main

import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.isdp2java.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.isdp2java.utils.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onThemeToggle: () -> Unit,
    tssrFolder: String?,
    wifiFolder: String?,
    matsiFolder: String?,
    onTssrFolderChange: (String?) -> Unit,
    onWifiFolderChange: (String?) -> Unit,
    onMatsiFolderChange: (String?) -> Unit,
    onNavigateToTSSR: (String?, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showFolderSelect by remember { mutableStateOf(false) }
    var existingFolders by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshFolders() {
        scope.launch(Dispatchers.IO) {
            val rootDir = Environment.getExternalStorageDirectory()
            val baseDir = File(rootDir, "SMS_ISDP_Surveys")
            if (!baseDir.exists()) baseDir.mkdirs()
            val folders = baseDir.listFiles { file -> file.isDirectory && File(file, "metadata.properties").exists() }?.map { it.name } ?: emptyList()
            withContext(Dispatchers.Main) {
                existingFolders = folders
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
                                        onNavigateToTSSR(tssrFolder, brand.name) 
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
                                        onNavigateToTSSR(tssrFolder, brand.name) 
                                    })
                            }
                            repeat(3 - brands.drop(3).take(3).size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                
                if (tssrFolder != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, null)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Active Project:", style = MaterialTheme.typography.labelSmall)
                                    Text(tssrFolder, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
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
            if (brand.image != null) Image(painter = painterResource(id = brand.image!!), contentDescription = brand.name, modifier = Modifier.size(32.dp))
            else if (brand.icon != null) Icon(brand.icon!!, contentDescription = brand.name, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(text = brand.name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
