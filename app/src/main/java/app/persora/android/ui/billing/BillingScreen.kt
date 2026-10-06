package app.persora.android.ui.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.persora.android.appContainer
import app.persora.android.core.util.Dates
import app.persora.android.core.util.Files
import app.persora.android.data.model.BillingSnapshot
import app.persora.android.data.model.SubscriptionPlan
import app.persora.android.ui.components.*
import app.persora.android.ui.navigation.LocalNotify
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.ui.dashboard.StorageCard
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** BillingView.tsx: storage usage, plans, manual payment checkout (bKash/Nagad/bank…) and payment history. Admin review stays on the web console. */
@Composable
fun BillingScreen() {
    val container = LocalContext.current.appContainer
    val api = container.api
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<BillingSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkout by remember { mutableStateOf<SubscriptionPlan?>(null) }
    fun reload() { scope.launch { runCatchingSafe { api.loadBilling() }.onSuccess { snapshot = it; error = null; container.vault.storage.value = it.storage }.onFailure { error = humanizeError(it.message ?: "Couldn't load billing.", "error").first } } }
    LaunchedEffect(Unit) { reload() }

    val snap = snapshot
    if (snap == null) { if (error != null) EmptyState("Billing unavailable", error!!, Icons.Outlined.CloudOff) { QuietButton("Retry", onClick = ::reload) } else LoadingBlock(Modifier.fillMaxSize()); return }
    val usedPct = if (snap.storage.storageLimitBytes > 0) (snap.storage.bytesUsed.toFloat() / snap.storage.storageLimitBytes).coerceIn(0f, 1f) else 0f
    val pending = snap.payments.any { it.status == "pending" }
    val currency = snap.billingSettings.currency

    val vaultItems by container.vault.items.collectAsStateWithLifecycle()
    val offline by container.vault.offline.collectAsStateWithLifecycle()
    val lastSynced by container.vault.lastSyncedAt.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StorageCard(vaultItems, snap.storage, offline, lastSynced) }
        item {
            BentoCard {
                SectionHeading("Current plan", snap.subscription.plan_name.ifBlank { snap.storage.planName.ifBlank { "Free" } }) { Pill(snap.subscription.status.ifBlank { "active" }, if (snap.subscription.status == "active" || snap.subscription.status.isBlank()) Tones.Green else Tones.Amber) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) { Text(Files.formatSize(snap.storage.bytesUsed), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Bento.fg, letterSpacing = (-1).sp); Spacer(Modifier.width(6.dp)); Text("of ${trimGb(snap.storage.storageLimitGb)} GB used", style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg, modifier = Modifier.padding(bottom = 5.dp)) }
                LinearProgressIndicator(progress = { usedPct }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(CircleShape), color = if (usedPct > 0.9f) Bento.danger else Bento.primary, trackColor = Bento.muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill("${snap.storage.objectCount} files · ${Files.formatSize(snap.storage.fileBytes)}", Tones.Neutral, Icons.Outlined.Folder); Pill("${snap.storage.databaseRecordCount} records · ${Files.formatSize(snap.storage.databaseBytes)}", Tones.Neutral, Icons.Outlined.Storage) }
                snap.subscription.current_period_end?.let { Text("Renews / ends ${Dates.formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        if (pending) item { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Accents.amber.soft).border(1.dp, Accents.amber.line, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.HourglassTop, null, tint = Accents.amber.text); Spacer(Modifier.width(10.dp)); Text("A payment is awaiting review. We'll upgrade your storage as soon as it's confirmed.", style = MaterialTheme.typography.bodySmall, color = Accents.amber.text) } }
        if (!snap.billingSettings.billingEnabled) item { Text("Upgrades are currently paused by the Persora team. Your existing plan keeps working.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) }
        item { Text("PLANS", style = MaterialTheme.typography.labelSmall, color = Bento.subtleFg) }
        items(snap.plans.filter { it.active }.sortedBy { it.sort_order }, key = { it.id }) { plan ->
            val current = plan.id == snap.subscription.plan_id
            // Like BillingView.tsx: any paid plan opens the checkout drawer; blockers (paused billing, pending review) are explained inside it.
            val choose: () -> Unit = {
                when {
                    current -> notify("This is already your current plan.", false)
                    plan.monthly_price <= 0 -> notify("The free plan is applied automatically.", false)
                    else -> checkout = plan
                }
            }
            BentoCard(onClick = choose, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneIconBox(if (plan.monthly_price == 0.0) Icons.Outlined.Spa else Icons.Outlined.WorkspacePremium, if (current) Tones.Green else Tones.Blue, size = 40.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(plan.name, style = MaterialTheme.typography.titleSmall, color = Bento.fg); if (current) { Spacer(Modifier.width(6.dp)); Pill("Current", Tones.Green) } }
                        Text("${trimGb(plan.storage_gb)} GB · ${plan.description.ifBlank { "Private storage for files and records" }}", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
                    }
                    Column(horizontalAlignment = Alignment.End) { Text(if (plan.monthly_price == 0.0) "Free" else "${plan.currency.ifBlank { currency }} ${fmt(plan.monthly_price)}", style = MaterialTheme.typography.titleMedium, color = Bento.fg); if (plan.monthly_price > 0) Text("per month", style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg) }
                }
                if (!current && plan.monthly_price > 0) {
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Select plan", onClick = choose, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.WorkspacePremium)
                }
            }
        }
        if (snap.payments.isNotEmpty()) {
            item { Text("PAYMENT HISTORY", style = MaterialTheme.typography.labelSmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 6.dp)) }
            items(snap.payments, key = { it.id }) { p ->
                BentoCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("${p.plan_name} · ${trimGb(p.storage_gb)} GB", style = MaterialTheme.typography.titleSmall, color = Bento.fg); Text("${p.method} · ref ${p.reference} · ${Dates.formatDate(p.submitted_at)}" + (p.term_months?.let { " · $it mo" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg); p.admin_note?.takeIf { it.isNotBlank() }?.let { Text("Note: $it", style = MaterialTheme.typography.bodySmall, color = Accents.amber.text) } }
                        Column(horizontalAlignment = Alignment.End) { Text("${p.currency} ${fmt(p.amount)}", style = MaterialTheme.typography.titleSmall); Pill(p.status, when (p.status) { "approved", "confirmed" -> Tones.Green; "rejected" -> Tones.Red; else -> Tones.Amber }) }
                    }
                }
            }
        }
        item { Text("Payments are verified manually by the Persora team from the admin console. Keep your transaction reference handy.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg) }
    }
    checkout?.let { plan -> CheckoutSheet(snap, plan, onDismiss = { checkout = null }, onDone = { checkout = null; reload() }) }
}

private fun trimGb(v: Double) = if (v == floor(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
private fun fmt(v: Double) = if (v == floor(v)) String.format(Locale.US, "%,.0f", v) else String.format(Locale.US, "%,.2f", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutSheet(snap: BillingSnapshot, plan: SubscriptionPlan, onDismiss: () -> Unit, onDone: () -> Unit) {
    val api = LocalContext.current.appContainer.api
    val notify = LocalNotify.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val methods = snap.paymentMethods.filter { it.active }.sortedBy { it.sort_order }
    var methodId by remember { mutableStateOf(methods.firstOrNull()?.id.orEmpty()) }
    var period by remember { mutableStateOf("monthly") }
    var duration by remember { mutableStateOf("1") }
    var reference by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val minTerm = maxOf(1, snap.billingSettings.minTermMonths); val maxTerm = minOf(120, maxOf(minTerm, snap.billingSettings.maxTermMonths))
    val periodMonths = if (period == "yearly") 12 else 1
    val minDur = ceil(minTerm / periodMonths.toDouble()).toInt(); val maxDur = floor(maxTerm / periodMonths.toDouble()).toInt()
    val parsed = duration.toIntOrNull()
    val valid = maxDur >= minDur && parsed != null && parsed in minDur..maxDur
    val months = (parsed ?: minDur).coerceIn(minDur, maxOf(minDur, maxDur)) * periodMonths
    val total = ((plan.monthly_price * months) * 100).roundToInt() / 100.0
    val method = methods.firstOrNull { it.id == methodId }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Bento.card, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("Secure manual checkout"); Text("${plan.name} · ${trimGb(plan.storage_gb)} GB", style = MaterialTheme.typography.titleLarge, color = Bento.fg)
            val pendingReview = snap.payments.any { it.status == "pending" }
            if (pendingReview) Notice(Icons.Outlined.Schedule, "Your previous payment request is awaiting manual review. You can start another checkout once it has been approved or rejected.")
            if (!snap.billingSettings.billingEnabled) Notice(Icons.Outlined.Lock, "Manual payments are not open yet. You can review the plan and pricing, but checkout is currently paused by the Persora team.")
            val canCheckout = snap.billingSettings.billingEnabled && !pendingReview && methods.isNotEmpty()
            SegmentedTabs(listOf("Monthly", "Yearly"), if (period == "yearly") 1 else 0, { period = if (it == 1) "yearly" else "monthly"; duration = minDur.toString() })
            TextInput(duration, { duration = it.filter { c -> c.isDigit() }.take(3) }, if (period == "yearly") "Number of years" else "Number of months", keyboard = KeyboardType.Number, supporting = if (maxDur < minDur) "This period isn't available for the current term limits." else "Between $minDur and $maxDur", isError = !valid)
            BentoCard(padding = 12.dp) { DetailRow("Term", "$months month${if (months == 1) "" else "s"}"); DetailRow("Total due", "${plan.currency.ifBlank { snap.billingSettings.currency }} ${fmt(total)}") }
            if (methods.isEmpty()) Text("No payment methods are configured yet. Please try again later.", color = Bento.danger, style = MaterialTheme.typography.bodySmall)
            else {
                Text("Pay with", style = MaterialTheme.typography.labelLarge, color = Bento.fg)
                methods.forEach { m -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, if (methodId == m.id) Bento.primary else Bento.border, RoundedCornerShape(12.dp)).clickable { methodId = m.id }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(methodId == m.id, { methodId = m.id }, colors = RadioButtonDefaults.colors(selectedColor = Bento.primary)); Column { Text(m.name, style = MaterialTheme.typography.titleSmall); Text(m.accountName, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg) } } }
                method?.let { m ->
                    BentoCard(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Send to", style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg); Text(m.accountIdentifier, style = MaterialTheme.typography.titleMedium, color = Bento.fg) }; TextButton(onClick = { clipboard.setText(AnnotatedString(m.accountIdentifier)); notify("Copied.", false) }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Copy") } }
                        if (m.instructions.isNotBlank()) Text(m.instructions, style = MaterialTheme.typography.bodySmall, color = Bento.fg, modifier = Modifier.padding(top = 6.dp))
                        if (snap.billingSettings.manualInstructions.isNotBlank()) Text(snap.billingSettings.manualInstructions, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                TextInput(reference, { reference = it }, "Transaction ID / reference", required = true, placeholder = "e.g. 8H2K4L9M1P")
            }
            PrimaryButton(if (busy) "Submitting…" else "Submit payment for review", onClick = {
                if (reference.isBlank() || method == null || !valid) { notify("Add the transaction reference and a valid term.", true); return@PrimaryButton }
                busy = true
                scope.launch { runCatchingSafe { api.submitPayment(plan.id, method.id, reference.trim(), period, parsed!!) }.onSuccess { notify("Payment submitted. We'll confirm it shortly.", false); onDone() }.onFailure { notify(it.message ?: "Could not submit payment.", true) }; busy = false }
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy && canCheckout, loading = busy)
        }
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Accents.amber.soft).border(1.dp, Accents.amber.line, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accents.amber.text, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Accents.amber.text)
    }
}
