package com.example.isdp2java

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.*
import com.example.isdp2java.ui.auth.AuthScreen
import com.example.isdp2java.ui.main.MainScreen
import com.example.isdp2java.ui.main.TSSRScreen
import com.example.isdp2java.ui.main.WifiScreen
import com.example.isdp2java.ui.main.MatsiScreen

@Composable
fun Navigation(onThemeToggle: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val navController = rememberNavController()

    // Generalized shared folder state
    var sharedFolder by remember { 
        mutableStateOf(sharedPrefs.getString("last_active_folder", null)) 
    }

    // Persist sharedFolder whenever it changes
    LaunchedEffect(sharedFolder) {
        sharedPrefs.edit().putString("last_active_folder", sharedFolder).apply()
    }

    NavHost(navController = navController, startDestination = "auth") {
        composable("auth") {
            AuthScreen(onLogin = { 
                navController.navigate("main") {
                    popUpTo("auth") { inclusive = true }
                }
            })
        }
        composable("main") {
            MainScreen(
                onThemeToggle = onThemeToggle,
                onLogout = {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                    }
                },
                tssrFolder = sharedFolder,
                wifiFolder = sharedFolder,
                matsiFolder = sharedFolder,
                onTssrFolderChange = { sharedFolder = it },
                onWifiFolderChange = { sharedFolder = it },
                onMatsiFolderChange = { sharedFolder = it },
                onNavigateToTSSR = { folderName, typeOrBrand ->
                    when (typeOrBrand) {
                        "Wifi" -> {
                            val route = if (folderName != null) "wifi?folder=$folderName" else "wifi"
                            navController.navigate(route)
                        }
                        "Matsi" -> {
                            var route = "matsi"
                            val args = mutableListOf<String>()
                            if (folderName != null) args.add("folder=$folderName")
                            if (args.isNotEmpty()) route += "?" + args.joinToString("&")
                            navController.navigate(route)
                        }
                        else -> {
                            var route = "tssr"
                            val args = mutableListOf<String>()
                            if (folderName != null) args.add("folder=$folderName")
                            if (typeOrBrand != null && typeOrBrand != "TSSR" && typeOrBrand != "Survey") {
                                args.add("telco=$typeOrBrand")
                            }
                            if (args.isNotEmpty()) route += "?" + args.joinToString("&")
                            navController.navigate(route)
                        }
                    }
                }
            )
        }
        composable(
            route = "tssr?folder={folder}&telco={telco}",
            arguments = listOf(
                navArgument("folder") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("telco") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folder = backStackEntry.arguments?.getString("folder")
            val telco = backStackEntry.arguments?.getString("telco")
            TSSRScreen(
                initialFolder = folder,
                initialTelco = telco,
                onBack = { navController.popBackStack() },
                onFolderChange = { sharedFolder = it }
            )
        }
        composable(
            route = "matsi?folder={folder}&telco={telco}",
            arguments = listOf(
                navArgument("folder") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("telco") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folder = backStackEntry.arguments?.getString("folder")
            val telco = backStackEntry.arguments?.getString("telco")
            MatsiScreen(
                initialFolder = folder,
                initialTelco = telco,
                onBack = { navController.popBackStack() },
                onFolderChange = { sharedFolder = it }
            )
        }
        composable(
            route = "wifi?folder={folder}",
            arguments = listOf(
                navArgument("folder") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folder = backStackEntry.arguments?.getString("folder")
            WifiScreen(
                initialFolder = folder,
                onBack = { navController.popBackStack() },
                onFolderChange = { sharedFolder = it }
            )
        }
    }
}
