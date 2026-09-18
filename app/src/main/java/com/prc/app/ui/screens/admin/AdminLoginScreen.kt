package com.prc.app.ui.screens.admin

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AdminRepository
import com.prc.app.ui.screens.auth.AuthBackCircle
import com.prc.app.ui.screens.auth.AuthField
import com.prc.app.ui.screens.auth.AuthHeadline
import com.prc.app.ui.screens.auth.AuroraBackdrop
import com.prc.app.ui.screens.auth.OnAurora
import com.prc.app.ui.screens.auth.auroraAccent
import com.prc.app.ui.screens.auth.auroraIsDark
import com.prc.app.ui.screens.auth.auroraSurface
import com.prc.app.ui.screens.auth.auroraGoldAccent
import com.prc.app.ui.screens.auth.PrimaryAuthButton

@Composable
fun AdminLoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit
) {
        SystemBarAppearance(darkIcons = !auroraIsDark())

    var mode by remember { mutableStateOf("login") }   // login | register
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error != null) {
            shake.animateTo(0f, tween(10))
            repeat(4) {
                shake.animateTo(14f, tween(55))
                shake.animateTo(-14f, tween(55))
            }
            shake.animateTo(0f, tween(55))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AuroraBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .graphicsLayer { translationX = shake.value }
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuthBackCircle(onBack = onBack)
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))

            // shield mark, distinct from the user-side briefcase
            AdminMark()
            Spacer(Modifier.height(22.dp))
            AuthHeadline(
                title = if (mode == "login") "Admin portal" else "Register admin",
                body = if (mode == "login")
                    "Paste provider jobs and approve user applications here."
                else
                    "Create the administrator account for this portal."
            )
            Spacer(Modifier.height(24.dp))

            // ---- mode toggle ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ModeChip("Log in", mode == "login", Modifier.weight(1f)) { mode = "login"; error = null }
                ModeChip("Sign up as admin", mode == "register", Modifier.weight(1f)) { mode = "register"; error = null }
            }
            Spacer(Modifier.height(20.dp))

            if (mode == "register") {
                AuthField(
                    value = fullName,
                    onValueChange = { fullName = it; error = null },
                    label = "Full name",
                    icon = Icons.Filled.AdminPanelSettings,
                    entranceKey = "admin-name",
                    entranceDelay = 60
                )
                Spacer(Modifier.height(14.dp))
            }

            AuthField(
                value = email,
                onValueChange = {
                    email = it
                    error = null
                },
                label = "Admin email",
                icon = Icons.Filled.Call,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                entranceKey = "admin-email",
                entranceDelay = 120
            )
            Spacer(Modifier.height(16.dp))

            AuthField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = "Password",
                icon = Icons.Filled.Lock,
                isPassword = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            modifier = Modifier.size(17.dp),
                            tint = OnAurora().copy(alpha = 0.6f)
                        )
                    }
                },
                entranceKey = "admin-pass",
                entranceDelay = 220
            )

            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    color = if (auroraIsDark()) Color(0xFFFF8A7A) else Color(0xFFC2452F),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(22.dp))
            PrimaryAuthButton(
                if (mode == "login") "Enter portal" else "Create admin account",
                enabled = email.isNotBlank() && password.isNotBlank(),
                loading = loading
            ) {
                loading = true
                if (mode == "login") {
                    AdminRepository.login(email, password) { message ->
                        loading = false
                        if (message == null) onLoginSuccess() else error = message
                    }
                } else {
                    AdminRepository.registerAdmin(fullName, email, password) { message ->
                        loading = false
                        if (message == null) onLoginSuccess() else error = message
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (selected) auroraAccent().copy(alpha = 0.18f) else auroraSurface(0.05f),
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (selected) auroraAccent().copy(alpha = 0.8f) else OnAurora().copy(alpha = 0.14f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) auroraAccent() else OnAurora().copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun AdminMark() {
    // Same tile language as AuthMark but with the admin shield glyph
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(54.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(auroraAccent(), auroraAccent().copy(alpha = 0.72f))
                    ),
                    RoundedCornerShape(15.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AdminPanelSettings,
                contentDescription = null,
                modifier = Modifier.size(27.dp),
                tint = Color.White
            )
        }
    }
}