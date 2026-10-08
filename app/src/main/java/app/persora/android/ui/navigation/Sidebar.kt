package app.persora.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.data.model.AppUser
import app.persora.android.data.model.Sections
import app.persora.android.ui.components.UserAvatar
import app.persora.android.ui.components.VerifiedBadge
import app.persora.android.ui.onboarding.BrandMark
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Bento

/** Tablet / landscape sidebar — the same grouped navigation as .workspace-sidebar on the web. */
@Composable
fun Sidebar(user: AppUser, route: String?, sectionArg: String?, onNavigate: (String) -> Unit) {
    val vault = LocalContext.current.appContainer.vault
    val items by vault.items.collectAsStateWithLifecycle()
    Column(Modifier.width(254.dp).fillMaxHeight().background(Bento.card)) {
        Row(Modifier.height(68.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(30.dp); Spacer(Modifier.width(9.dp))
            Text("Persora", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Bento.fg, letterSpacing = (-1).sp); Text(".", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Bento.primary)
        }
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth().height(44.dp)
                .clip(RoundedCornerShape(12.dp)).background(Accents.brand.tile)
                .border(1.dp, Color.White.copy(alpha = if (Bento.isDark) 0.24f else 0.62f), RoundedCornerShape(12.dp))
                .clickable { EditorDrawer.openAddDocument() }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.NoteAdd, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Add document", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text("Choose a space and type", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.78f))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 13.dp)) {
            GroupLabel("Overview")
            SidebarLink(Icons.Outlined.Home, "Home", route == Routes.HOME) { onNavigate(Routes.HOME) }
            Sections.navGroups.forEach { (label, ids) ->
                GroupLabel(label)
                ids.forEach { id -> val s = Sections[id]; SidebarLink(s.icon, s.label, route == Routes.SECTION && sectionArg == id, items.count { it.section == id }) { onNavigate(Routes.section(id)) } }
            }
            GroupLabel("More")
            Routes.secondary.forEach { SidebarLink(it.icon, it.label, route == it.route) { onNavigate(it.route) } }
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Bento.bg).border(1.dp, Bento.border, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Bento.muted), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.VerifiedUser, null, tint = Bento.primary, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) { Text("Private & encrypted", style = MaterialTheme.typography.labelMedium, color = Bento.fg); Text("Only you can open these files", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg) }
                Icon(Icons.Outlined.CheckCircle, null, tint = Bento.primary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onNavigate(Routes.SETTINGS) }.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user, 35.dp); Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(user.fullName, style = MaterialTheme.typography.labelLarge, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (user.emailVerified) VerifiedBadge(activePlan = user.uploadsEnabled, size = 14.dp)
                    }
                    Text("ID ${user.userId}", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) { Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp), color = Bento.subtleFg, modifier = Modifier.padding(start = 10.dp, top = 16.dp, bottom = 6.dp)) }

@Composable
private fun SidebarLink(icon: ImageVector, label: String, active: Boolean, count: Int? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 39.dp).clip(RoundedCornerShape(10.dp)).background(if (active) Bento.primarySoft else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (active) Bento.primary else Bento.mutedFg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium), color = if (active) Bento.primary else Bento.mutedFg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null && count > 0) Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (active) Bento.primarySoft else Bento.muted).padding(horizontal = 6.dp, vertical = 1.dp)) { Text("$count", style = MaterialTheme.typography.labelMedium, color = if (active) Bento.primary else Bento.mutedFg) }
    }
}
