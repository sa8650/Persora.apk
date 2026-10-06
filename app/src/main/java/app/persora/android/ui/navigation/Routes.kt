package app.persora.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import app.persora.android.data.model.Sections

object Routes {
    const val HOME = "home"
    const val SPACES = "spaces"
    const val SECTION = "section/{id}"
    const val ITEM = "item/{id}"
    const val EDITOR = "editor?section={section}&id={id}&folder={folder}&kind={kind}&share={share}"
    const val CONTACTS = "contacts"
    const val CONTACT = "contact/{id}"
    const val CONTACT_EDITOR = "contactEditor?id={id}"
    const val MEDICAL = "medical"
    const val MEDICAL_EDITOR = "medicalEditor?id={id}"
    const val TIMELINE = "timeline"
    const val SHARED = "shared"
    const val CARDS = "cards"
    const val CARD_EDITOR = "cardEditor?id={id}"
    const val BILLING = "billing"
    const val SETTINGS = "settings"
    const val MORE = "more"
    const val CALLS = "calls?dial={dial}"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val PUBLIC_CARD = "publicCard/{cardId}"

    fun section(id: String) = "section/$id"
    fun item(id: String) = "item/$id"
    fun editor(section: String, id: String? = null, folder: String? = null, kind: String? = null, share: String? = null) = "editor?section=$section&id=${id.orEmpty()}&folder=${folder.orEmpty()}&kind=${kind.orEmpty()}&share=${share.orEmpty()}"
    fun contact(id: String) = "contact/$id"
    fun contactEditor(id: String? = null) = "contactEditor?id=${id.orEmpty()}"
    fun medicalEditor(id: String? = null) = "medicalEditor?id=${id.orEmpty()}"
    fun cardEditor(id: String? = null) = "cardEditor?id=${id.orEmpty()}"
    fun publicCard(cardId: String) = "publicCard/$cardId"

    /** Phone bottom bar / tablet rail. */
    data class Primary(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)
    val primary = listOf(
        Primary(HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
        Primary(SPACES, "Spaces", Icons.Outlined.GridView, Icons.Filled.GridView),
        Primary(CONTACTS, "Contacts", Icons.Outlined.Person, Icons.Filled.Person),
        Primary(section("notes"), "Tasks", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
        Primary(MORE, "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
    )

    /** Everything else that lives under "More" (and in the expanded-width sidebar). */
    val secondary = listOf(
        Primary(calls(), "Calls", Icons.Outlined.Call),
        Primary(MEDICAL, "Medical records", Icons.Outlined.MonitorHeart),
        Primary(TIMELINE, "Life timeline", Icons.Outlined.EventNote),
        Primary(SHARED, "Shared with me", Icons.Outlined.Share),
        Primary(CARDS, "Business cards", Icons.Outlined.CreditCard),
        Primary(BILLING, "Storage & billing", Icons.Outlined.AccountBalanceWallet),
        Primary(SETTINGS, "Settings", Icons.Outlined.Settings),
    )

    /** Title + eyebrow for the top bar (breadcrumb-overline / breadcrumb-title on the web). */
    fun calls(dial: String? = null) = "calls?dial=${android.net.Uri.encode(dial.orEmpty())}"

    fun titleFor(route: String?, sectionId: String?): Pair<String, String> = when {
        route == null || route == HOME -> "Your personal space" to "Home"
        route == SPACES -> "All your spaces" to "Spaces"
        route == CONTACTS || route?.startsWith("contact") == true -> "People you know" to "Contacts"
        route == MEDICAL || route?.startsWith("medical") == true -> "Health archive" to "Medical records"
        route == TIMELINE -> "Your story, by date" to "Life timeline"
        route == SHARED -> "Shared documents" to "Shared with me"
        route == CARDS || route?.startsWith("cardEditor") == true -> "Your digital identity" to "Business cards"
        route == BILLING -> "Storage plan" to "Storage & billing"
        route == SETTINGS -> "Account" to "Settings"
        route == MORE -> "Everything else" to "More"
        route == CALLS -> "Recents & dialpad" to "Calls"
        route == SEARCH -> "Find anything" to "Search"
        route == NOTIFICATIONS -> "Reminders, alarms & sharing" to "Notifications"
        sectionId != null -> Sections[sectionId].let { it.eyebrow to it.label }
        else -> "Persora" to "Persora"
    }
}
