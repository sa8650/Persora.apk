package app.persora.android.ui.contacts

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.Dates
import app.persora.android.core.util.Files
import app.persora.android.core.util.PickedFile
import app.persora.android.core.util.Qr
import app.persora.android.data.model.CONTACT_CATEGORIES
import app.persora.android.data.model.ContactPhone
import app.persora.android.data.model.PersoraContact
import app.persora.android.ui.components.*
import app.persora.android.ui.calls.rememberCaller
import app.persora.android.ui.navigation.Details
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.navigation.Routes
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tones
import app.persora.android.ui.vault.ShareDialog
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val PHONE_LABELS = listOf("Mobile", "Home", "Work", "WhatsApp", "Other")

@Composable
fun ContactPhoto(contact: PersoraContact, size: androidx.compose.ui.unit.Dp) {
    val api = LocalContext.current.appContainer.api
    if (contact.photoKey != null) AsyncImage(model = api.contactPhotoUrl(contact.id) + "&v=${contact.updatedAt.hashCode()}", contentDescription = contact.name, modifier = Modifier.size(size).clip(CircleShape).background(Bento.muted), contentScale = ContentScale.Crop)
    else Avatar(contact.initials, size)
}

/* ---------------- List ---------------- */

@Composable
fun ContactsScreen() {
    val vault = LocalContext.current.appContainer.vault
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val contacts by vault.contacts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var mergeGroup by remember { mutableStateOf<List<PersoraContact>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var importOpen by rememberSaveable { mutableStateOf(false) }

    val duplicates = remember(contacts) { app.persora.android.core.util.ContactImport.duplicateGroups(contacts) }
    val visible = remember(contacts, query, category, favoritesOnly) {
        contacts.filter { c -> (category == "All" || c.category == category) && (!favoritesOnly || c.favorite) && (query.isBlank() || listOf(c.name, c.email, c.company, c.jobTitle).any { it.contains(query, true) } || c.phoneNumbers.any { it.number.contains(query) }) }
            .sortedWith(compareByDescending<PersoraContact> { it.favorite }.thenBy { it.name.lowercase() })
    }
    val grouped = remember(visible) { visible.groupBy { if (it.favorite) "★ Favorites" else it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#" } }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, floatingActionButton = { ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.contactEditor()) }, containerColor = Bento.primary, contentColor = Bento.primaryFg, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchField(query, { query = it }, "Search people, companies, numbers…", modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Bento.card).border(1.dp, Bento.border, RoundedCornerShape(12.dp))) { Icon(Icons.Outlined.MoreVert, "More", tint = Bento.fg) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Bento.card) {
                            DropdownMenuItem(text = { Text("Recent calls") }, leadingIcon = { Icon(Icons.Outlined.History, null) }, onClick = { menuOpen = false; nav.navigate(Routes.calls()) })
                            DropdownMenuItem(text = { Text("Import contacts") }, leadingIcon = { Icon(Icons.Outlined.PersonAddAlt, null) }, onClick = { menuOpen = false; importOpen = true })
                            if (duplicates.isNotEmpty()) DropdownMenuItem(text = { Text("Merge duplicates (${duplicates.size})") }, leadingIcon = { Icon(Icons.Outlined.CallMerge, null) }, onClick = { menuOpen = false; mergeGroup = duplicates.first() })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, { Text("Favorites") }, shape = CircleShape, leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(15.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bento.muted, selectedLabelColor = Bento.primary, selectedLeadingIconColor = Bento.primary))
                    (listOf("All") + CONTACT_CATEGORIES).forEach { c -> FilterChip(category == c, { category = c }, { Text(c) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bento.muted, selectedLabelColor = Bento.primary)) }
                }
            }
            if (duplicates.isNotEmpty()) item {
                BentoCard(padding = 12.dp, onClick = { mergeGroup = duplicates.first() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIconBox(Icons.Outlined.CallMerge, Tones.Amber, size = 34.dp, radius = 10.dp); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("${duplicates.size} possible duplicate${if (duplicates.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall, color = Bento.fg); Text("Same phone or email · tap to review and merge", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }; Icon(Icons.Outlined.ChevronRight, null, tint = Bento.subtleFg) }
                }
            }
            if (visible.isEmpty()) item { EmptyState(if (contacts.isEmpty()) "No contacts yet." else "No one matches.", if (contacts.isEmpty()) "Add the people who matter—with photos, numbers and birthdays—synced with the website." else "Try another name, number or category.", Icons.Outlined.ContactPage) }
            grouped.forEach { (letter, list) ->
                item { Text(letter, style = MaterialTheme.typography.labelLarge, color = Bento.subtleFg, modifier = Modifier.padding(top = 6.dp, start = 4.dp)) }
                items(list, key = { it.id }) { c ->
                    BentoCard(onClick = { Details.openContact(c.id) }, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ContactPhoto(c, 42.dp); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(c.phoneNumbers.firstOrNull()?.number, c.jobTitle.takeIf { it.isNotBlank() }, c.company.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ").ifBlank { c.email.ifBlank { c.category } }, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Pill(c.category, Tones.byName(categoryTone(c.category)))
                        }
                    }
                }
            }
        }
    }

    if (importOpen) ContactImportSheet(onDismiss = { importOpen = false })

    mergeGroup?.let { group ->
        var primary by remember(group) { mutableStateOf(group.first().id) }
        AlertDialog(onDismissRequest = { mergeGroup = null }, shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), containerColor = Bento.card, title = { Text("Merge duplicates") },
            text = { Column { Text("Choose the contact to keep. Details from the others are merged into it.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg); Spacer(Modifier.height(8.dp)); group.forEach { c -> Row(Modifier.fillMaxWidth().clickable { primary = c.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(primary == c.id, { primary = c.id }, colors = RadioButtonDefaults.colors(selectedColor = Bento.primary)); Column { Text(c.name, style = MaterialTheme.typography.titleSmall); Text("${c.phoneNumbers.size} number(s) · ${c.email.ifBlank { "no email" }} · updated ${Dates.formatRelative(c.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) } } } } },
            confirmButton = { TextButton(onClick = { scope.launch { runCatchingSafe { vault.mergeContacts(primary, group.map { it.id } - primary) }.onSuccess { notify("Contacts merged.", false) }.onFailure { notify(it.message ?: "Merge failed.", true) }; mergeGroup = null } }) { Text("Merge", fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { mergeGroup = null }) { Text("Not now", color = Bento.mutedFg) } })
    }

}

private fun categoryTone(c: String) = when (c) { "Family" -> "rose"; "Friend" -> "violet"; "Work" -> "blue"; "Service" -> "teal"; "Emergency" -> "red"; else -> "slate" }

/* ---------------- Detail ---------------- */

@Composable
fun ContactDetailScreen(id: String, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val nav = LocalNav.current
    val close: () -> Unit = onClose ?: { nav.popBackStack() }
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val contacts by vault.contacts.collectAsStateWithLifecycle()
    val incomingRecords by vault.incomingRecordShares.collectAsStateWithLifecycle()
    val sharedEntry = incomingRecords.firstOrNull { it.resourceType == "contact" && it.resourceId == id }
    val contact = contacts.firstOrNull { it.id == id } ?: sharedEntry?.contact
    var confirmDelete by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    val call = rememberCaller()
    if (contact == null) { EmptyState("Contact not found", "It may have been removed on another device.", Icons.Outlined.PersonOff) { QuietButton("Go back", onClick = { close() }) }; return }
    val readOnly = sharedEntry != null

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentWindowInsets = if (onClose != null) WindowInsets(0.dp) else ScaffoldDefaults.contentWindowInsets, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(if (onClose != null) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Bento.mutedFg) }
            Spacer(Modifier.weight(1f))
            if (!readOnly) IconButton(onClick = { scope.launch { runCatchingSafe { vault.saveContact(contact.copy(favorite = !contact.favorite), false) }.onFailure { notify(it.message ?: "Could not update.", true) } } }) { Icon(if (contact.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favorite", tint = if (contact.favorite) Accents.amber.c500 else Bento.mutedFg) }
            IconButton(onClick = { showQr = true }) { Icon(Icons.Outlined.QrCode2, "QR", tint = Bento.mutedFg) }
            if (!readOnly) IconButton(onClick = { shareOpen = true }) { Icon(Icons.Outlined.Share, "Share", tint = Bento.mutedFg) }
            if (!readOnly) IconButton(onClick = { val target = Routes.contactEditor(contact.id); if (onClose != null) onClose(); nav.navigate(target) }) { Icon(Icons.Outlined.Edit, "Edit", tint = Bento.mutedFg) }
            if (!readOnly) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete", tint = Bento.danger) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ContactPhoto(contact, 96.dp)
            Spacer(Modifier.height(12.dp))
            Text(contact.name, style = MaterialTheme.typography.headlineSmall, color = Bento.fg)
            Text(listOf(contact.jobTitle, contact.company).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill(contact.category, Tones.byName(categoryTone(contact.category))); if (sharedEntry != null) Pill("Shared by ${sharedEntry.owner.fullName}", Tones.Amber, Icons.Outlined.Share) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                contact.phoneNumbers.firstOrNull()?.let { p ->
                    ActionCircle(Icons.Outlined.Call, "Call") { call(p.number, contact.name) }
                    ActionCircle(Icons.Outlined.Sms, "Message") { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${p.number}"))) }
                }
                if (contact.email.isNotBlank()) ActionCircle(Icons.Outlined.Email, "Email") { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${contact.email}"))) }
                ActionCircle(Icons.Outlined.PersonAddAlt, "Save to phone") {
                    context.startActivity(Intent(Intent.ACTION_INSERT).apply { type = android.provider.ContactsContract.Contacts.CONTENT_TYPE; putExtra(android.provider.ContactsContract.Intents.Insert.NAME, contact.name); contact.phoneNumbers.firstOrNull()?.let { putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, it.number) }; if (contact.email.isNotBlank()) putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, contact.email); if (contact.company.isNotBlank()) putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, contact.company) })
                }
            }
            Spacer(Modifier.height(18.dp))
            BentoCard(Modifier.fillMaxWidth()) {
                contact.phoneNumbers.forEach { p -> Row(Modifier.fillMaxWidth().clickable { call(p.number, contact.name) }.padding(vertical = 6.dp)) { Column { Text(p.label, style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg); Text(p.number, style = MaterialTheme.typography.bodyMedium, color = Bento.primary, fontWeight = FontWeight.Medium) } } }
                if (contact.email.isNotBlank()) DetailRow("Email", contact.email)
                if (contact.address.isNotBlank()) DetailRow("Address", contact.address)
                if (contact.birthday.isNotBlank()) DetailRow("Birthday", Dates.formatDate(contact.birthday) + (Dates.parseLocalDate(contact.birthday)?.let { b -> val next = b.withYear(java.time.LocalDate.now().year).let { if (it.isBefore(java.time.LocalDate.now())) it.plusYears(1) else it }; " · in ${java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), next)} days" } ?: ""))
                if (contact.notes.isNotBlank()) DetailRow("Notes", contact.notes)
                DetailRow("Updated", Dates.formatRelative(contact.updatedAt))
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${contact.name}?", "This removes the contact from your Persora vault on every device.", onConfirm = { confirmDelete = false; scope.launch { runCatchingSafe { vault.deleteContact(contact.id) }.onSuccess { notify("Contact deleted.", false); close() }.onFailure { notify(it.message ?: "Could not delete.", true) } } }, onDismiss = { confirmDelete = false })
    if (showQr) QrDialog("Scan to save", Qr.vCard(contact.name, contact.phoneNumbers.map { it.number }, contact.email, contact.company, contact.jobTitle, contact.address), "vCard · opens in any phone's camera", onDismiss = { showQr = false })
    if (shareOpen) ShareDialog(allowPermission = false, onDismiss = { shareOpen = false }) { recipient, _ -> scope.launch { runCatchingSafe { container.api.createRecordShare("contact", contact.id, recipient); vault.refreshShares() }.onSuccess { notify("Contact shared with $recipient.", false) }.onFailure { notify(it.message ?: "Could not share.", true) }; shareOpen = false } }
}

@Composable
private fun ActionCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(6.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Bento.muted), contentAlignment = Alignment.Center) { Icon(icon, label, tint = Bento.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(4.dp)); Text(label, style = MaterialTheme.typography.labelMedium, color = Bento.mutedFg)
    }
}

/* ---------------- Editor ---------------- */

@Composable
fun ContactEditorScreen(id: String?) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val contacts by vault.contacts.collectAsStateWithLifecycle()
    val existing = contacts.firstOrNull { it.id == id }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var phones by remember { mutableStateOf(existing?.phoneNumbers?.ifEmpty { null } ?: listOf(ContactPhone("Mobile", ""))) }
    var email by rememberSaveable { mutableStateOf(existing?.email.orEmpty()) }
    var company by rememberSaveable { mutableStateOf(existing?.company.orEmpty()) }
    var jobTitle by rememberSaveable { mutableStateOf(existing?.jobTitle.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(existing?.address.orEmpty()) }
    var birthday by rememberSaveable { mutableStateOf(existing?.birthday.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "Other") }
    var photo by remember { mutableStateOf<PickedFile?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { photo = Files.describe(context, it); removePhoto = false } }

    fun save() {
        if (name.isBlank()) { notify("Give this contact a name.", true); return }
        saving = true
        scope.launch {
            try {
                val cleanPhones = phones.filter { it.number.isNotBlank() }
                // Same flow as App.tsx saveContact(): photo goes to R2 through /api/upload, then photoKey is saved with the contact.
                var photoKey = if (removePhoto) null else existing?.photoKey
                var uploadedKey: String? = null
                photo?.let { p -> if (p.size > 5L * 1024 * 1024) error("Photos must be 5 MB or smaller."); val up = withContext(Dispatchers.IO) { container.api.uploadVaultFile(p.name, p.mime, p.size, { p.open(context) }) }; photoKey = up.key; uploadedKey = up.key }
                val draft = (existing ?: PersoraContact(id = UUID.randomUUID().toString(), name = "")).copy(name = name.trim(), phoneNumbers = cleanPhones, email = email.trim(), company = company.trim(), jobTitle = jobTitle.trim(), address = address.trim(), birthday = birthday, notes = notes.trim(), category = category, photoKey = photoKey)
                try { vault.saveContact(draft, existing == null) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { uploadedKey?.let { k -> runCatchingSafe { container.api.deleteVaultFile(k) } }; throw e }
                notify(if (existing == null) "Contact added." else "Contact updated.", false); nav.popBackStack()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Could not save contact.", true) } finally { saving = false }
        }
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.Close, "Cancel", tint = Bento.mutedFg) }
            Column(Modifier.weight(1f)) { Eyebrow("People"); Text(if (existing == null) "New contact" else "Edit contact", style = MaterialTheme.typography.titleMedium, color = Bento.fg) }
            PrimaryButton("Save", ::save, enabled = !saving, loading = saving)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    when {
                        photo != null -> AsyncImage(model = photo!!.uri, contentDescription = null, modifier = Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        existing?.photoKey != null && !removePhoto -> ContactPhoto(existing, 96.dp)
                        else -> Avatar(name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "+" }, 96.dp)
                    }
                    Row {
                        if (photo != null || (existing?.photoKey != null && !removePhoto)) SmallFloatingActionButton(onClick = { photo = null; removePhoto = true }, containerColor = Bento.card, contentColor = Bento.danger, shape = CircleShape) { Icon(Icons.Outlined.Delete, "Remove photo", Modifier.size(16.dp)) }
                        SmallFloatingActionButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, containerColor = Bento.primary, contentColor = Bento.primaryFg, shape = CircleShape) { Icon(Icons.Outlined.PhotoCamera, "Photo", Modifier.size(16.dp)) }
                    }
                }
            }
            BentoCard {
                TextInput(name, { name = it }, "Full name", required = true)
                Spacer(Modifier.height(10.dp)); SelectInput(category, CONTACT_CATEGORIES, { category = it }, "Category", allowEmpty = false)
                Spacer(Modifier.height(10.dp)); TextInput(email, { email = it }, "Email", keyboard = KeyboardType.Email)
            }
            BentoCard {
                SectionHeading("Phone numbers", "${phones.count { it.number.isNotBlank() }} saved") { TextButton(onClick = { phones = phones + ContactPhone("Mobile", "") }) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text("Add") } }
                phones.forEachIndexed { i, p ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectInput(p.label, PHONE_LABELS, { l -> phones = phones.toMutableList().also { it[i] = p.copy(label = l) } }, "Label", modifier = Modifier.width(120.dp), allowEmpty = false)
                        TextInput(p.number, { n -> phones = phones.toMutableList().also { it[i] = p.copy(number = n) } }, "Number", modifier = Modifier.weight(1f), keyboard = KeyboardType.Phone)
                        if (phones.size > 1) IconButton(onClick = { phones = phones.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Outlined.RemoveCircleOutline, "Remove", tint = Bento.danger) }
                    }
                }
            }
            BentoCard {
                TextInput(company, { company = it }, "Company")
                Spacer(Modifier.height(10.dp)); TextInput(jobTitle, { jobTitle = it }, "Job title")
                Spacer(Modifier.height(10.dp)); TextInput(address, { address = it }, "Address", minLines = 2)
                Spacer(Modifier.height(10.dp)); DateInput(birthday, { birthday = it }, "Birthday")
                Spacer(Modifier.height(10.dp)); TextInput(notes, { notes = it }, "Notes", minLines = 3)
            }
        }
    }
}
