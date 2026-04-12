package com.example.isdp2java.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isdp2java.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onThemeToggle: () -> Unit,
    onNavigateToTSSR: (String?, String?) -> Unit = { _, _ -> }
) {
    val navIcons = listOf(Icons.Default.History, Icons.Default.Wifi, Icons.Default.Person)
    val navLabels = listOf("History", "Wifi", "Profile")
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var historyItems by remember { mutableStateOf<List<History>>(emptyList()) }

    // Load history from unified SMS_ISDP folder
    LaunchedEffect(showBottomSheet) {
        if (showBottomSheet) {
            val appDir = File(context.getExternalFilesDir(null), "SMS_ISDP")
            if (appDir.exists()) {
                val folders = appDir.listFiles { file -> file.isDirectory }
                val items = folders?.map { folder ->
                    val metadataFile = File(folder, "metadata.properties")
                    var title = folder.name
                    var savedTelco = ""
                    if (metadataFile.exists()) {
                        try {
                            val props = Properties()
                            metadataFile.inputStream().use { props.load(it) }
                            val name = props.getProperty("siteName")
                            if (!name.isNullOrBlank()) title = name
                            savedTelco = props.getProperty("telcoName", "")
                        } catch (e: Exception) {}
                    }
                    
                    val lastModified = Date(folder.lastModified())
                    val date = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(lastModified)
                    
                    History(
                        title = title, 
                        date = date,
                        folderName = folder.name,
                        telco = savedTelco
                    )
                }?.sortedByDescending { it.folderName } ?: emptyList()
                historyItems = items
            }
        }
    }

    val brands = remember {
        listOf(
            Brand("Globe", image = R.drawable.globe_logo),
            Brand("Smart", image = R.drawable.smart_logo),
            Brand("Dito", image = R.drawable.dito),
            Brand("Wifi", icon = Icons.Default.Wifi),
        )
    }

    val filteredBrands = remember(searchQuery, brands) {
        if (searchQuery.isEmpty()) brands else brands.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Text(text = "ISDP", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                IconButton(onClick = onThemeToggle) {
                    Icon(Icons.Default.WbSunny, contentDescription = "Toggle theme")
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = RoundedCornerShape(50), tonalElevation = 4.dp, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = { showBottomSheet = true }) {
                            Icon(navIcons[0], contentDescription = navLabels[0], tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(16.dp))
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { onNavigateToTSSR(null, "Wifi") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(navIcons[1], contentDescription = navLabels[1], tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(onClick = { /* Handle Profile */ }) {
                            Icon(navIcons[2], contentDescription = navLabels[2], tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Brands") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
                )
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        filteredBrands.take(3).forEach { brand ->
                            BrandBox(modifier = Modifier.weight(1f), brand = brand, onClick = { onNavigateToTSSR(null, brand.name) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        filteredBrands.drop(3).firstOrNull()?.let { brand ->
                            BrandBox(modifier = Modifier.weight(1f), brand = brand, onClick = { onNavigateToTSSR(null, brand.name) })
                            Spacer(modifier = Modifier.weight(2f))
                        }
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
            var historySearchQuery by remember { mutableStateOf("") }
            val filteredHistoryInSheet = remember(historySearchQuery, historyItems) {
                if (historySearchQuery.isEmpty()) historyItems 
                else historyItems.filter { it.title.contains(historySearchQuery, ignoreCase = true) || it.telco.contains(historySearchQuery, ignoreCase = true) }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Site History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                OutlinedTextField(
                    value = historySearchQuery,
                    onValueChange = { historySearchQuery = it },
                    placeholder = { Text("Search History") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = CircleShape
                )
                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) { 
                    items(filteredHistoryInSheet) { history -> 
                        HistoryItem(history = history, onClick = { 
                            showBottomSheet = false
                            onNavigateToTSSR(history.folderName, null) 
                        }) 
                    } 
                }
            }
        }
    }
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

@Composable
fun HistoryItem(history: History, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = history.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = history.date, style = MaterialTheme.typography.bodySmall)
            }
            if (history.telco.isNotBlank()) {
                Text(text = "Telco: ${history.telco}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
