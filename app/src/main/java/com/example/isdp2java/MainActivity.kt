package com.example.isdp2java

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.isdp2java.ui.theme.ISDP2JAVATheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            checkAndRequestManageStorage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (requiredPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            checkAndRequestManageStorage()
        } else {
            requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
        }

        setContent {
            var darkTheme by remember { mutableStateOf(false) }

            ISDP2JAVATheme(darkTheme = darkTheme) {
                Navigation(onThemeToggle = { darkTheme = !darkTheme })
            }
        }
    }

    private fun checkAndRequestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", packageName))
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(intent)
                }
            }
        }
    }
}
