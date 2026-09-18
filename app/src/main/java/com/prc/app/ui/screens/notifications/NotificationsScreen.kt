package com.prc.app.ui.screens.notifications

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AppNotification
import com.prc.app.data.AuthRepository
import com.prc.app.data.NotificationRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val all by NotificationRepository.notifications.collectAsState()
    val readKeys by NotificationRepository.readBy.collectAsState()
    val me = AuthRepository.currentUser.collectAsState().value
    val list = all.filter { it.recipientContact == me?.contact }

    // Opening (then leaving) this screen clears the unread badge.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { NotificationRepository.markAllRead(me?.contact) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (list.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🔔", fontSize = 44.sp)
                Spacer(Modifier.height(12.dp))
                Text("You're all caught up", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "New applicants and application updates will land here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list, key = { it.id }) { n ->
                val isUnread = NotificationRepository.readKey(me?.contact, n.id) !in readKeys
                NotificationRow(n, isUnread)
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, isUnread: Boolean) {
    val (icon, tint) = when (n.type) {
        "applicant" -> Icons.Filled.Person to MaterialTheme.colorScheme.primary
        "accepted" -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        else -> Icons.Filled.Cancel to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = n.type, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.title,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (isUnread) {
                        Box(
                            Modifier
                                .padding(start = 6.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Text(n.body, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    n.timeAgo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
