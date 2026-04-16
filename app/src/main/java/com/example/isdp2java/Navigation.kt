package com.example.isdp2java

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
    val navController = rememberNavController()

    // Generalized shared folder state
    var sharedFolder by remember { mutableStateOf<String?>(null) }

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
                onNavigateToTSSR = { folderName, telco ->
                    val finalFolder = folderName ?: sharedFolder
                    when (telco) {
                        "Wifi" -> {
                            val route = if (finalFolder != null) "wifi?folder=$finalFolder" else "wifi"
                            navController.navigate(route)
                        }
                        "Matsi" -> {
                            var route = "matsi"
                            val args = mutableListOf<String>()
                            if (finalFolder != null) args.add("folder=$finalFolder")
                            if (telco != null) args.add("telco=$telco")
                            if (args.isNotEmpty()) route += "?" + args.joinToString("&")
                            navController.navigate(route)
                        }
                        else -> {
                            var route = "tssr"
                            val args = mutableListOf<String>()
                            if (finalFolder != null) args.add("folder=$finalFolder")
                            if (telco != null) args.add("telco=$telco")
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
                initialFolder = folder ?: sharedFolder,
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
                initialFolder = folder ?: sharedFolder,
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
                initialFolder = folder ?: sharedFolder,
                onBack = { navController.popBackStack() },
                onFolderChange = { sharedFolder = it }
            )
        }
    }
}
