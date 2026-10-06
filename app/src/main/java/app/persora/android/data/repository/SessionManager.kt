package app.persora.android.data.repository

import app.persora.android.core.network.ApiException
import app.persora.android.core.network.PersistentCookieJar
import app.persora.android.core.storage.JsonCache
import app.persora.android.core.storage.SecurePrefs
import app.persora.android.data.api.PersoraApi
import app.persora.android.data.model.AppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface SessionState {
    data object Booting : SessionState
    data object NeedsOnboarding : SessionState
    data object SignedOut : SessionState
    data class Locked(val user: AppUser) : SessionState
    data class SignedIn(val user: AppUser, val offline: Boolean = false) : SessionState
}

/**
 * Restores the cookie session on launch (POST-less `GET /auth/me`, exactly like App.tsx's authRestoring phase),
 * then exposes sign-in / sign-up / sign-out plus the biometric app-lock state.
 */
class SessionManager(
    private val api: PersoraApi,
    private val cookieJar: PersistentCookieJar,
    private val prefs: SecurePrefs,
    private val cache: JsonCache,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<SessionState>(SessionState.Booting)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val currentUser: AppUser? get() = when (val s = _state.value) { is SessionState.SignedIn -> s.user; is SessionState.Locked -> s.user; else -> null }
    val appLockEnabled: Boolean get() = prefs.getBoolean(SecurePrefs.KEY_APP_LOCK)
    val onboardingComplete: Boolean get() = prefs.getBoolean(SecurePrefs.KEY_ONBOARDED)

    suspend fun restore() {
        val startedAt = System.currentTimeMillis()
        val next: SessionState = if (!cookieJar.hasSession()) {
            if (onboardingComplete) SessionState.SignedOut else SessionState.NeedsOnboarding
        } else {
            try {
                val user = api.me()
                if (user == null) { cookieJar.clear(); SessionState.SignedOut } else { remember(user); lockedOrSignedIn(user, offline = false) }
            } catch (error: ApiException) {
                // Offline: trust the stored cookie + last known profile so the cached vault still opens.
                val cached = prefs.getString(SecurePrefs.KEY_LAST_USER)?.let { runCatching { json.decodeFromString<AppUser>(it) }.getOrNull() }
                if (error.isOffline && cached != null) lockedOrSignedIn(cached, offline = true) else SessionState.SignedOut
            }
        }
        // Keep the orbit animation visible for at least ~1.4s so the boot screen reads as intentional, not a flash.
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed < 1400) kotlinx.coroutines.delay(1400 - elapsed)
        _state.value = next
    }

    private fun lockedOrSignedIn(user: AppUser, offline: Boolean): SessionState = if (appLockEnabled) SessionState.Locked(user) else SessionState.SignedIn(user, offline)

    suspend fun signIn(identifier: String, password: String): AppUser {
        val user = api.login(identifier, password)
        remember(user); _state.value = SessionState.SignedIn(user); return user
    }

    suspend fun signUp(fullName: String, email: String, password: String): AppUser {
        val user = api.register(fullName, email, password)
        remember(user); _state.value = SessionState.SignedIn(user); return user
    }

    suspend fun signOut() {
        api.logout()
        cookieJar.clear(); cache.clear(); prefs.remove(SecurePrefs.KEY_LAST_USER)
        _state.value = SessionState.SignedOut
    }

    /** Called when any API call answers 401 — the 7-day session expired or was revoked. */
    fun sessionExpired() { cookieJar.clear(); prefs.remove(SecurePrefs.KEY_LAST_USER); _state.value = SessionState.SignedOut }

    fun unlock() { _state.update { if (it is SessionState.Locked) SessionState.SignedIn(it.user) else it } }
    fun lock() { _state.update { if (it is SessionState.SignedIn) SessionState.Locked(it.user) else it } }
    fun setAppLock(enabled: Boolean) = prefs.putBoolean(SecurePrefs.KEY_APP_LOCK, enabled)
    fun completeOnboarding(interest: String?) { prefs.putBoolean(SecurePrefs.KEY_ONBOARDED, true); prefs.putString(SecurePrefs.KEY_PRIMARY_INTEREST, interest); if (_state.value is SessionState.NeedsOnboarding) _state.value = SessionState.SignedOut }
    fun primaryInterest(): String? = prefs.getString(SecurePrefs.KEY_PRIMARY_INTEREST)
    fun updateUser(user: AppUser) { remember(user); _state.update { when (it) { is SessionState.SignedIn -> it.copy(user = user); is SessionState.Locked -> it.copy(user = user); else -> it } } }

    private fun remember(user: AppUser) = prefs.putString(SecurePrefs.KEY_LAST_USER, json.encodeToString(user))
}
