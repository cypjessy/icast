package com.prc.app.ui.screens.auth

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import kotlinx.coroutines.launch

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val PHONE_REGEX = Regex("^\\+?[0-9]{9,15}$")   // 9-15 digits, optional + prefix

@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    SystemBarAppearance(darkIcons = !auroraIsDark())

    var fullName by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var agreed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // ---- live validation signals ----
    val contactKey = contact.trim()
    val contactValid = when {
        contactKey.isEmpty() -> null
        else -> EMAIL_REGEX.matches(contactKey)
    }
    val phoneKey = phone.filter { c -> c.isDigit() || c == '+' }
    val phoneValid = when {
        phoneKey.isEmpty() -> null
        else -> PHONE_REGEX.matches(phoneKey)
    }
    val passwordsMatch = confirmPassword.isEmpty() || password == confirmPassword
    val strength = passwordStrength(password)
    val canSubmit = fullName.trim().length >= 2 && contactValid == true &&
            phoneValid == true &&
            password.length >= 8 && passwordsMatch && agreed && !loading

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
        ) {
            Spacer(Modifier.height(14.dp))
            AuthBackCircle(onBack = onNavigateToLogin)
            Spacer(Modifier.height(18.dp))
            AuthHeadline(
                title = "Create your account",
                body = "Takes less than a minute — start applying to jobs right after."
            )
            Spacer(Modifier.height(24.dp))

            AuthField(
                value = fullName,
                onValueChange = { fullName = it; error = null },
                label = "Full name",
                icon = Icons.Filled.Person,
                entranceKey = "f-name",
                entranceDelay = 60
            )
            Spacer(Modifier.height(16.dp))

            AuthField(
                value = contact,
                onValueChange = { contact = it; error = null },
                label = "Email address",
                icon = Icons.Filled.AlternateEmail,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                ),
                entranceKey = "f-contact",
                entranceDelay = 0
            )
            if (contactValid == false && contactKey.length > 3) {
                Text(
                    "Enter a valid email address",
                    fontSize = 11.sp,
                    color = if (auroraIsDark()) Color(0xFFFF8A7A) else Color(0xFFC2452F),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            AuthField(
                value = phone,
                onValueChange = { phone = it; error = null },
                label = "Phone number",
                icon = Icons.Filled.Call,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                ),
                entranceKey = "f-phone",
                entranceDelay = 60
            )
            if (phoneValid == false && phoneKey.length > 3) {
                Text(
                    "Enter a valid phone number (e.g. 0712345678)",
                    fontSize = 11.sp,
                    color = if (auroraIsDark()) Color(0xFFFF8A7A) else Color(0xFFC2452F),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            AuthField(
                value = password,
                onValueChange = { password = it; error = null },
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
                entranceKey = "f-pass",
                entranceDelay = 180
            )
            // ---- strength meter ----
            if (password.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                ) {
                    repeat(4) { i ->
                        val filled = strength.level > i
                        val barColor by animateColorAsState(
                            when {
                                !filled -> OnAurora().copy(alpha = 0.15f)
                                strength.level <= 1 -> Color(0xFFC2452F)
                                strength.level == 2 -> Color(0xFFD9A324)
                                else -> Color(0xFF2E9E6B)
                            },
                            label = "strengthBar$i"
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .padding(end = 4.dp)
                                .background(barColor, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        strength.label,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnAurora().copy(alpha = 0.55f)
                    )
                }
                if (password.length < 8) {
                    Text(
                        "Use at least 8 characters",
                        fontSize = 11.sp,
                        color = OnAurora().copy(alpha = 0.45f),
                        modifier = Modifier.padding(start = 4.dp, top = 5.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            AuthField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = "Confirm password",
                icon = Icons.Filled.Lock,
                isPassword = true,
                visualTransformation = PasswordVisualTransformation(),
                entranceKey = "f-confirm",
                entranceDelay = 240
            )
            if (!passwordsMatch) {
                Text(
                    "Passwords don't match",
                    fontSize = 11.sp,
                    color = if (auroraIsDark()) Color(0xFFFF8A7A) else Color(0xFFC2452F),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }
            Spacer(Modifier.height(20.dp))

            // ---- terms checkbox with spring pop ----
            val checkScale by animateFloatAsState(
                targetValue = if (agreed) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "checkPop"
            )
            val checkColor by animateColorAsState(
                if (agreed) auroraAccent() else Color.Transparent,
                label = "checkColor"
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { agreed = !agreed; error = null }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(checkColor, CircleShape)
                        .border(
                            1.5.dp,
                            if (agreed) auroraAccent() else OnAurora().copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(12.dp)
                            .scale(checkScale)
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    "I agree to PRC Jobs' Terms of Service and Privacy Policy",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = OnAurora().copy(alpha = 0.6f)
                )
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    color = if (auroraIsDark()) Color(0xFFFF8A7A) else Color(0xFFC2452F),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            PrimaryAuthButton("Create account", enabled = canSubmit, loading = loading) {
                // final guard
                when {
                    fullName.trim().length < 2 -> error = "Please enter your full name"
                    contactValid != true ->
                        error = "Please enter a valid email address"
                    phoneValid != true ->
                        error = "Please enter a valid phone number (e.g. 0712345678)"
                    password.length < 8 -> error = "Password must be at least 8 characters"
                    password != confirmPassword -> error = "Passwords don't match"
                    !agreed -> error = "Please agree to the Terms of Service and Privacy Policy"
                    else -> {
                        loading = true
                        scope.launch {
                            AuthRepository.signUp(contact.trim(), password, fullName.trim(), phone = phoneKey) { result ->
                                loading = false
                                result.fold(
                                    onSuccess = { onSignUpSuccess() },
                                    onFailure = { e -> error = e.message }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            OrDivider()
            Spacer(Modifier.height(8.dp))
            GoogleAuthButton { /* mock — no backend yet */ }

            Spacer(Modifier.height(22.dp))
            BottomAuthLink("Already have an account?", "Log in", onNavigateToLogin)
            Spacer(Modifier.height(24.dp))
        }
    }
}


private data class PwStrength(val level: Int, val label: String)

private fun passwordStrength(pw: String): PwStrength {
    if (pw.isEmpty()) return PwStrength(0, "")
    var score = 0
    if (pw.length >= 8) score++
    if (pw.length >= 12) score++
    if (pw.any { it.isDigit() } && pw.any { it.isLetter() }) score++
    if (pw.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> PwStrength(1, "Weak")
        score == 2 -> PwStrength(2, "Good")
        else -> PwStrength(3, "Strong")
    }
}

