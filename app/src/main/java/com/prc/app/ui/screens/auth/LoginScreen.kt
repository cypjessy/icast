package com.prc.app.ui.screens.auth

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.prc.app.data.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgot: () -> Unit
) {
        SystemBarAppearance(darkIcons = !auroraIsDark())

    var contact by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val emailValid = remember(contact) {
        Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(contact.trim())
    }

    val canSubmit = emailValid && password.length >= 6 && !loading

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
            AuthMark()
            Spacer(Modifier.height(22.dp))
            AuthHeadline(
                title = "Welcome back",
                body = "Log in to keep track of your applications and saved jobs."
            )
            Spacer(Modifier.height(30.dp))

            AuthField(
                value = contact,
                onValueChange = {
                    contact = it
                    error = null
                },
                label = "Email address",
                icon = Icons.Filled.AlternateEmail,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                entranceKey = "login-contact",
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
                entranceKey = "login-pass",
                entranceDelay = 220
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "Forgot password?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = auroraGoldAccent(),
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable { onNavigateToForgot() }
                )
            }

            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    color = if (auroraIsDark()) androidx.compose.ui.graphics.Color(0xFFFF8A7A) else androidx.compose.ui.graphics.Color(0xFFC2452F),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(12.dp))
            PrimaryAuthButton("Log in", enabled = canSubmit, loading = loading) {
                loading = true
                error = null
                AuthRepository.login(contact.trim(), password) { result ->
                    loading = false
                    result.fold(
                        onSuccess = { onLoginSuccess() },
                        onFailure = { e ->
                            error = e.message
                            Toast.makeText(context, e.message ?: "Log in failed", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            OrDivider()
            Spacer(Modifier.height(8.dp))
            GoogleAuthButton {
                Toast.makeText(context, "Google sign-in comes later", Toast.LENGTH_SHORT).show()
            }

            Spacer(Modifier.height(24.dp))
            BottomAuthLink("Don't have an account?", "Sign up", onNavigateToSignUp)
            Spacer(Modifier.height(24.dp))
        }
    }
}
