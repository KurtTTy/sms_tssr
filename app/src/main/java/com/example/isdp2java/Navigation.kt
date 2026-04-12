package com.example.isdp2java

import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.Composable
import com.example.isdp2java.ui.auth.AuthScreen
import com.example.isdp2java.ui.main.MainScreen
import com.example.isdp2java.ui.main.TSSRScreen

@Composable
fun Navigation(onThemeToggle: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "auth") {
        composable("auth") {
            AuthScreen(onLogin = { navController.navigate("main") })
        }
        composable("main") {
            MainScreen(
                onThemeToggle = onThemeToggle,
                onNavigateToTSSR = { folderName, telco ->
                    var route = "tssr"
                    val args = mutableListOf<String>()
                    if (folderName != null) args.add("folder=$folderName")
                    if (telco != null) args.add("telco=$telco")
                    
                    if (args.isNotEmpty()) {
                        route += "?" + args.joinToString("&")
                    }
                    navController.navigate(route)
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
            TSSRScreen(initialFolder = folder, initialTelco = telco, onBack = { navController.popBackStack() })
        }
    }
}