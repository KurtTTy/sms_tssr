package com.example.isdp2java.utils

import android.content.Context
import android.graphics.*
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object FileUtils {

    suspend fun getAddressFromLocation(context: Context, latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.awaitFromLocation(latitude, longitude, 1)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
            }
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                "${addr.locality ?: ""}, ${addr.adminArea ?: ""}, ${addr.countryName ?: ""}\n${addr.getAddressLine(0) ?: ""}"
            } else "Address not found"
        } catch (e: Exception) {
            "Address detection failed"
        }
    }

    private suspend fun Geocoder.awaitFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int
    ): List<Address>? = suspendCancellableCoroutine { continuation ->
        getFromLocation(latitude, longitude, maxResults, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (continuation.isActive) continuation.resume(addresses)
            }

            override fun onError(errorMessage: String?) {
                if (continuation.isActive) continuation.resume(null)
            }
        })
    }

    fun addOverlayToImage(context: Context, uri: Uri, siteName: String, lat: String, lng: String, address: String): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return uri
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            inputStream.close()

            var originalBitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return uri
            
            // Fix orientation
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            if (orientation != ExifInterface.ORIENTATION_UNDEFINED && orientation != ExifInterface.ORIENTATION_NORMAL) {
                val rotated = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                if (rotated != originalBitmap) originalBitmap.recycle()
                originalBitmap = rotated
            }

            val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = originalBitmap.height / 45f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }
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
            
            context.contentResolver.openOutputStream(uri)?.use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            if (originalBitmap != mutableBitmap) originalBitmap.recycle()
            mutableBitmap.recycle()
            uri
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to add overlay", e)
            uri
        }
    }

    fun getSurveyFolder(context: Context, surveyType: String, siteName: String, sessionTimestamp: String, customFolderName: String?): File? {
        val rootDir = Environment.getExternalStorageDirectory()
        val baseDir = File(rootDir, "SMS_ISDP_Surveys")
        
        if (!baseDir.exists()) baseDir.mkdirs()

        val parentFolderName = if (!customFolderName.isNullOrBlank()) {
            customFolderName
        } else {
            val sanitizedSiteName = siteName.ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            "${sanitizedSiteName}_$sessionTimestamp"
        }
        
        val dir = File(baseDir, parentFolderName)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e("FileUtils", "Failed to create directory: ${dir.absolutePath}")
            return null
        }

        // Ensure metadata exists for newly created folders
        if (!File(dir, "metadata.properties").exists()) {
            val props = Properties()
            props.setProperty("siteName", siteName.ifBlank { parentFolderName })
            props.setProperty("timestamp", sessionTimestamp)
            props.setProperty("surveyType", surveyType)
            saveMetadata(dir, props)
        }
        
        return dir
    }

    fun createProjectFolder(folderName: String): File? {
        val rootDir = Environment.getExternalStorageDirectory()
        val baseDir = File(rootDir, "SMS_ISDP_Surveys")
        if (!baseDir.exists()) baseDir.mkdirs()
        
        val folder = File(baseDir, folderName)
        if (!folder.exists() && !folder.mkdirs()) return null
        
        val props = Properties()
        props.setProperty("siteName", folderName)
        props.setProperty("timestamp", SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()))
        saveMetadata(folder, props)
        
        return folder
    }

    fun createSurveyFile(context: Context, surveyType: String, siteName: String, section: String, fieldName: String, sessionTimestamp: String, customFolderName: String?, suffix: String = ""): File? {
        try {
            val sanitizedSection = section.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val sanitizedFieldName = fieldName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val time = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
            
            val folder = getSurveyFolder(context, surveyType, siteName, sessionTimestamp, customFolderName) ?: return null
            val subDir = when (surveyType) {
                "TSSR" -> File(folder, "TSSR/$sanitizedSection")
                "Wifi" -> File(folder, "Wifi/$sanitizedSection")
                "Matsi" -> File(folder, "Matsi/$sanitizedSection")
                else -> File(folder, sanitizedSection)
            }
            if (!subDir.exists() && !subDir.mkdirs()) return null
            
            val prefix = when (surveyType) {
                "TSSR" -> "TSSR_"
                "Wifi" -> "Wifi_"
                "Matsi" -> "Matsi_"
                else -> ""
            }
            
            return File(subDir, "${prefix}${sanitizedSection}_${sanitizedFieldName}_${date}_$time${suffix}.jpg").apply {
                if (!exists()) createNewFile()
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to create file", e)
            return null
        }
    }

    fun saveMetadata(folder: File, properties: Properties, comment: String = "Survey Metadata") {
        try {
            val metadataFile = File(folder, "metadata.properties")
            metadataFile.outputStream().use { properties.store(it, comment) }
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to save metadata", e)
        }
    }

    fun findImageRelativePath(siteFolder: File, uri: Uri): String? {
        // Since we know our structure: SiteFolder / (TSSR|Wifi|Matsi) / Section / File
        // We can search for the file by name in subdirectories
        val fileName = uri.lastPathSegment ?: return null
        
        fun searchRecursively(currentDir: File): String? {
            val files = currentDir.listFiles() ?: return null
            for (file in files) {
                if (file.isDirectory) {
                    val found = searchRecursively(file)
                    if (found != null) return found
                } else if (file.name == fileName) {
                    return file.absolutePath.substringAfter(siteFolder.absolutePath).removePrefix(File.separator)
                }
            }
            return null
        }
        
        return searchRecursively(siteFolder)
    }

    fun loadMetadata(folder: File): Properties? {
        val metadataFile = File(folder, "metadata.properties")
        if (!metadataFile.exists()) return null
        return try {
            val props = Properties()
            metadataFile.inputStream().use { props.load(it) }
            props
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to load metadata", e)
            null
        }
    }
}
