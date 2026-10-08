package app.persora.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.appContainer
import app.persora.android.data.model.AppUser
import app.persora.android.data.repository.SessionState

/** Observe the current session profile (including refreshed upload entitlement) in upload-capable screens. */
@Composable
fun observeCurrentUser(): AppUser? {
    val session = LocalContext.current.appContainer.session
    val state by session.state.collectAsStateWithLifecycle()
    return when (val current = state) {
        is SessionState.SignedIn -> current.user
        is SessionState.Locked -> current.user
        else -> null
    }
}
