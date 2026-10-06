package app.persora.android.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.persora.android.data.model.Sections
import app.persora.android.ui.components.Eyebrow
import app.persora.android.ui.components.PrimaryButton
import app.persora.android.ui.components.ToneIconBox
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.components.IconTile
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.Accent
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.launch

/**
 * Onboarding modelled on the strongest Mobbin flows for storage / personal-management apps
 * (Dropbox Android, Notion, Wise, 1Password): a short value carousel with page control and a persistent
 * "I already have an account" exit, then a single personalization question, then account creation.
 * Permission priming (notifications, exact alarms, app lock) is deferred until *after* sign-in (ProtectScreen).
 */
private data class OnboardingPage(val eyebrow: String, val title: String, val body: String, val icons: List<Pair<ImageVector, Accent>>)

private val pages = listOf(
    OnboardingPage("Personal vault", "Everything important,\nin one private place.", "Passports, certificates, warranties, accounts and family records—organized the way you think about them.",
        listOf(Icons.Outlined.Badge to Accents.sky, Icons.Outlined.School to Accents.violet, Icons.Outlined.Favorite to Accents.rose, Icons.Outlined.ShoppingBag to Accents.sky, Icons.Outlined.Key to Accents.indigo)),
    OnboardingPage("Never miss a date", "Renewals, expiries and\nfollow-ups, handled.", "Subscriptions, document expiry, warranty end dates, tasks, reminders and alarms—Persora keeps the dates in sight.",
        listOf(Icons.Outlined.CreditCard to Accents.orange, Icons.Outlined.NotificationsActive to Accents.sky, Icons.Outlined.Alarm to Accents.violet, Icons.Outlined.EventNote to Accents.violet, Icons.Outlined.CheckCircle to Accents.emerald)),
    OnboardingPage("People & health", "Contacts, cards and\nmedical records—together.", "Keep contacts with photos, share a digital business card by QR, and file prescriptions, lab tests and visits with follow-up reminders.",
        listOf(Icons.Outlined.ContactPage to Accents.sky, Icons.Outlined.BusinessCenter to Accents.slate, Icons.Outlined.MonitorHeart to Accents.rose, Icons.Outlined.QrCode2 to Accents.sky, Icons.Outlined.Share to Accents.amber)),
    OnboardingPage("Private by design", "Yours, and only yours.", "Files live in a private bucket served only to you. Sessions expire. Passwords, PINs and CVVs are never stored. Lock the app with your fingerprint.",
        listOf(Icons.Outlined.VerifiedUser to Accents.sky, Icons.Outlined.Fingerprint to Accents.sky, Icons.Outlined.Cloud to Accents.sky, Icons.Outlined.Lock to Accents.emerald, Icons.Outlined.Timer to Accents.amber)),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: (interest: String?) -> Unit, onSignIn: () -> Unit) {
    var step by remember { mutableStateOf(0) } // 0 = carousel, 1 = personalize
    AnimatedContent(step, transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) }, label = "onboarding") { s ->
        if (s == 0) Carousel(onContinue = { step = 1 }, onSignIn = onSignIn) else Personalize(onDone = onFinished, onBack = { step = 0 })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Carousel(onContinue: () -> Unit, onSignIn: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bento.bg, Bento.bg))).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(9.dp))
            Text("Persora", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Bento.fg, letterSpacing = (-0.8).sp)
            Spacer(Modifier.weight(1f))
            if (!last) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.lastIndex) } }) { Text("Skip", color = Bento.mutedFg) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Illustration(page.icons, active = pager.currentPage == index)
                Spacer(Modifier.height(34.dp))
                Eyebrow(page.eyebrow)
                Spacer(Modifier.height(8.dp))
                Text(page.title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp, lineHeight = 33.sp), color = Bento.fg, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = MaterialTheme.typography.bodyLarge, color = Bento.mutedFg, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 440.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PageDots(pages.size, pager.currentPage)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(if (last) "Get started" else "Continue", onClick = { if (last) onContinue() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp))
            TextButton(onClick = onSignIn, modifier = Modifier.padding(top = 4.dp)) { Text("I already have an account", color = Bento.primary, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Personalize(onDone: (String?) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    val options = listOf("documents", "subscriptions", "contacts", "medical-records", "notes", "personal-finance", "academics", "family", "business-card")
    Column(Modifier.fillMaxSize().background(Bento.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = Bento.mutedFg); Spacer(Modifier.width(4.dp)); Text("Back", color = Bento.mutedFg) }
            Spacer(Modifier.weight(1f)); Text("Step 1 of 2", style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg)
        }
        Spacer(Modifier.height(10.dp))
        Eyebrow("Make it yours")
        Spacer(Modifier.height(6.dp))
        Text("What would you like to organize first?", style = MaterialTheme.typography.headlineMedium, color = Bento.fg)
        Spacer(Modifier.height(6.dp))
        Text("We'll open that space first and tailor your getting-started checklist. You can change this anytime.", style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg)
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(options) { id ->
                val (label, icon, tone) = when (id) {
                    "contacts" -> Triple("Contacts", Icons.Outlined.ContactPage, Tones.Blue)
                    "medical-records" -> Triple("Medical records", Icons.Outlined.MonitorHeart, Tones.Red)
                    else -> Sections[id].let { Triple(it.label, it.icon, Tones.byName(it.color)) }
                }
                val active = selected == id
                val border by animateDpAsState(if (active) 2.dp else 1.dp, label = "b")
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) Bento.muted else Bento.card).border(border, if (active) Bento.primary else Bento.border, RoundedCornerShape(16.dp)).clickable { selected = if (active) null else id }.padding(14.dp),
                ) {
                    ToneIconBox(icon, tone, size = 36.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall, color = if (active) Bento.primary else Bento.fg)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Create my account", onClick = { onDone(selected) }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { onDone(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Not sure yet", color = Bento.mutedFg) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Illustration(icons: List<Pair<ImageVector, Accent>>, active: Boolean) {
    val scale by animateFloatAsState(if (active) 1f else 0.92f, label = "scale")
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(200.dp * scale).clip(CircleShape).background(Brush.radialGradient(listOf(Bento.muted, Color.Transparent))))
        val positions = listOf(Pair(0.dp, (-86).dp), Pair(82.dp, (-27).dp), Pair(51.dp, 70.dp), Pair((-51).dp, 70.dp), Pair((-82).dp, (-27).dp))
        icons.forEachIndexed { i, (icon, accent) ->
            val (x, y) = positions[i % positions.size]
            Box(Modifier.offset(x * scale, y * scale).rotate(if (i % 2 == 0) -6f else 5f)) { IconTile(icon, accent, size = 52.dp, radius = 16.dp) }
        }
        Box(Modifier.size(64.dp).shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = Bento.primary.copy(alpha = 0.3f), spotColor = Bento.primary.copy(alpha = 0.3f)).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Bento.primary, Bento.primary))), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = Bento.primaryFg, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 7.dp, label = "dot")
            Box(Modifier.height(7.dp).width(width).clip(CircleShape).background(if (i == current) Bento.primary else Bento.borderStrong))
        }
    }
}

@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp = 30.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Bento.primary), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.VerifiedUser, null, tint = Bento.primaryFg, modifier = Modifier.size(size * 0.58f))
    }
}
