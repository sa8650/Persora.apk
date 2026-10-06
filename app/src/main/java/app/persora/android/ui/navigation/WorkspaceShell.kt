package app.persora.android.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.persora.android.ui.components.DetailSheet
import app.persora.android.ui.vault.ItemDetailScreen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.window.core.layout.WindowWidthSizeClass
import app.persora.android.DeepLink
import app.persora.android.appContainer
import app.persora.android.data.model.AppUser
import app.persora.android.ui.billing.BillingScreen
import app.persora.android.ui.businesscards.BusinessCardEditorScreen
import app.persora.android.ui.businesscards.BusinessCardsScreen
import app.persora.android.ui.businesscards.PublicCardScreen
import app.persora.android.ui.components.ToastHost
import app.persora.android.ui.components.rememberToastState
import app.persora.android.ui.components.toastKindFor
import app.persora.android.ui.components.UserAvatar
import app.persora.android.ui.contacts.ContactDetailScreen
import app.persora.android.ui.contacts.ContactEditorScreen
import app.persora.android.ui.contacts.ContactsScreen
import app.persora.android.ui.calls.CallLogScreen
import app.persora.android.ui.dashboard.DashboardScreen
import app.persora.android.ui.dashboard.MoreScreen
import app.persora.android.ui.dashboard.NotificationsScreen
import app.persora.android.ui.dashboard.SpacesScreen
import app.persora.android.ui.medical.MedicalEditorScreen
import app.persora.android.ui.medical.MedicalRecordsScreen
import app.persora.android.ui.search.SearchScreen
import app.persora.android.ui.settings.SettingsScreen
import app.persora.android.ui.shared.SharedScreen
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Accents
import app.persora.android.ui.theme.MonoCaption
import app.persora.android.ui.timeline.TimelineScreen
import app.persora.android.ui.vault.ItemDetailScreen
import app.persora.android.ui.vault.ItemEditorScreen
import app.persora.android.ui.vault.SectionScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Snackbar-style notify() available to every screen (toast-notice on the web). */
val LocalNotify = staticCompositionLocalOf<(String, Boolean) -> Unit> { { _, _ -> } }
val LocalNav = staticCompositionLocalOf<NavHostController> { error("No NavController") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceShell(user: AppUser, offline: Boolean, deepLink: DeepLink?, onDeepLinkConsumed: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vault = container.vault
    val nav = rememberNavController()
    val toast = rememberToastState()
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val sectionArg = backStack?.arguments?.getString("id")?.takeIf { route == Routes.SECTION }
    val notifications by vault.notifications.collectAsStateWithLifecycle()
    val lastError by vault.lastError.collectAsStateWithLifecycle()
    val repoOffline by vault.offline.collectAsStateWithLifecycle()
    val isOffline = offline || repoOffline
    val windowClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val lifecycleOwner = LocalLifecycleOwner.current

    val notify: (String, Boolean) -> Unit = { message, isError ->
        if (!message.contains("left the composition", ignoreCase = true)) toast.show(message, toastKindFor(message, isError))
    }

    // Load cache instantly, then reconcile with the API; poll every 30 s while in the foreground (same cadence as the web).
    LaunchedEffect(user.id) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vault.loadFromCache() }; vault.refreshAll(); val interest = container.session.primaryInterest(); if (interest != null && vault.items.value.isEmpty()) { /* first run: open the chosen space */ nav.navigate(if (interest == "contacts") Routes.CONTACTS else if (interest == "medical-records") Routes.MEDICAL else Routes.section(interest)) } }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vault.launch { vault.refreshAll() }
                Lifecycle.Event.ON_STOP -> if (container.session.appLockEnabled) container.session.lock()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { var tick = 0; while (true) { delay(30_000); tick++; vault.launch { if (tick % 4 == 0) vault.refreshAll() else vault.refreshLight() } } }
    LaunchedEffect(lastError) { lastError?.let { toast.error(it); vault.clearError() } }
    LaunchedEffect(deepLink) {
        deepLink ?: return@LaunchedEffect
        when {
            deepLink.publicCardId != null -> nav.navigate(Routes.publicCard(deepLink.publicCardId))
            deepLink.itemId != null -> Details.openItem(deepLink.itemId)
            deepLink.view == "notes" -> nav.navigate(Routes.section("notes"))
            deepLink.view == "shared" -> nav.navigate(Routes.SHARED)
            deepLink.view == "calls" -> nav.navigate(Routes.calls(deepLink.dialNumber))
        }
        onDeepLinkConsumed()
    }

    val (eyebrow, title) = Routes.titleFor(route, sectionArg)
    val unread = notifications.any { it.readAt == null }
    val hideChrome = route == Routes.PUBLIC_CARD
    // Tapping a primary tab always lands on that tab's root (popping notifications, detail or editor screens on top).
    val navigateTop: (String) -> Unit = { target -> nav.navigate(target) { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true } }
    // A transient message shouldn't follow the user to the next screen.
    LaunchedEffect(route) { toast.dismiss() }

    CompositionLocalProvider(LocalNotify provides notify, LocalNav provides nav) {
        // Routes where pulling down starts a background sync (list/overview screens; editors are excluded).
        val pullable = route != null && !hideChrome && route !in setOf(Routes.EDITOR, Routes.CONTACT_EDITOR, Routes.MEDICAL_EDITOR, Routes.CARD_EDITOR, Routes.SEARCH)
        // Only a user's pull-down shows an indicator; the 30 s background sync is completely silent.
        var manualRefreshing by remember { mutableStateOf(false) }
        val content: @Composable (PaddingValues) -> Unit = { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                PullToRefreshBox(
                    isRefreshing = manualRefreshing,
                    onRefresh = { if (pullable && !manualRefreshing) scope.launch { manualRefreshing = true; try { vault.refreshAll() } finally { manualRefreshing = false } } },
                    modifier = Modifier.fillMaxSize(), indicator = { },
                ) { WorkspaceNavHost(nav, user) }
                androidx.compose.animation.AnimatedVisibility(visible = manualRefreshing, modifier = Modifier.align(Alignment.TopCenter), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                    Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(Bento.card).border(1.dp, Bento.ring, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp, color = Bento.fg); Spacer(Modifier.width(8.dp)); Text("SYNCING", style = MonoCaption, color = Bento.mutedFg)
                    }
                }
            }
        }
        val topBar: @Composable () -> Unit = {
            if (!hideChrome) Column(Modifier.fillMaxWidth().background(Bento.bg)) {
                TopAppBar(
                    title = {
                        Column { Text(eyebrow.uppercase(), style = MonoCaption, color = Bento.mutedFg, maxLines = 1); Text(title, style = MaterialTheme.typography.titleLarge, color = Bento.fg, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    actions = {
                        if (isOffline) Icon(Icons.Outlined.CloudOff, "Offline — showing cached records", tint = Accents.amber.text, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                        IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Outlined.Search, "Search your vault", tint = Bento.mutedFg) }
                        Box {
                            IconButton(onClick = { nav.navigate(Routes.NOTIFICATIONS) }) { Icon(Icons.Outlined.Notifications, "Notifications", tint = Bento.mutedFg) }
                            if (unread) Box(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 11.dp).size(7.dp).clip(CircleShape).background(Accents.rose.c500).border(1.5.dp, Bento.bg, CircleShape))
                        }
                        IconButton(onClick = { navigateTop(Routes.SETTINGS) }) { UserAvatar(user, 30.dp) }
                        Spacer(Modifier.width(4.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Bento.border))
            }
        }
        Box(Modifier.fillMaxSize().background(Bento.bg)) {
            when {
                windowClass == WindowWidthSizeClass.EXPANDED && !hideChrome -> PermanentNavigationDrawer(drawerContent = { Sidebar(user, route, sectionArg, onNavigate = navigateTop) }) {
                    Scaffold(topBar = topBar, containerColor = Color.Transparent, content = content)
                }
                windowClass == WindowWidthSizeClass.MEDIUM && !hideChrome -> Row(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxHeight().width(76.dp).background(Bento.card).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Routes.primary.forEach { item -> BentoTab(item.label, if (isSelected(route, sectionArg, item.route)) item.selectedIcon else item.icon, isSelected(route, sectionArg, item.route), Modifier.width(64.dp)) { navigateTop(item.route) } }
                    }
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Bento.border))
                    Scaffold(topBar = topBar, containerColor = Color.Transparent, content = content)
                }
                else -> Scaffold(
                    topBar = topBar, containerColor = Color.Transparent,
                    bottomBar = { if (!hideChrome) BentoBottomBar(route, sectionArg, onNavigate = navigateTop) },
                    content = content,
                )
            }
            // App-wide record drawers (item / contact) slide up over everything, including the tab bar.
            Details.itemId?.let { id -> DetailSheet(onDismiss = { Details.close() }) { ItemDetailScreen(id, onClose = { Details.close() }) } }
            Details.contactId?.let { id -> DetailSheet(onDismiss = { Details.close() }) { ContactDetailScreen(id, onClose = { Details.close() }) } }
            ToastHost(toast, bottomPadding = if (windowClass == WindowWidthSizeClass.COMPACT && !hideChrome) 92.dp else 24.dp)
        }
    }
}

/** Flat bento tab bar: card surface, 1 px top hairline, icon + mono uppercase label, foreground when active. */
@Composable
private fun BentoBottomBar(route: String?, sectionArg: String?, onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Bento.card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Bento.border))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Routes.primary.forEach { item ->
                val selected = isSelected(route, sectionArg, item.route)
                BentoTab(item.label, if (selected) item.selectedIcon else item.icon, selected, Modifier.weight(1f).fillMaxHeight()) { onNavigate(item.route) }
            }
        }
    }
}

@Composable
private fun BentoTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) Bento.primary else Bento.subtleFg, label = "tab-tint")
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(width = 40.dp, height = 26.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) Bento.primarySoft else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label.uppercase(), style = MonoCaption.copy(fontSize = 7.5.sp, letterSpacing = 1.2.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium), color = tint, maxLines = 1)
    }
}

private fun isSelected(route: String?, sectionArg: String?, target: String): Boolean = when {
    target.startsWith("section/") -> route == Routes.SECTION && sectionArg == target.removePrefix("section/")
    target == Routes.MORE -> route == Routes.MORE || Routes.secondary.any { it.route == route }
    target == Routes.SPACES -> route == Routes.SPACES || (route == Routes.SECTION && sectionArg != "notes")
    target == Routes.CONTACTS -> route == Routes.CONTACTS || route?.startsWith("contact") == true
    else -> route == target
}

@Composable
private fun WorkspaceNavHost(nav: NavHostController, user: AppUser) {
    NavHost(nav, startDestination = Routes.HOME, enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) }, exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) }) {
        composable(Routes.HOME) { DashboardScreen(user) }
        composable(Routes.SPACES) { SpacesScreen() }
        composable(Routes.SECTION, arguments = listOf(navArgument("id") { type = NavType.StringType })) { SectionScreen(it.arguments!!.getString("id")!!) }
        composable(Routes.ITEM, arguments = listOf(navArgument("id") { type = NavType.StringType })) { ItemDetailScreen(it.arguments!!.getString("id")!!) }
        composable(Routes.EDITOR, arguments = listOf(navArgument("section") { type = NavType.StringType }, navArgument("id") { defaultValue = "" }, navArgument("folder") { defaultValue = "" }, navArgument("kind") { defaultValue = "" }, navArgument("share") { defaultValue = "" })) {
            ItemEditorScreen(it.arguments!!.getString("section")!!, it.arguments!!.getString("id")!!.ifBlank { null }, it.arguments!!.getString("folder")!!.ifBlank { null }, it.arguments!!.getString("kind")!!.ifBlank { null }, it.arguments!!.getString("share")!!.ifBlank { null })
        }
        composable(Routes.CONTACTS) { ContactsScreen() }
        composable(Routes.CONTACT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { ContactDetailScreen(it.arguments!!.getString("id")!!) }
        composable(Routes.CONTACT_EDITOR, arguments = listOf(navArgument("id") { defaultValue = "" })) { ContactEditorScreen(it.arguments!!.getString("id")!!.ifBlank { null }) }
        composable(Routes.MEDICAL) { MedicalRecordsScreen() }
        composable(Routes.MEDICAL_EDITOR, arguments = listOf(navArgument("id") { defaultValue = "" })) { MedicalEditorScreen(it.arguments!!.getString("id")!!.ifBlank { null }) }
        composable(Routes.TIMELINE) { TimelineScreen() }
        composable(Routes.SHARED) { SharedScreen(user) }
        composable(Routes.CARDS) { BusinessCardsScreen() }
        composable(Routes.CARD_EDITOR, arguments = listOf(navArgument("id") { defaultValue = "" })) { BusinessCardEditorScreen(it.arguments!!.getString("id")!!.ifBlank { null }) }
        composable(Routes.BILLING) { BillingScreen() }
        composable(Routes.SETTINGS) { SettingsScreen(user) }
        composable(Routes.MORE) { MoreScreen() }
        composable(Routes.CALLS, arguments = listOf(navArgument("dial") { defaultValue = "" })) { entry -> CallLogScreen(initialDial = entry.arguments?.getString("dial")?.takeIf { it.isNotBlank() }) }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen() }
        composable(Routes.PUBLIC_CARD, arguments = listOf(navArgument("cardId") { type = NavType.StringType })) { PublicCardScreen(it.arguments!!.getString("cardId")!!) }
    }
}
