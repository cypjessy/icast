package com.prc.app.ui.screens.company

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.Job
import com.prc.app.data.JobRepository
import com.prc.app.ui.screens.auth.AuroraGold
import com.prc.app.ui.theme.SystemBarAppearance
import com.prc.app.ui.components.JobListRow

/** Brand avatar colour, mirroring the home palette. */
private val brandPalette = listOf(
    Color(0xFF161616), Color(0xFF2E2E2E), Color(0xFF0F5C4E),
    Color(0xFF2F6F3E), Color(0xFF4B5A55), Color(0xFFB65C2E)
)
private fun brandColor(name: String): Color =
    brandPalette[kotlin.math.abs(name.hashCode()) % brandPalette.size]

@Composable
fun CompanyJobsScreen(
    companyName: String,
    onBack: () -> Unit,
    onJobClick: (Int) -> Unit
) {
    SystemBarAppearance(darkIcons = false, statusBarColor = brandColor(companyName))

    val jobs by JobRepository.jobs.collectAsState()
    val companyJobs = jobs
        .filter { !it.draft && !it.closed && it.postedBy == companyName }
        .sortedByDescending { it.postedAtMillis }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // header with company gradient
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            brandColor(companyName),
                            brandColor(companyName).copy(alpha = 0.72f)
                        )
                    )
                )
        ) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(58.dp)
                            .background(Color.White.copy(alpha = 0.22f), CircleShape)
                    ) {
                        Text(
                            companyName.split(" ").filter { it.isNotBlank() }.take(2)
                                .map { it.first().uppercase() }.joinToString(""),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            companyName,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${companyJobs.size} open position${if (companyJobs.size == 1) "" else "s"}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        if (companyJobs.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.WorkOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No open jobs right now",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Check back later for new openings",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                "Open roles",
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(companyJobs, key = { it.id }) { job ->
                    JobListRow(job) { onJobClick(job.id) }
                }
            }
        }
    }
}
