package com.example.isdp2java.ui.main

import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri

data class Brand(
    val name: String,
    val image: Int? = null,
    val icon: ImageVector? = null
)

data class History(
    val title: String,
    val date: String,
    val folderName: String = "",
    val telco: String = "",
    val surveyType: String = "",
    val address: String = "",
    val thumbnailUri: Uri? = null
)

data class TSSRSectionData(val title: String, val fields: List<String>)
