package app.persora.android.ui.businesscards

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.core.util.Files
import app.persora.android.core.util.PickedFile
import app.persora.android.core.util.Qr
import app.persora.android.data.model.*
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.EditorDrawer
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

private data class CardStyle(val id: String, val name: String, val note: String, val cover: List<Color>, val onCover: Color)
private val STYLES = listOf(
    CardStyle("garden", "Signature", "Sky", listOf(Accents.sky.c400, Accents.sky.c600), Color.White),
    CardStyle("minimal", "Minimal", "Soft neutral", listOf(Color(0xFFF5F5F5), Color(0xFFE5E5E5)), Color(0xFF0A0A0A)),
    CardStyle("midnight", "Midnight", "Graphite", listOf(Color(0xFF262626), Color(0xFF0A0A0A)), Color.White),
    CardStyle("terracotta", "Terracotta", "Warm amber", listOf(Accents.amber.c400, Accents.orange.c600), Color.White),
)
private fun styleOf(id: String) = STYLES.firstOrNull { it.id == id } ?: STYLES.first()

/** .business-card-mini-preview / .public-card-surface — the card itself. `photoUrl`/`logoUrl` already resolved by the caller. */
@Composable
fun BusinessCardVisual(card: DigitalBusinessCard, photoUrl: String?, logoUrl: String?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val style = styleOf(card.style)
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Bento.card).border(1.dp, Bento.border, RoundedCornerShape(20.dp))) {
        Box(Modifier.fillMaxWidth().height(if (compact) 64.dp else 96.dp).background(Brush.linearGradient(style.cover))) {
            Box(Modifier.align(Alignment.TopStart).padding(12.dp).size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.BusinessCenter, null, tint = style.onCover, modifier = Modifier.size(15.dp)) }
            if (logoUrl != null) AsyncImage(model = logoUrl, contentDescription = card.company, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentScale = ContentScale.Fit)
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.offset(y = (-36).dp).size(64.dp).clip(CircleShape).background(Bento.card).padding(3.dp)) {
                    if (photoUrl != null) AsyncImage(model = photoUrl, contentDescription = card.fullName, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    else Avatar(card.fullName.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "P" }, 58.dp)
                }
                Spacer(Modifier.weight(1f))
                Pill(if (card.isPublic) "Public" else "Private", if (card.isPublic) Tones.Green else Tones.Neutral, if (card.isPublic) Icons.Outlined.Public else Icons.Outlined.Lock)
            }
            Column(Modifier.offset(y = (-24).dp)) {
                Text(card.fullName.ifBlank { "Your name" }, style = MaterialTheme.typography.titleLarge, color = Bento.fg)
                Text(listOf(card.jobTitle, card.company).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Title · Company" }, style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg)
                if (!compact) {
                    if (card.bio.isNotBlank()) Text(card.bio, style = MaterialTheme.typography.bodyMedium, color = Bento.fg, modifier = Modifier.padding(top = 8.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    card.phoneNumbers.firstOrNull { it.number.isNotBlank() }?.let { InfoLine(Icons.Outlined.Call, it.number) }
                    if (card.email.isNotBlank()) InfoLine(Icons.Outlined.Email, card.email)
                    card.websites.filter { it.isNotBlank() }.forEach { InfoLine(Icons.Outlined.Language, it.removePrefix("https://").removePrefix("http://")) }
                    if (card.address.isNotBlank()) InfoLine(Icons.Outlined.Place, card.address)
                    if (card.socialLinks.any { it.url.isNotBlank() }) Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { card.socialLinks.filter { it.url.isNotBlank() }.forEach { Pill(it.platform, Tones.Blue) } }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Bento.subtleFg, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(6.dp)); Text(text, style = MaterialTheme.typography.bodySmall, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/* ---------------- Manager ---------------- */

@Composable
fun BusinessCardsScreen() {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val api = container.api
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val cards by vault.businessCards.collectAsStateWithLifecycle()
    var qr by remember { mutableStateOf<DigitalBusinessCard?>(null) }
    var share by remember { mutableStateOf<DigitalBusinessCard?>(null) }
    var confirmDelete by remember { mutableStateOf<DigitalBusinessCard?>(null) }
    LaunchedEffect(Unit) { vault.refreshCards() }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, floatingActionButton = { ExtendedFloatingActionButton(onClick = { EditorDrawer.openBusinessCard() }, containerColor = Bento.primary, contentColor = Bento.primaryFg, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (cards.isEmpty()) item { EmptyState("No business cards yet.", "Create your first card in under a minute. You choose what's public.", Icons.Outlined.CreditCard) }
            items(cards, key = { it.id }) { card ->
                Column {
                    BusinessCardVisual(card, card.profilePhotoKey?.let { api.vaultFileUrl(it) }, card.businessLogoKey?.let { api.vaultFileUrl(it) }, Modifier.fillMaxWidth().clickable { EditorDrawer.openBusinessCard(card.id) })
                    Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SoftButton("Edit", onClick = { EditorDrawer.openBusinessCard(card.id) }, icon = Icons.Outlined.Edit)
                        if (card.isPublic && card.cardId != null) {
                            QuietButton("QR", onClick = { qr = card }, icon = Icons.Outlined.QrCode2)
                            QuietButton("Copy link", onClick = { val url = api.publicCardUrl(card.cardId); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }, "Share card link")) }, icon = Icons.Outlined.Link)
                            QuietButton("Preview", onClick = { nav.navigate(Routes.publicCard(card.cardId)) }, icon = Icons.Outlined.OpenInNew)
                        }
                        QuietButton("Share", onClick = { share = card }, icon = Icons.Outlined.PersonAddAlt)
                        TextButton(onClick = { confirmDelete = card }) { Text("Delete", color = Bento.danger) }
                    }
                    if (!card.isPublic) Text("Private — switch on “Public link” in the editor to get a shareable URL and QR code.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    qr?.let { c -> QrDialog("Scan to open ${c.fullName.ifBlank { "this card" }}", api.publicCardUrl(c.cardId!!), api.publicCardUrl(c.cardId), onDismiss = { qr = null }) }
    share?.let { c -> ShareDialog(allowPermission = false, onDismiss = { share = null }) { recipient, _ -> scope.launch { runCatchingSafe { api.createRecordShare("business_card", c.id, recipient); vault.refreshShares() }.onSuccess { notify("Card shared with $recipient.", false) }.onFailure { notify(it.message ?: "Could not share.", true) }; share = null } } }
    confirmDelete?.let { c -> ConfirmDialog("Delete this card?", "The public link stops working immediately.", onConfirm = { confirmDelete = null; scope.launch { runCatchingSafe { vault.deleteBusinessCard(c.id) }.onSuccess { notify("Card deleted.", false) }.onFailure { notify(it.message ?: "Could not delete.", true) } } }, onDismiss = { confirmDelete = null }) }
}

/* ---------------- Editor ---------------- */

@Composable
fun BusinessCardEditorScreen(id: String?, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val api = container.api
    val nav = LocalNav.current
    val closeEditor: () -> Unit = onClose ?: { nav.popBackStack() }
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val cards by vault.businessCards.collectAsStateWithLifecycle()
    val user = observeCurrentUser()
    val canUpload = user?.uploadsEnabled == true
    val existing = cards.firstOrNull { it.id == id }
    var form by remember(existing) { mutableStateOf(existing ?: DigitalBusinessCard(id = UUID.randomUUID().toString(), fullName = user?.fullName.orEmpty(), email = user?.email.orEmpty(), phoneNumbers = listOf(ContactPhone("Mobile", "")), websites = listOf(""))) }
    var photo by remember { mutableStateOf<PickedFile?>(null) }
    var logo by remember { mutableStateOf<PickedFile?>(null) }
    var clearPhoto by remember { mutableStateOf(false) }
    var clearLogo by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && canUpload) { photo = Files.describe(context, uri); clearPhoto = false }
        else if (uri != null) notify("New business-card image uploads require an active paid plan. Existing images remain accessible.", true)
    }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && canUpload) { logo = Files.describe(context, uri); clearLogo = false }
        else if (uri != null) notify("New business-card image uploads require an active paid plan. Existing images remain accessible.", true)
    }

    fun save() {
        if ((photo != null || logo != null) && !canUpload) { notify("New business-card image uploads require an active paid plan. Existing images remain accessible.", true); return }
        if (form.fullName.isBlank()) { notify("Add your name.", true); return }
        saving = true
        scope.launch {
            try {
                var profileKey = if (clearPhoto) null else existing?.profilePhotoKey
                var logoKey = if (clearLogo) null else existing?.businessLogoKey
                photo?.let { p -> if (p.size > 5L * 1024 * 1024) error("Photos must be 5 MB or smaller."); profileKey = withContext(Dispatchers.IO) { api.uploadVaultFile(p.name, p.mime, p.size, { p.open(context) }) }.key }
                logo?.let { p -> if (p.size > 5L * 1024 * 1024) error("Logos must be 5 MB or smaller."); logoKey = withContext(Dispatchers.IO) { api.uploadVaultFile(p.name, p.mime, p.size, { p.open(context) }) }.key }
                val clean = form.copy(fullName = form.fullName.trim(), phoneNumbers = form.phoneNumbers.filter { it.number.isNotBlank() }, websites = form.websites.map { it.trim() }.filter { it.isNotBlank() }, socialLinks = form.socialLinks.filter { it.url.isNotBlank() }, customLinks = form.customLinks.filter { it.url.isNotBlank() && it.label.isNotBlank() }, profilePhotoKey = profileKey, businessLogoKey = logoKey)
                vault.saveBusinessCard(clean, existing == null)
                notify(if (existing == null) "Card created." else "Card updated.", false); closeEditor()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { notify(e.message ?: "Could not save card.", true) } finally { saving = false }
        }
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = closeEditor) { Icon(Icons.Outlined.Close, "Cancel", tint = Bento.mutedFg) }
            Column(Modifier.weight(1f)) { Eyebrow("Your digital identity"); Text(if (existing == null) "New business card" else "Edit card", style = MaterialTheme.typography.titleMedium, color = Bento.fg) }
            PrimaryButton("Save", ::save, enabled = !saving, loading = saving)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BusinessCardVisual(form, photo?.uri?.toString() ?: existing?.profilePhotoKey?.takeIf { !clearPhoto }?.let { api.vaultFileUrl(it) }, logo?.uri?.toString() ?: existing?.businessLogoKey?.takeIf { !clearLogo }?.let { api.vaultFileUrl(it) }, Modifier.fillMaxWidth(), compact = true)
            BentoCard {
                SectionHeading("Style", "Pick a look")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STYLES.forEach { s -> Column(Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).border(if (form.style == s.id) 2.dp else 1.dp, if (form.style == s.id) Bento.primary else Bento.border, RoundedCornerShape(12.dp)).clickable { form = form.copy(style = s.id) }.padding(6.dp)) { Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(s.cover))); Text(s.name, style = MaterialTheme.typography.labelLarge, color = Bento.fg, modifier = Modifier.padding(top = 4.dp)); Text(s.note, style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg) } }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(form.isPublic, { form = form.copy(isPublic = it) }, colors = SwitchDefaults.colors(checkedTrackColor = Bento.primary)); Spacer(Modifier.width(8.dp)); Column { Text("Public link", style = MaterialTheme.typography.titleSmall); Text("Anyone with the link or QR can view this card. Persora generates the link.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) } }
            }
            BentoCard {
                SectionHeading("Images", "Photo & logo")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SoftButton(if (photo != null) "Change photo" else "Profile photo", onClick = { if (canUpload) photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Icons.Outlined.AddAPhoto, enabled = canUpload); if (photo != null || (existing?.profilePhotoKey != null && !clearPhoto)) TextButton(onClick = { photo = null; clearPhoto = true }) { Text("Remove", color = Bento.danger) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SoftButton(if (logo != null) "Change logo" else "Business logo", onClick = { if (canUpload) logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Icons.Outlined.Image, enabled = canUpload); if (logo != null || (existing?.businessLogoKey != null && !clearLogo)) TextButton(onClick = { logo = null; clearLogo = true }) { Text("Remove", color = Bento.danger) } }
                Text(if (canUpload) "JPG, PNG, WEBP or GIF · max 5 MB" else "New photo and logo uploads require an active paid plan. Existing images remain accessible.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
            }
            BentoCard {
                TextInput(form.fullName, { form = form.copy(fullName = it) }, "Full name", required = true)
                Spacer(Modifier.height(10.dp)); TextInput(form.jobTitle, { form = form.copy(jobTitle = it) }, "Job title")
                Spacer(Modifier.height(10.dp)); TextInput(form.company, { form = form.copy(company = it) }, "Company")
                Spacer(Modifier.height(10.dp)); TextInput(form.bio, { form = form.copy(bio = it.take(280)) }, "Short bio", minLines = 2, supporting = "${form.bio.length}/280")
                Spacer(Modifier.height(10.dp)); TextInput(form.email, { form = form.copy(email = it) }, "Email", keyboard = KeyboardType.Email)
                Spacer(Modifier.height(10.dp)); TextInput(form.address, { form = form.copy(address = it) }, "Address")
            }
            BentoCard {
                SectionHeading("Phone numbers", "") { TextButton(onClick = { form = form.copy(phoneNumbers = form.phoneNumbers + ContactPhone("Work", "")) }) { Text("Add") } }
                form.phoneNumbers.forEachIndexed { i, p -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { SelectInput(p.label, listOf("Mobile", "Work", "Home", "WhatsApp", "Other"), { l -> form = form.copy(phoneNumbers = form.phoneNumbers.toMutableList().also { it[i] = p.copy(label = l) }) }, "Label", Modifier.width(120.dp), allowEmpty = false); TextInput(p.number, { n -> form = form.copy(phoneNumbers = form.phoneNumbers.toMutableList().also { it[i] = p.copy(number = n) }) }, "Number", Modifier.weight(1f), keyboard = KeyboardType.Phone); IconButton(onClick = { form = form.copy(phoneNumbers = form.phoneNumbers.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.RemoveCircleOutline, null, tint = Bento.danger) } } }
                Spacer(Modifier.height(10.dp))
                SectionHeading("Websites", "") { TextButton(onClick = { form = form.copy(websites = form.websites + "") }) { Text("Add") } }
                form.websites.forEachIndexed { i, w -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) { TextInput(w, { v -> form = form.copy(websites = form.websites.toMutableList().also { it[i] = v }) }, "https://", Modifier.weight(1f), keyboard = KeyboardType.Uri); IconButton(onClick = { form = form.copy(websites = form.websites.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.RemoveCircleOutline, null, tint = Bento.danger) } } }
            }
            BentoCard {
                SectionHeading("Social links", "") { TextButton(onClick = { form = form.copy(socialLinks = form.socialLinks + BusinessSocialLink(BUSINESS_SOCIAL_PLATFORMS.first(), "")) }) { Text("Add") } }
                form.socialLinks.forEachIndexed { i, s -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { SelectInput(s.platform, BUSINESS_SOCIAL_PLATFORMS, { p -> form = form.copy(socialLinks = form.socialLinks.toMutableList().also { it[i] = s.copy(platform = p) }) }, "Platform", Modifier.width(130.dp), allowEmpty = false); TextInput(s.url, { u -> form = form.copy(socialLinks = form.socialLinks.toMutableList().also { it[i] = s.copy(url = u) }) }, "Profile URL", Modifier.weight(1f), keyboard = KeyboardType.Uri); IconButton(onClick = { form = form.copy(socialLinks = form.socialLinks.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.RemoveCircleOutline, null, tint = Bento.danger) } } }
                Spacer(Modifier.height(10.dp))
                SectionHeading("Custom links", "") { TextButton(onClick = { form = form.copy(customLinks = form.customLinks + BusinessCustomLink("", "")) }) { Text("Add") } }
                form.customLinks.forEachIndexed { i, l -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextInput(l.label, { v -> form = form.copy(customLinks = form.customLinks.toMutableList().also { it[i] = l.copy(label = v) }) }, "Label", Modifier.width(120.dp)); TextInput(l.url, { v -> form = form.copy(customLinks = form.customLinks.toMutableList().also { it[i] = l.copy(url = v) }) }, "URL", Modifier.weight(1f), keyboard = KeyboardType.Uri); IconButton(onClick = { form = form.copy(customLinks = form.customLinks.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.RemoveCircleOutline, null, tint = Bento.danger) } } }
            }
        }
    }
}

/* ---------------- Public card (persora.pages.dev/card/{id}) ---------------- */

@Composable
fun PublicCardScreen(cardId: String) {
    val context = LocalContext.current
    val api = context.appContainer.api
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val call = app.persora.android.ui.calls.rememberCaller()
    var card by remember { mutableStateOf<DigitalBusinessCard?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    LaunchedEffect(cardId) { runCatchingSafe { api.fetchPublicBusinessCard(cardId) }.onSuccess { card = it }.onFailure { error = humanizeError(it.message ?: "This card isn't available.", "error").first } }
    val url = api.publicCardUrl(cardId)

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Bento.mutedFg) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { qr = true }) { Icon(Icons.Outlined.QrCode2, "QR", tint = Bento.mutedFg) }
            IconButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }, "Share card")) }) { Icon(Icons.Outlined.IosShare, "Share", tint = Bento.mutedFg) }
            IconButton(onClick = { report = true }) { Icon(Icons.Outlined.Flag, "Report", tint = Bento.mutedFg) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 60.dp)) {
            when {
                error != null -> EmptyState("Card unavailable", error!!, Icons.Outlined.CreditCardOff)
                card == null -> LoadingBlock(Modifier.fillMaxWidth().height(240.dp))
                else -> {
                    val c = card!!
                    BusinessCardVisual(c, c.profilePhotoKey?.let { api.publicCardPhotoUrl(cardId, "profile") }, c.businessLogoKey?.let { api.publicCardPhotoUrl(cardId, "logo") }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        c.phoneNumbers.firstOrNull()?.let { p -> SoftButton("Call", onClick = { call(p.number, c.fullName) }, icon = Icons.Outlined.Call) }
                        if (c.email.isNotBlank()) SoftButton("Email", onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${c.email}"))) }, icon = Icons.Outlined.Email)
                        QuietButton("Save contact", onClick = { context.startActivity(Intent(Intent.ACTION_INSERT).apply { type = android.provider.ContactsContract.Contacts.CONTENT_TYPE; putExtra(android.provider.ContactsContract.Intents.Insert.NAME, c.fullName); c.phoneNumbers.firstOrNull()?.let { putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, it.number) }; if (c.email.isNotBlank()) putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, c.email); if (c.company.isNotBlank()) putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, c.company); if (c.jobTitle.isNotBlank()) putExtra(android.provider.ContactsContract.Intents.Insert.JOB_TITLE, c.jobTitle) }) }, icon = Icons.Outlined.PersonAddAlt)
                    }
                    if (c.socialLinks.isNotEmpty() || c.customLinks.isNotEmpty() || c.websites.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        BentoCard {
                            SectionHeading("Links", "Find ${c.fullName.substringBefore(' ')} online")
                            (c.websites.map { it to it } + c.socialLinks.map { it.platform to it.url } + c.customLinks.map { it.label to it.url }).forEach { (label, link) ->
                                Row(Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (link.startsWith("http")) link else "https://$link"))) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Link, null, tint = Bento.primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(10.dp)); Text(label, style = MaterialTheme.typography.bodyMedium, color = Bento.primary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Shared via Persora · $url", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
                }
            }
        }
    }
    if (qr) QrDialog("Scan to open this card", url, url, onDismiss = { qr = false })
    if (report) {
        var reason by remember { mutableStateOf("Spam or misleading") }
        var details by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { report = false }, shape = RoundedCornerShape(22.dp), containerColor = Bento.card, title = { Text("Report this card") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Choose a reason. Reports are private and help keep public cards safe.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg); SelectInput(reason, listOf("Spam or misleading", "Inappropriate content", "Impersonation", "Other"), { reason = it }, "Reason", allowEmpty = false); TextInput(details, { details = it.take(500) }, "Details (optional)", minLines = 3) } },
            confirmButton = { TextButton(onClick = { busy = true; scope.launch { runCatchingSafe { api.reportPublicBusinessCard(cardId, reason, details) }.onSuccess { notify("Thanks for letting us know. Your report was submitted.", false) }.onFailure { notify(it.message ?: "Could not send report.", true) }; busy = false; report = false } }, enabled = !busy) { Text(if (busy) "Sending…" else "Send report", fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { report = false }) { Text("Cancel", color = Bento.mutedFg) } })
    }
}
