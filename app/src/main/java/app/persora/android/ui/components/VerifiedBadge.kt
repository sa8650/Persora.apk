package app.persora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Facebook-style email check: blue during an active paid term, muted gray otherwise. */
@Composable
fun VerifiedBadge(activePlan: Boolean, size: Dp = 18.dp) {
    val fill = if (activePlan) Color(0xFF1877F2) else Color(0xFFA8B0BB)
    Box(
        Modifier.size(size).background(fill, CircleShape)
            .semantics { contentDescription = if (activePlan) "Verified email, active paid plan" else "Verified email, no active paid plan" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.68f))
    }
}
