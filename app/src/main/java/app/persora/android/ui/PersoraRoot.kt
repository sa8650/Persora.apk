package app.persora.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.persora.android.DeepLink
import app.persora.android.appContainer
import app.persora.android.data.repository.SessionState
import app.persora.android.ui.auth.AuthScreen
import app.persora.android.ui.auth.LockScreen
import app.persora.android.ui.boot.PersoraBootScreen
import app.persora.android.ui.navigation.WorkspaceShell
import app.persora.android.ui.onboarding.OnboardingScreen
import app.persora.android.ui.onboarding.ProtectScreen

/** Equivalent of App.tsx's top-level switch: boot → (onboarding | auth) → lock → workspace. */
@Composable
fun PersoraRoot(deepLink: DeepLink?, onDeepLinkConsumed: () -> Unit) {
    val container = LocalContext.current.appContainer
    val session = container.session
    val state by session.state.collectAsStateWithLifecycle()
    var authMode by remember { mutableStateOf<String?>(null) }      // null | "signin" | "register"
    var showProtect by remember { mutableStateOf(false) }
    var appLock by remember { mutableStateOf(session.appLockEnabled) }

    // Off the main thread: encrypted-prefs init + the /auth/me round-trip must never stall the boot animation.
    LaunchedEffect(Unit) { if (state is SessionState.Booting) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { session.restore() } }

    Crossfade(targetState = state, label = "root") { s ->
        when (s) {
            SessionState.Booting -> PersoraBootScreen()
            SessionState.NeedsOnboarding -> when (authMode) {
                null -> OnboardingScreen(onFinished = { interest -> session.completeOnboarding(interest); authMode = "register" }, onSignIn = { authMode = "signin" })
                else -> AuthScreen(session, startInRegister = authMode == "register", onBack = { authMode = null }, onAuthenticated = { isNew -> session.completeOnboarding(session.primaryInterest()); showProtect = isNew })
            }
            SessionState.SignedOut -> AuthScreen(session, startInRegister = authMode == "register", onBack = null, onAuthenticated = { isNew -> showProtect = isNew })
            is SessionState.Locked -> LockScreen(onUnlocked = { session.unlock() }, onSignOut = { container.vault.launch { session.signOut() } })
            is SessionState.SignedIn -> if (showProtect) {
                ProtectScreen(appLockEnabled = appLock, onToggleAppLock = { appLock = it; session.setAppLock(it) }, onDone = { showProtect = false })
            } else {
                WorkspaceShell(user = s.user, offline = s.offline, deepLink = deepLink, onDeepLinkConsumed = onDeepLinkConsumed)
            }
        }
    }
}
