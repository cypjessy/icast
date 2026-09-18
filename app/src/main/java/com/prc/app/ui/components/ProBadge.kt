package com.prc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ProGold = Color(0xFFD9A324)

/**
 * Small gold "Pro" badge (premium medal) to sit next to a user's name or
 * avatar when their subscription is active. Size scales with [size].
 */
@Composable
fun ProBadge(size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = ProGold,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = "Pro subscriber",
                tint = Color.White,
                modifier = Modifier.size(size * 0.62f)
            )
        }
    }
}

/** Gold ring/edge used behind avatars of Pro subscribers. */
@Composable
fun ProAvatarRing(size: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size + 5.dp)
            .background(ProGold, CircleShape)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .align(Alignment.Center)
        ) { content() }
    }
}
