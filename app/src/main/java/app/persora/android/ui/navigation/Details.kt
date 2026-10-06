package app.persora.android.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide "item view" drawer. Any screen calls [openItem]/[openContact]; the workspace shell renders the
 * bottom drawer (slides up over whatever is on screen) so every record view feels the same.
 */
object Details {
    var itemId by mutableStateOf<String?>(null)
        private set
    var contactId by mutableStateOf<String?>(null)
        private set

    fun openItem(id: String) { contactId = null; itemId = id }
    fun openContact(id: String) { itemId = null; contactId = id }
    fun close() { itemId = null; contactId = null }

    /** Routes like `item/<id>` or `contact/<id>` (search hits) → drawer; anything else → null (navigate normally). */
    fun openRoute(route: String): Boolean = when {
        route.startsWith("item/") -> { openItem(route.removePrefix("item/")); true }
        route.startsWith("contact/") && !route.startsWith("contactEditor") -> { openContact(route.removePrefix("contact/")); true }
        else -> false
    }
}
