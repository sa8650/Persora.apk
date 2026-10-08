package app.persora.android.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.data.model.Sections
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.Details
import app.persora.android.ui.navigation.EditorDrawer
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.Routes
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Tone
import app.persora.android.ui.theme.Tones

private data class Hit(val id: String, val title: String, val subtitle: String, val kind: String, val icon: ImageVector, val tone: Tone, val route: String, val score: Int)

/** SearchModal.tsx: one box across vault items, contacts, medical records, business cards and timeline. */
@Composable
fun SearchScreen() {
    val vault = LocalContext.current.appContainer.vault
    val nav = LocalNav.current
    val items by vault.items.collectAsStateWithLifecycle()
    val contacts by vault.contacts.collectAsStateWithLifecycle()
    val medical by vault.medicalRecords.collectAsStateWithLifecycle()
    val cards by vault.businessCards.collectAsStateWithLifecycle()
    val timeline by vault.timeline.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf("All") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val q = query.trim().lowercase()
    fun score(vararg fields: String?): Int { if (q.isBlank()) return 0; var s = 0; fields.forEachIndexed { i, f -> val v = f?.lowercase() ?: return@forEachIndexed; if (v == q) s += 100 - i; else if (v.startsWith(q)) s += 60 - i; else if (v.contains(q)) s += 30 - i }; return s }
    val hits = remember(q, scope, items, contacts, medical, cards, timeline) {
        if (q.length < 2) emptyList() else buildList {
            if (scope == "All" || scope == "Records") items.forEach { it -> val sec = Sections[it.section]; val s = score(it.title, it.subtitle, *it.metadata.values.toTypedArray()); if (s > 0) add(Hit(it.id, it.title, sec.label + (it.metadata[sec.previewKeys.firstOrNull() ?: ""]?.let { v -> " · $v" } ?: ""), "record", sec.icon, Tones.byName(sec.color), Routes.item(it.id), s)) }
            if (scope == "All" || scope == "People") contacts.forEach { c -> val s = score(c.name, c.email, c.company, c.jobTitle, *c.phoneNumbers.map { it.number }.toTypedArray()); if (s > 0) add(Hit(c.id, c.name, listOf(c.phoneNumbers.firstOrNull()?.number, c.company.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ").ifBlank { "Contact" }, "contact", Icons.Outlined.ContactPage, Tones.Blue, Routes.contact(c.id), s)) }
            if (scope == "All" || scope == "Medical") medical.forEach { m -> val s = score(m.title, m.diagnosis, m.provider, m.hospital, m.testName, m.recordType); if (s > 0) add(Hit(m.id, m.title, "${m.recordType} · ${m.provider.ifBlank { m.hospital }}", "medical", Icons.Outlined.MonitorHeart, Tones.Red, Routes.MEDICAL, s)) }
            if (scope == "All") cards.forEach { c -> val s = score(c.fullName, c.company, c.jobTitle, c.email); if (s > 0) add(Hit(c.id, c.fullName, "Business card · ${c.company}", "card", Icons.Outlined.CreditCard, Tones.Violet, Routes.cardEditor(c.id), s)) }
            if (scope == "All") timeline.forEach { e -> val s = score(e.title, e.description); if (s > 0) add(Hit(e.id, e.title, "Timeline · ${e.eventDate.take(10)}", "timeline", Icons.Outlined.EventNote, Tones.Indigo, Routes.TIMELINE, s)) }
        }.sortedByDescending { it.score }.take(60)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        SearchField(query, { query = it }, "Search everything in your vault…", Modifier.padding(top = 14.dp), focusRequester = focus)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Records", "People", "Medical").forEach { s -> FilterChip(scope == s, { scope = s }, { Text(s) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bento.muted, selectedLabelColor = Bento.primary)) } }
        Spacer(Modifier.height(8.dp))
        when {
            q.length < 2 -> {
                Text("QUICK JUMP", style = MaterialTheme.typography.labelSmall, color = Bento.subtleFg, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(Sections.all, key = { it.id }) { s -> BentoCard(padding = 10.dp, onClick = { nav.navigate(Routes.section(s.id)) }) { Row(verticalAlignment = Alignment.CenterVertically) { ToneIconBox(s.icon, Tones.byName(s.color), size = 32.dp, radius = 9.dp); Spacer(Modifier.width(10.dp)); Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Bento.fg); Text("${items.count { it.section == s.id }}", style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg) } } }
                }
            }
            hits.isEmpty() -> EmptyState("No matches for “$query”.", "Try a shorter word, a phone number or a company name.", Icons.Outlined.SearchOff)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { Text("${hits.size} RESULT${if (hits.size == 1) "" else "S"}", style = MaterialTheme.typography.labelSmall, color = Bento.subtleFg, modifier = Modifier.padding(vertical = 4.dp)) }
                items(hits, key = { it.kind + it.id }) { h ->
                    BentoCard(padding = 10.dp, onClick = { if (h.kind == "card") EditorDrawer.openBusinessCard(h.id) else if (!Details.openRoute(h.route)) nav.navigate(h.route) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ToneIconBox(h.icon, h.tone, size = 34.dp, radius = 10.dp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(h.title, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(h.subtitle, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Icon(Icons.Outlined.ArrowOutward, null, tint = Bento.subtleFg, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}
