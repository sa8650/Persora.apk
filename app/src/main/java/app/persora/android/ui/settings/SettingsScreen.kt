package app.persora.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import app.persora.android.core.util.Avatars
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.persora.android.BuildConfig
import app.persora.android.appContainer
import app.persora.android.core.util.Dates
import app.persora.android.core.util.Files
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.data.model.AppUser
import app.persora.android.data.model.SiteContent
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.LocalNav
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.navigation.Routes
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

private val TIMEZONES = listOf("Asia/Dhaka", "Asia/Kolkata", "Asia/Dubai", "Asia/Riyadh", "Asia/Singapore", "Asia/Kuala_Lumpur", "Asia/Tokyo", "Europe/London", "Europe/Berlin", "Europe/Paris", "America/New_York", "America/Chicago", "America/Los_Angeles", "America/Toronto", "Australia/Sydney", "UTC")

/** Preferences / profile on the web → Settings here, plus Android-only items (app lock, notifications, exact alarms). */
@Composable
fun SettingsScreen(initialUser: AppUser) {
    val user = observeCurrentUser() ?: initialUser
    val context = LocalContext.current
    val container = context.appContainer
    val api = container.api
    val session = container.session
    val vault = container.vault
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var fullName by remember(user.fullName) { mutableStateOf(user.fullName) }
    var timezone by remember(user.timezone) { mutableStateOf(user.timezone.ifBlank { ZoneId.systemDefault().id }) }
    var avatarUrl by remember(user.avatarUrl) { mutableStateOf(user.avatarUrl) }
    var avatarBusy by remember { mutableStateOf(false) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    val lastSyncedAt by vault.lastSyncedAt.collectAsStateWithLifecycle()
    var savingProfile by remember { mutableStateOf(false) }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var verificationCodeSent by rememberSaveable { mutableStateOf(false) }
    var sendingVerification by remember { mutableStateOf(false) }
    var verifyingEmail by remember { mutableStateOf(false) }
    val canUpload = user.uploadsEnabled
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        avatarBusy = true
        scope.launch {
            val picked = Files.describe(context, uri)
            runCatchingSafe { withContext(Dispatchers.IO) { Avatars.encodeProfilePhoto(context, uri, picked.size, picked.mime) } }
                .onSuccess { avatarUrl = it }.onFailure { notify(it.message ?: "Couldn't process that photo.", true) }
            avatarBusy = false
        }
    }
    val profileDirty = fullName != user.fullName || timezone != user.timezone || avatarUrl != user.avatarUrl
    fun sendVerificationCode() {
        if (sendingVerification) return
        sendingVerification = true
        scope.launch {
            runCatchingSafe {
                val alreadyVerified = api.sendEmailVerificationCode()
                if (alreadyVerified) api.me()?.let(session::updateUser)
                alreadyVerified
            }.onSuccess { alreadyVerified ->
                if (alreadyVerified) { verificationCodeSent = false; notify("This email address is already verified.", false) }
                else { verificationCodeSent = true; notify("A one-time code was sent to your registered email.", false) }
            }.onFailure { notify(it.message ?: "Couldn't send a verification code.", true) }
            sendingVerification = false
        }
    }
    fun verifyEmailCode() {
        if (!verificationCode.matches(Regex("\\d{6}"))) { notify("Enter the six-digit code from your email.", true); return }
        if (verifyingEmail) return
        verifyingEmail = true
        scope.launch {
            runCatchingSafe { api.verifyEmailVerificationCode(verificationCode) }
                .onSuccess { verified -> session.updateUser(verified); verificationCode = ""; verificationCodeSent = false; notify("Your email is verified.", false) }
                .onFailure { notify(it.message ?: "That code couldn't be verified.", true) }
            verifyingEmail = false
        }
    }
    var appLock by remember { mutableStateOf(session.appLockEnabled) }
    var passwordOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var signOutOpen by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf<Pair<String, String>?>(null) }
    var site by remember { mutableStateOf<SiteContent?>(null) }
    val biometricOk = remember { BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS }
    val tzOptions = remember(timezone) { (listOf(timezone) + TIMEZONES).distinct() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BentoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user, 56.dp, avatarUrl = avatarUrl, fullName = fullName); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(fullName.ifBlank { user.fullName }, style = MaterialTheme.typography.titleMedium, color = Bento.fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (user.emailVerified) VerifiedBadge(activePlan = canUpload, size = 17.dp)
                    }
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                    Row(Modifier.clickable { clipboard.setText(AnnotatedString(user.userId)); notify("Persora ID copied.", false) }, verticalAlignment = Alignment.CenterVertically) { Text("Persora ID ${user.userId}", style = MaterialTheme.typography.labelLarge, color = Bento.primary); Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.ContentCopy, null, tint = Bento.primary, modifier = Modifier.size(13.dp)) }
                }
                if (user.role == "admin") Pill("Admin", Tones.Purple, Icons.Outlined.AdminPanelSettings)
                IconButton(onClick = { editingProfile = !editingProfile }) { Icon(if (editingProfile) Icons.Outlined.ExpandLess else Icons.Outlined.Edit, if (editingProfile) "Close editor" else "Edit profile", tint = Bento.primary) }
            }
            Text("Members can share with you using this ID or your email.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 8.dp))
        }

        BentoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIconBox(if (user.emailVerified) Icons.Outlined.MarkEmailRead else Icons.Outlined.Email, if (user.emailVerified) Tones.Green else Tones.Blue, size = 42.dp, radius = 13.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Email verification", style = MaterialTheme.typography.titleMedium, color = Bento.fg)
                    Text("Your account address", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                }
                Pill(if (user.emailVerified) "Verified" else "Not verified", if (user.emailVerified) Tones.Green else Tones.Amber, if (user.emailVerified) Icons.Outlined.CheckCircle else Icons.Outlined.Info)
            }
            Spacer(Modifier.height(12.dp))
            if (user.emailVerified) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tones.Green.bg).border(1.dp, Tones.Green.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VerifiedBadge(activePlan = canUpload, size = 25.dp)
                    Column(Modifier.weight(1f)) {
                        Text(user.email, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(if (canUpload) "Verified · active paid plan" else "Verified · no active paid plan", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                    }
                }
                Text("The verified mark appears beside your name. Its color reflects your current plan status.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("Confirm the email registered to your account to show a verified mark beside your name.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bento.card).border(1.dp, Bento.border, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Bento.primarySoft), contentAlignment = Alignment.Center) { Text("1", style = MaterialTheme.typography.labelMedium, color = Bento.primary, fontWeight = FontWeight.Bold) }
                    Text("Send a one-time code", style = MaterialTheme.typography.labelMedium, color = Bento.fg, modifier = Modifier.padding(start = 7.dp))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = Bento.subtleFg, modifier = Modifier.size(15.dp))
                    Box(Modifier.padding(horizontal = 8.dp).size(24.dp).clip(CircleShape).background(if (verificationCodeSent) Bento.primarySoft else Bento.muted), contentAlignment = Alignment.Center) { Text("2", style = MaterialTheme.typography.labelMedium, color = if (verificationCodeSent) Bento.primary else Bento.subtleFg, fontWeight = FontWeight.Bold) }
                    Text("Enter and verify", style = MaterialTheme.typography.labelMedium, color = if (verificationCodeSent) Bento.fg else Bento.subtleFg)
                }
                Text("A six-digit code will be sent to", style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg, modifier = Modifier.padding(top = 12.dp))
                Text(user.email, style = MaterialTheme.typography.titleSmall, color = Bento.fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                PrimaryButton(if (sendingVerification) "Sending code…" else if (verificationCodeSent) "Send another code" else "Send verification code", onClick = ::sendVerificationCode, enabled = !sendingVerification, loading = sendingVerification, icon = Icons.Outlined.Email, modifier = Modifier.fillMaxWidth())
                if (verificationCodeSent) {
                    Spacer(Modifier.height(12.dp))
                    TextInput(verificationCode, { verificationCode = it.filter(Char::isDigit).take(6) }, "Six-digit code", keyboard = KeyboardType.Number, placeholder = "000000", supporting = "Codes expire in 10 minutes. Check your inbox and spam folder.")
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(if (verifyingEmail) "Verifying…" else "Verify email", onClick = ::verifyEmailCode, enabled = !verifyingEmail && verificationCode.length == 6, loading = verifyingEmail, icon = Icons.Outlined.CheckCircle, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (!canUpload) BentoCard {
            SectionHeading("File uploads", "Paid plan required")
            Text("You can still add, edit and delete records. New file, image and document uploads are paused; previously uploaded files remain accessible.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("View plans", onClick = { nav.navigate(Routes.BILLING) }, icon = Icons.Outlined.WorkspacePremium)
        }

        if (editingProfile) BentoCard {
            SectionHeading("Profile photo", "Photo, emoji or avatar")
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user, 72.dp, avatarUrl = avatarUrl, fullName = fullName); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoftButton(if (avatarBusy) "Preparing…" else "Upload photo", onClick = { if (!avatarBusy && canUpload) photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Icons.Outlined.AddAPhoto, enabled = canUpload && !avatarBusy)
                    if (avatarUrl.isNotBlank()) TextButton(onClick = { avatarUrl = "" }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Use initials", color = Bento.mutedFg) }
                    Text(if (canUpload) "JPG, PNG or WEBP · up to 8 MB · cropped square" else "New profile-photo uploads need an active paid plan. Emoji and built-in avatars remain available.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Emoji", style = MaterialTheme.typography.labelLarge, color = Bento.fg)
            Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PROFILE_AVATAR_EMOJI.forEach { emoji ->
                    val selected = avatarUrl == "emoji:$emoji"
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (selected) Bento.muted else Bento.muted).border(if (selected) 2.dp else 1.dp, if (selected) Bento.primary else Bento.border, CircleShape).clickable { avatarUrl = "emoji:$emoji" }, contentAlignment = Alignment.Center) { Text(emoji, fontSize = 22.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("DiceBear Adventurer", style = MaterialTheme.typography.labelLarge, color = Bento.fg)
            Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PROFILE_AVATAR_DICEBEAR.forEach { (name, url) ->
                    val selected = avatarUrl == url
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { avatarUrl = url }) {
                        Box(Modifier.size(52.dp).clip(CircleShape).border(if (selected) 2.dp else 1.dp, if (selected) Bento.primary else Bento.border, CircleShape), contentAlignment = Alignment.Center) { AsyncImage(model = url, contentDescription = name, modifier = Modifier.size(48.dp).clip(CircleShape).background(Bento.muted)) }
                        Text(name, style = MaterialTheme.typography.labelMedium, color = if (selected) Bento.primary else Bento.subtleFg)
                    }
                }
            }
        }

        if (editingProfile) BentoCard {
            SectionHeading("Profile", "Name & time zone")
            TextInput(fullName, { fullName = it }, "Full name", required = true)
            Spacer(Modifier.height(10.dp)); SelectInput(timezone, tzOptions, { timezone = it }, "Time zone", allowEmpty = false)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(if (savingProfile) "Saving…" else "Save profile", onClick = {
                if (fullName.isBlank()) { notify("Your name can't be empty.", true); return@PrimaryButton }
                if (avatarUrl.startsWith("data:image/") && avatarUrl != user.avatarUrl && !canUpload) { notify("New profile-photo uploads require an active paid plan. Emoji and built-in avatars remain available.", true); return@PrimaryButton }
                savingProfile = true
                scope.launch {
                    runCatchingSafe { api.updateProfile(fullName.trim(), timezone, avatarUrl); api.me() }
                        .onSuccess { me -> (me ?: user.copy(fullName = fullName.trim(), timezone = timezone, avatarUrl = avatarUrl)).let(session::updateUser); notify("Your profile has been updated.", false); editingProfile = false }
                        .onFailure { notify(it.message ?: "Couldn't save profile.", true) }
                    savingProfile = false
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !savingProfile && profileDirty, loading = savingProfile)
            if (profileDirty) Text("Unsaved changes — tap Save profile to sync them to the website too.", style = MaterialTheme.typography.bodySmall, color = Accents.amber.text, modifier = Modifier.padding(top = 6.dp))
        }

        BentoCard {
            SectionHeading("Security", "On this device")
            SettingRow(Icons.Outlined.Fingerprint, "App lock", if (!biometricOk) "Set up a fingerprint, face or screen lock in Android settings first." else "Require biometrics or your screen lock to open Persora.") {
                Switch(appLock, { on -> if (on && !biometricOk) { notify("No screen lock available on this device.", true) } else { appLock = on; session.setAppLock(on); notify(if (on) "App lock on." else "App lock off.", false) } }, enabled = biometricOk || appLock, colors = SwitchDefaults.colors(checkedTrackColor = Bento.primary))
            }
            SettingRow(Icons.Outlined.Password, "Change password", "Used on the website and in this app.", onClick = { passwordOpen = true })
        }

        BentoCard {
            SectionHeading("Reminders & alarms", "Android permissions")
            SettingRow(Icons.Outlined.NotificationsActive, "Notifications", "Open system settings to manage Persora's reminder and alarm channels.", onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) })
            if (android.os.Build.VERSION.SDK_INT >= 31) SettingRow(Icons.Outlined.Alarm, "Exact alarms", if (container.scheduler.canScheduleExact()) "Allowed — alarms ring on time even in Doze." else "Not allowed — tap to allow so alarms ring at the exact minute.", onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) })
            if (android.os.Build.VERSION.SDK_INT >= 34) SettingRow(Icons.Outlined.Fullscreen, "Full-screen alarms", if ((context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).canUseFullScreenIntent()) "Allowed — alarms open over the lock screen." else "Tap to allow alarms to open over the lock screen.", onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))) })
            if (android.os.Build.VERSION.SDK_INT >= 31 && !container.scheduler.canScheduleExact()) SettingRow(Icons.Outlined.Alarm, "Exact alarms", "Tap to allow exact alarms so they ring on time, even offline.", onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) })
            SettingRow(Icons.Outlined.CloudDone, "Offline copy", if (lastSyncedAt > 0) "Everything is cached on this phone · last synced ${Dates.formatRelative(java.time.Instant.ofEpochMilli(lastSyncedAt).toString())}. Pull down on any list to sync now." else "Syncing your vault for offline use…", onClick = { scope.launch { vault.refreshAll(); notify("Vault synced.", false) } })
            SettingRow(Icons.Outlined.BatterySaver, "Battery optimisation", "Exclude Persora so background alarms are never delayed.", onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        }

        BentoCard {
            SectionHeading("Your data", "Export & storage")
            SettingRow(Icons.Outlined.Download, if (exporting) "Preparing export…" else "Export my data", "Download a JSON export of your records (files stay in your vault).", onClick = {
                if (exporting) return@SettingRow
                exporting = true
                scope.launch { runCatchingSafe { val uri = withContext(Dispatchers.IO) { api.exportAccount().use { Files.stash(context, it.name.ifBlank { "persora-export.json" }, it.stream) } }; context.startActivity(Files.shareIntent(uri, "application/json", "Persora export")) }.onFailure { notify(it.message ?: "Export failed.", true) }; exporting = false }
            })
            SettingRow(Icons.Outlined.CloudQueue, "Storage & billing", "See usage and upgrade your plan.", onClick = { nav.navigate(Routes.BILLING) })
        }

        BentoCard {
            SectionHeading("About", "Persora for Android v${BuildConfig.VERSION_NAME}")
            SettingRow(Icons.Outlined.Language, "Open persora.pages.dev", "Same account, same vault, on the web.", onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.WEB_ORIGIN))) })
            SettingRow(Icons.Outlined.PrivacyTip, "Privacy policy", "", onClick = { scope.launch { val s = site ?: runCatchingSafe { api.loadSiteContent() }.getOrNull()?.also { site = it }; if (s != null) legal = s.privacyTitle to s.privacyBody else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.WEB_ORIGIN}/privacy"))) } })
            SettingRow(Icons.Outlined.Gavel, "Terms of service", "", onClick = { scope.launch { val s = site ?: runCatchingSafe { api.loadSiteContent() }.getOrNull()?.also { site = it }; if (s != null) legal = s.termsTitle to s.termsBody else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.WEB_ORIGIN}/terms"))) } })
            SettingRow(Icons.Outlined.SupportAgent, "Contact support", "", onClick = { scope.launch { val s = site ?: runCatchingSafe { api.loadSiteContent() }.getOrNull()?.also { site = it }; val email = s?.contactEmail.orEmpty(); if (email.isNotBlank()) context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email?subject=Persora%20Android%20support"))) else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.WEB_ORIGIN}/contact"))) } })
            if (user.role == "admin") Text("Administration (plans, payments, members, content) is managed from the website console.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 6.dp))
        }

        BentoCard {
            SettingRow(Icons.Outlined.Logout, "Sign out", "You'll need your password to sign back in.", onClick = { signOutOpen = true })
            SettingRow(Icons.Outlined.DeleteForever, "Delete account", "Permanently removes your account, records and files.", danger = true, onClick = { deleteOpen = true })
        }
    }

    if (passwordOpen) PasswordDialog(onDismiss = { passwordOpen = false })
    if (signOutOpen) ConfirmDialog("Sign out of Persora?", "Cached data on this device is cleared. Alarms scheduled by the app are cancelled.", confirmLabel = "Sign out", destructive = false, onConfirm = { signOutOpen = false; scope.launch { container.vault.clear(); container.scheduler.sync(emptyList()); session.signOut() } }, onDismiss = { signOutOpen = false })
    if (deleteOpen) {
        var confirm by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { deleteOpen = false }, shape = RoundedCornerShape(22.dp), containerColor = Bento.card, title = { Text("Delete your account?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("This permanently deletes your records, contacts, cards, medical files and uploads from Persora. It cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = Bento.fg); TextInput(confirm, { confirm = it }, "Type DELETE to confirm") } },
            confirmButton = { TextButton(onClick = { scope.launch { runCatchingSafe { api.deleteAccount() }.onSuccess { container.vault.clear(); container.scheduler.sync(emptyList()); session.sessionExpired(); notify("Account deleted.", false) }.onFailure { notify(it.message ?: "Could not delete.", true) } } }, enabled = confirm == "DELETE") { Text("Delete forever", color = Bento.danger, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Keep my account", color = Bento.mutedFg) } })
    }
    legal?.let { (title, body) -> AlertDialog(onDismissRequest = { legal = null }, shape = RoundedCornerShape(22.dp), containerColor = Bento.card, title = { Text(title) }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(body.ifBlank { "Not published yet." }, style = MaterialTheme.typography.bodyMedium, color = Bento.fg) } }, confirmButton = { TextButton(onClick = { legal = null }) { Text("Close") } }) }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, body: String, danger: Boolean = false, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ToneIconBox(icon, if (danger) Tones.Red else Tones.Blue, size = 36.dp, radius = 10.dp); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) Bento.danger else Bento.fg); if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = Bento.subtleFg)
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit) {
    val api = LocalContext.current.appContainer.api
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(22.dp), containerColor = Bento.card, title = { Text("Change password") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { TextInput(current, { current = it }, "Current password", password = true); TextInput(next, { next = it }, "New password", password = true, supporting = "At least 8 characters"); TextInput(again, { again = it }, "Repeat new password", password = true, isError = again.isNotEmpty() && again != next) } },
        confirmButton = { TextButton(onClick = { if (next.length < 8 || next != again) { notify("Check the new password.", true); return@TextButton }; busy = true; scope.launch { runCatchingSafe { api.changePassword(current, next) }.onSuccess { notify("Password updated.", false); onDismiss() }.onFailure { notify(it.message ?: "Could not update password.", true) }; busy = false } }, enabled = !busy) { Text("Update", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Bento.mutedFg) } })
}
