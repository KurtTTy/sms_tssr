package com.example.isdp2java.ui.auth

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

import androidx.compose.foundation.background
import com.example.isdp2java.ui.theme.PrimaryGradient

@Composable
fun AuthScreen(onLogin: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LoginTab(onLogin)
            }
        }
    }
}

@Composable
fun LoginTab(onLogin: () -> Unit) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val savedRememberMe = sharedPreferences.getBoolean("remember_me", false)
        rememberMe = savedRememberMe
        if (savedRememberMe) {
            username = sharedPreferences.getString("username", "") ?: ""
            password = sharedPreferences.getString("password", "") ?: ""
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = username,
            onValueChange = { username = it; hasError = false },
            label = { Text("Email or Username") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            isError = hasError
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = password,
            onValueChange = { password = it; hasError = false },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            isError = hasError
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                Text("Remember me")
            }
            TextButton(onClick = { /*TODO*/ }) {
                Text("Forgot?")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val validUsers = mapOf(
                    "SMSGT-WAERO-0" to "Waero@0",
                    "SMSGT-WAERO-01" to "Waero@01",
                    "SMSGT-WAERO-02" to "Waero@02",
                    "SMSGT-WAERO-03" to "Waero@03",
                    "SMSGT-WAERO-04" to "Waero@04",
                    "SMSGT-WAERO-05" to "Waero@05"
                )

                if (validUsers[username] == password) {
                    sharedPreferences.edit {
                        if (rememberMe) {
                            putString("username", username)
                            putString("password", password)
                            putBoolean("remember_me", true)
                        } else {
                            remove("username")
                            remove("password")
                            putBoolean("remember_me", false)
                        }
                    }
                    onLogin()
                } else {
                    hasError = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(PrimaryGradient, shape = RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Text("Login", fontSize = 18.sp, color = Color.White)
        }

        if (hasError) {
            Text(
                text = "Invalid username or password",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

