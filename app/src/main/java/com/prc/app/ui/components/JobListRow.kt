package com.prc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.Job

/**
 * Compact vertical listing row (template's .list-row): category accent bar,
 * title + poster + meta chips, pay/timestamp right column. Tap opens details.
 */
@Composable
fun JobListRow(
    job: Job,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // accent bar color-coded by category
        Box(
            Modifier
                .width(3.dp)
                .height(44.dp)
                .background(categoryAccent(job.category), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                job.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                job.postedBy,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${job.location} • ${job.type}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (job.deadlineDays != null && job.deadlineDays <= 3) {
                    Text(
                        "• Closes in ${job.deadlineDays}d",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (trailing != null) {
                trailing()
            } else {
                Text(
                    job.pay.ifBlank { "Pay not stated" },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (job.pay.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    job.postedAgo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Stable accent colors per category (template's accent-bar mapping). */
fun categoryAccent(category: String): androidx.compose.ui.graphics.Color =
    when (category) {
        "IT & Software" -> androidx.compose.ui.graphics.Color(0xFF3B5BDB)
        "Medicine & Health" -> androidx.compose.ui.graphics.Color(0xFF0F5C4E)
        "Human Resources" -> androidx.compose.ui.graphics.Color(0xFF5F4B8B)
        "Finance & Accounting" -> androidx.compose.ui.graphics.Color(0xFF1D6F8C)
        "Sales & Marketing" -> androidx.compose.ui.graphics.Color(0xFFB65C2E)
        "Education" -> androidx.compose.ui.graphics.Color(0xFF7A5C3E)
        "Engineering" -> androidx.compose.ui.graphics.Color(0xFF4A6B8A)
        "Law & Governance" -> androidx.compose.ui.graphics.Color(0xFF4B5A55)
        "Agriculture" -> androidx.compose.ui.graphics.Color(0xFF7A9D54)
        "Hospitality & Tourism" -> androidx.compose.ui.graphics.Color(0xFFD9A324)
        "Logistics & Supply Chain" -> androidx.compose.ui.graphics.Color(0xFF8C5E2A)
        "Media & Creative" -> androidx.compose.ui.graphics.Color(0xFFB03A6B)
        "Retail" -> androidx.compose.ui.graphics.Color(0xFF2F6F3E)
        "Domestic" -> androidx.compose.ui.graphics.Color(0xFF6E7A85)
        "Driving" -> androidx.compose.ui.graphics.Color(0xFFD9A324)
        "Construction" -> androidx.compose.ui.graphics.Color(0xFF0F5C4E)
        "Skilled trade" -> androidx.compose.ui.graphics.Color(0xFF5B6473)
        else -> androidx.compose.ui.graphics.Color(0xFF5B6473)
    }
